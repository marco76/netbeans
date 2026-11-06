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
package org.netbeans.modules.spring.beans.wizards;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.spring.beans.wizards.SpringInitializrService.DependencyItem;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.AsyncGUIJob;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/**
 * Wizard iterator for creating and configuring Spring Boot projects (Maven and Gradle)
 * via Spring Initializr ({@code https://start.spring.io}) or offline generation.
 */
public class SpringBootProjectWizardIterator implements WizardDescriptor.ProgressInstantiatingIterator<WizardDescriptor> {

    public static final String PROJECT_NAME = "project.name"; //NOI18N
    public static final String PROJECT_LOCATION = "project.location"; //NOI18N
    public static final String SERVICE_URL = "service.url"; //NOI18N
    public static final String BOOT_VERSION = "boot.version"; //NOI18N
    public static final String MAVEN_GROUP = "maven.group"; //NOI18N
    public static final String MAVEN_ARTIFACT = "maven.artifact"; //NOI18N
    public static final String PACKAGE_NAME = "package.name"; //NOI18N
    public static final String DESCRIPTION = "project.description"; //NOI18N
    public static final String PACKAGING = "packaging"; //NOI18N
    public static final String JAVA_VERSION = "java.version"; //NOI18N
    public static final String LANGUAGE = "language"; //NOI18N
    public static final String BUILD_TOOL = "build.tool"; //NOI18N
    public static final String CONFIG_FORMAT = "config.format"; //NOI18N
    public static final String DEPENDENCIES = "dependencies"; //NOI18N

    public static final class MavenSpringBootProject extends SpringBootProjectWizardIterator {
        @Override
        public void initialize(WizardDescriptor wizard) {
            super.initialize(wizard);
            wizard.putProperty(BUILD_TOOL, "MAVEN"); //NOI18N
            wizard.putProperty("NewProjectWizard_Title", //NOI18N
                    NbBundle.getMessage(SpringBootProjectWizardIterator.class, "LBL_SpringBootMavenWizardTitle"));
        }
    }

    public static final class GradleSpringBootProject extends SpringBootProjectWizardIterator {
        @Override
        public void initialize(WizardDescriptor wizard) {
            super.initialize(wizard);
            wizard.putProperty(BUILD_TOOL, "GRADLE"); //NOI18N
            wizard.putProperty("NewProjectWizard_Title", //NOI18N
                    NbBundle.getMessage(SpringBootProjectWizardIterator.class, "LBL_SpringBootGradleWizardTitle"));
        }
    }

    private transient int index;
    private transient WizardDescriptor.Panel<WizardDescriptor>[] panels;
    private transient WizardDescriptor wiz;

    @Override
    @SuppressWarnings("unchecked")
    public Set<FileObject> instantiate(ProgressHandle handle) throws IOException {
        try {
            handle.start(4);
            String projectName = (String) wiz.getProperty(PROJECT_NAME);
            File projFile = FileUtil.normalizeFile((File) wiz.getProperty(PROJECT_LOCATION));
            if (!projFile.isDirectory() && !projFile.mkdirs()) {
                throw new IOException("Cannot create project directory: " + projFile); //NOI18N
            }
            handle.progress(1);

            FileObject projDir = FileUtil.toFileObject(projFile);
            Set<DependencyItem> dependencies = (Set<DependencyItem>) wiz.getProperty(DEPENDENCIES);
            SpringInitializrService.getInstance().createProject(
                    (String) wiz.getProperty(SERVICE_URL),
                    (String) wiz.getProperty(BUILD_TOOL),
                    (String) wiz.getProperty(BOOT_VERSION),
                    (String) wiz.getProperty(MAVEN_GROUP),
                    (String) wiz.getProperty(MAVEN_ARTIFACT),
                    projectName,
                    (String) wiz.getProperty(DESCRIPTION),
                    (String) wiz.getProperty(PACKAGE_NAME),
                    (String) wiz.getProperty(PACKAGING),
                    (String) wiz.getProperty(JAVA_VERSION),
                    (String) wiz.getProperty(LANGUAGE),
                    (String) wiz.getProperty(CONFIG_FORMAT),
                    dependencies != null ? dependencies : Collections.emptySet(),
                    projDir);
            handle.progress(3);

            ProjectManager.getDefault().clearNonProjectCache();
            Project prj = ProjectManager.getDefault().findProject(projDir);
            if (prj != null) {
                ActionProvider actionProvider = prj.getLookup().lookup(ActionProvider.class);
                if (actionProvider != null
                        && Arrays.asList(actionProvider.getSupportedActions()).contains(ActionProvider.COMMAND_PRIME)
                        && actionProvider.isActionEnabled(ActionProvider.COMMAND_PRIME, Lookup.EMPTY)) {
                    actionProvider.invokeAction(ActionProvider.COMMAND_PRIME, Lookup.EMPTY);
                }
            }
            File parent = projFile.getParentFile();
            if (parent != null && parent.exists()) {
                ProjectChooser.setProjectsFolder(parent);
            }
            return Collections.singleton(projDir);
        } finally {
            handle.finish();
        }
    }

    @Override
    public Set<FileObject> instantiate() throws IOException {
        throw new UnsupportedOperationException("Not supported."); //NOI18N
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void initialize(WizardDescriptor wizard) {
        this.wiz = wizard;
        wiz.putProperty("NewProjectWizard_Title", //NOI18N
                NbBundle.getMessage(SpringBootProjectWizardIterator.class, "LBL_SpringBootMavenWizardTitle"));
        index = 0;
        panels = new WizardDescriptor.Panel[]{
            new SpringBootNameAndLocationWizardPanel(),
            new SpringBootBasePropertiesWizardPanel(),
            new SpringBootDependenciesWizardPanel()
        };
        String[] steps = new String[]{
            NbBundle.getMessage(SpringBootProjectWizardIterator.class, "LBL_NameAndLocationStep"),
            NbBundle.getMessage(SpringBootProjectWizardIterator.class, "LBL_BasePropertiesStep"),
            NbBundle.getMessage(SpringBootProjectWizardIterator.class, "LBL_DependenciesStep")
        };
        for (int i = 0; i < panels.length; i++) {
            JComponent jc = (JComponent) panels[i].getComponent();
            jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
            jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
        }
        Utilities.attachInitJob(panels[1].getComponent(), (AsyncGUIJob) panels[1].getComponent());
        Utilities.attachInitJob(panels[2].getComponent(), (AsyncGUIJob) panels[2].getComponent());
    }

    @Override
    public void uninitialize(WizardDescriptor wizard) {
        panels = null;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return panels[index];
    }

    @Override
    public String name() {
        return NbBundle.getMessage(SpringBootProjectWizardIterator.class, "NameFormat", index + 1, panels.length);
    }

    @Override
    public boolean hasNext() {
        return index < panels.length - 1;
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
}
