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
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * Wizard panel for the Base Properties step in the Spring Boot project wizard.
 */
public final class SpringBootBasePropertiesWizardPanel implements
        WizardDescriptor.ValidatingPanel<WizardDescriptor>,
        WizardDescriptor.FinishablePanel<WizardDescriptor> {

    WizardDescriptor wizardDescriptor;
    private SpringBootBasePropertiesVisual component;
    private final ChangeSupport changeSupport = new ChangeSupport(this);

    @Override
    public Component getComponent() {
        if (component == null) {
            component = new SpringBootBasePropertiesVisual(this);
            component.setName(NbBundle.getMessage(SpringBootBasePropertiesWizardPanel.class, "LBL_BasePropertiesStep"));
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        getComponent();
        return component.valid(wizardDescriptor);
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
    public void readSettings(WizardDescriptor wiz) {
        wizardDescriptor = wiz;
        getComponent();
        component.read(wizardDescriptor);
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        getComponent();
        component.store(wiz);
    }

    @Override
    public boolean isFinishPanel() {
        return false;
    }

    @Override
    public void validate() throws WizardValidationException {
        getComponent();
        component.validate(wizardDescriptor);
    }
}
