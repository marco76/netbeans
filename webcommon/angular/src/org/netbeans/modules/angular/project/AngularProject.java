/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.angular.project;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.angular.model.AngularWorkspace.AngularSubProject;
import org.netbeans.modules.angular.model.PackageJsonConfig;
import org.netbeans.modules.angular.ui.AngularLogicalViewProvider;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.SubprojectProvider;
import org.netbeans.spi.project.support.GenericSources;
import org.netbeans.spi.project.ui.PrivilegedTemplates;
import org.netbeans.spi.project.ui.RecommendedTemplates;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * NetBeans {@link Project} implementation for Angular CLI workspaces ({@code angular.json}).
 */
public final class AngularProject implements Project {

    public static final String SOURCES_TYPE_ANGULAR = "angular-sources"; // NOI18N

    private final FileObject projectDirectory;
    private final ProjectState state;
    private volatile AngularWorkspace workspace;
    private final Lookup lookup;

    public AngularProject(
            @NonNull FileObject projectDirectory,
            @NonNull AngularWorkspace workspace,
            @NonNull ProjectState state) {
        this.projectDirectory = projectDirectory;
        this.workspace = workspace;
        this.state = state;
        this.lookup = Lookups.fixed(
                this,
                this.state,
                new Info(),
                new AngularSources(),
                new AngularSubprojectProvider(),
                new AngularActionProvider(this),
                new AngularLogicalViewProvider(this),
                new TemplatesImpl());
    }

    @Override
    public @NonNull FileObject getProjectDirectory() {
        return projectDirectory;
    }

    @Override
    public @NonNull Lookup getLookup() {
        return lookup;
    }

    public @NonNull AngularWorkspace getWorkspace() {
        AngularWorkspace reloaded = AngularWorkspace.load(projectDirectory);
        if (reloaded != null) {
            this.workspace = reloaded;
        }
        return this.workspace;
    }

    public @NonNull PackageJsonConfig getPackageJson() {
        PackageJsonConfig pkg = PackageJsonConfig.load(projectDirectory);
        if (pkg != null) {
            return pkg;
        }
        return new PackageJsonConfig(
                projectDirectory,
                projectDirectory.getName(),
                null,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    private final class Info implements ProjectInformation {

        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

        @Override
        public String getName() {
            AngularSubProject primary = getWorkspace().getPrimaryProject();
            if (primary != null && primary.getName() != null && !primary.getName().isEmpty()) {
                return primary.getName();
            }
            return projectDirectory.getName();
        }

        @Override
        public String getDisplayName() {
            return getName();
        }

        @Override
        public Icon getIcon() {
            return ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, false);
        }

        @Override
        public Project getProject() {
            return AngularProject.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            pcs.addPropertyChangeListener(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            pcs.removePropertyChangeListener(listener);
        }
    }

    private final class AngularSources implements Sources {

        private final ChangeSupport cs = new ChangeSupport(this);

        @Override
        public SourceGroup[] getSourceGroups(String type) {
            if (Sources.TYPE_GENERIC.equals(type)) {
                return new SourceGroup[]{
                    GenericSources.group(
                            AngularProject.this,
                            projectDirectory,
                            projectDirectory.getName(),
                            getLookup().lookup(ProjectInformation.class).getDisplayName(),
                            null,
                            null)
                };
            }
            if (SOURCES_TYPE_ANGULAR.equals(type)) {
                List<SourceGroup> groups = new ArrayList<>();
                for (FileObject root : getWorkspace().getSourceRoots()) {
                    groups.add(GenericSources.group(
                            AngularProject.this,
                            root,
                            root.getName(),
                            "Angular Sources (" + root.getName() + ")", // NOI18N
                            ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, false),
                            ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, false)));
                }
                return groups.toArray(SourceGroup[]::new);
            }
            return new SourceGroup[0];
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
            cs.addChangeListener(listener);
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
            cs.removeChangeListener(listener);
        }
    }

    private final class AngularSubprojectProvider implements SubprojectProvider {

        @Override
        public Set<? extends Project> getSubprojects() {
            Set<Project> subprojects = new LinkedHashSet<>();
            for (AngularSubProject sub : getWorkspace().getProjects().values()) {
                FileObject root = sub.resolveRoot(projectDirectory);
                if (root != null && !root.equals(projectDirectory)) {
                    try {
                        Project p = ProjectManager.getDefault().findProject(root);
                        if (p != null) {
                            subprojects.add(p);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return subprojects;
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
        }
    }

    private static final class TemplatesImpl implements RecommendedTemplates, PrivilegedTemplates {

        @Override
        public String[] getRecommendedTypes() {
            return new String[]{
                "Angular", // NOI18N
                "typescript", // NOI18N
                "html5", // NOI18N
                "clientside-types", // NOI18N
                "simple-files" // NOI18N
            };
        }

        @Override
        public String[] getPrivilegedTemplates() {
            return new String[]{
                "Templates/Angular/AngularComponent.ts", // NOI18N
                "Templates/Angular/AngularTemplate.html", // NOI18N
                "Templates/Angular/AngularService.ts", // NOI18N
                "Templates/Angular/AngularDirective.ts", // NOI18N
                "Templates/Angular/AngularPipe.ts", // NOI18N
                "Templates/Angular/AngularGuard.ts", // NOI18N
                "Templates/Angular/AngularInterceptor.ts", // NOI18N
                "Templates/Angular/AngularInterface.ts", // NOI18N
                "Templates/Angular/AngularResolver.ts", // NOI18N
                "Templates/Angular/AngularUnitTest.ts", // NOI18N
                "Templates/Angular/AngularEnvironment.ts", // NOI18N
                "Templates/ClientSide/html.html", // NOI18N
                "Templates/ClientSide/css.css", // NOI18N
                "Templates/ClientSide/style.scss", // NOI18N
                "Templates/Other/TypeScriptDataObjectTemplate.ts" // NOI18N
            };
        }
    }
}
