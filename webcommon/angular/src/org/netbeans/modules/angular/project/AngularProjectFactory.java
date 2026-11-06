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

import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectFactory2;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.util.lookup.ServiceProvider;

/**
 * Recognizes directories containing an {@code angular.json} workspace file as Angular projects.
 */
// Recognize Angular workspaces before the generic Ant factory (position 100),
// including workspaces previously imported as HTML5 projects in nbproject.
@ServiceProvider(service = ProjectFactory.class, position = 50)
public final class AngularProjectFactory implements ProjectFactory2 {

    public static final String PROJECT_TYPE = "org-netbeans-modules-angular"; // NOI18N

    @Override
    public boolean isProject(FileObject projectDirectory) {
        return isProject2(projectDirectory) != null;
    }

    @Override
    public ProjectManager.Result isProject2(FileObject projectDirectory) {
        if (projectDirectory == null || !projectDirectory.isFolder()) {
            return null;
        }
        FileObject angularJson = projectDirectory.getFileObject(AngularWorkspace.ANGULAR_JSON);
        if (angularJson == null || angularJson.isFolder()) {
            return null;
        }
        return new ProjectManager.Result(
                projectDirectory.getName(),
                PROJECT_TYPE,
                ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, false));
    }

    @Override
    public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
        if (isProject2(projectDirectory) == null) {
            return null;
        }
        AngularWorkspace workspace = AngularWorkspace.load(projectDirectory);
        if (workspace == null) {
            return null;
        }
        return new AngularProject(projectDirectory, workspace, state);
    }

    @Override
    public void saveProject(Project project) throws IOException, ClassCastException {
        // angular.json and package.json are managed directly on disk
    }
}
