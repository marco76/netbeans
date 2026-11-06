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

import java.awt.Component;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;

/**
 * Wizard panel for configuring Spring modules/dependencies and {@code @Configuration}
 * annotations.
 */
public final class SpringJavaConfigPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    public static final String SELECTED_MODULES = "selectedSpringModules"; //NOI18N
    public static final String SPRING_BOOT_MODE = "springBootMode"; //NOI18N
    public static final String SPRING_VERSION = "springVersion"; //NOI18N
    public static final String SPRING_LIBRARY = "springLibrary"; //NOI18N
    public static final String ADD_SPRING_LIBRARY = "addSpringLibrary"; //NOI18N
    public static final String COMPONENT_SCAN = "componentScan"; //NOI18N
    public static final String COMPONENT_SCAN_PACKAGES = "componentScanPackages"; //NOI18N
    public static final String PROPERTY_SOURCE_ENABLED = "propertySourceEnabled"; //NOI18N
    public static final String PROPERTY_SOURCE = "propertySource"; //NOI18N
    public static final String IMPORT_CONFIGS_ENABLED = "importConfigsEnabled"; //NOI18N
    public static final String IMPORT_CONFIGS = "importConfigs"; //NOI18N
    public static final String ENABLE_ANNOTATIONS = "enableAnnotations"; //NOI18N
    public static final String GENERATE_SAMPLE_BEAN = "generateSampleBean"; //NOI18N

    private final ChangeSupport changeSupport = new ChangeSupport(this);
    private SpringJavaConfigVisual component;

    @Override
    public Component getComponent() {
        if (component == null) {
            component = new SpringJavaConfigVisual(this);
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return new HelpCtx(SpringJavaConfigPanel.class);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        changeSupport.addChangeListener(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        changeSupport.removeChangeListener(l);
    }

    void fireChangeEvent() {
        changeSupport.fireChange();
    }

    @Override
    public void readSettings(WizardDescriptor settings) {
        getComponent();
        Project project = Templates.getProject(settings);
        FileObject targetFolder = Templates.getTargetFolder(settings);
        ClassPath cp = null;
        if (project != null) {
            FileObject artifact = NewSpringXMLConfigWizardIterator.getSourceGroupArtifact(
                    project, targetFolder != null ? targetFolder : project.getProjectDirectory());
            if (artifact != null) {
                cp = ClassPath.getClassPath(artifact, ClassPath.COMPILE);
            }
        }
        String defaultPackage = NewSpringJavaConfigWizardIterator.resolvePackageName(project, targetFolder);
        component.configureContext(project, cp, defaultPackage);
        component.read(settings);
    }

    @Override
    public void storeSettings(WizardDescriptor settings) {
        getComponent();
        component.store(settings);
    }
}
