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
import java.util.List;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.modules.spring.beans.wizards.NewSpringDataRepositoryWizardIterator.EntityInfo;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;

/**
 * Wizard panel for configuring a new Spring Data Repository interface.
 */
public final class SpringDataRepositoryPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    public static final String ENTITY_CLASS = "springDataEntityClass"; //NOI18N
    public static final String ID_TYPE = "springDataIdType"; //NOI18N
    public static final String REPOSITORY_TYPE = "springDataRepositoryType"; //NOI18N
    public static final String SPECIFICATION_EXECUTOR = "springDataSpecificationExecutor"; //NOI18N
    public static final String ADD_REPOSITORY_ANNOTATION = "springDataAddRepositoryAnnotation"; //NOI18N
    public static final String GENERATE_SAMPLE_METHODS = "springDataGenerateSampleMethods"; //NOI18N
    public static final String ENSURE_DATA_DEPENDENCY = "springDataEnsureDependency"; //NOI18N
    public static final String ENTITY_SAMPLE_PROP_NAME = "springDataEntitySamplePropName"; //NOI18N
    public static final String ENTITY_SAMPLE_PROP_TYPE = "springDataEntitySamplePropType"; //NOI18N

    private final ChangeSupport changeSupport = new ChangeSupport(this);
    private SpringDataRepositoryVisual component;
    private WizardDescriptor wizard;

    @Override
    public Component getComponent() {
        if (component == null) {
            component = new SpringDataRepositoryVisual(this);
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return new HelpCtx(SpringDataRepositoryPanel.class);
    }

    @Override
    public boolean isValid() {
        getComponent();
        String err = component.getErrorMessage();
        if (wizard != null) {
            wizard.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, err);
        }
        return err == null;
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
        this.wizard = settings;
        getComponent();
        Project project = Templates.getProject(settings);
        List<EntityInfo> entities = NewSpringDataRepositoryWizardIterator.discoverProjectEntities(project);
        component.configureEntities(entities);
        component.read(settings);
    }

    @Override
    public void storeSettings(WizardDescriptor settings) {
        getComponent();
        component.store(settings);
    }
}
