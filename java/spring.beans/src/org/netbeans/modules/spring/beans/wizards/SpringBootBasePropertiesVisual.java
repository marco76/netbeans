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

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import javax.lang.model.SourceVersion;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.modules.spring.beans.wizards.SpringInitializrService.InitializrMetadata;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.awt.Mnemonics;
import org.openide.util.AsyncGUIJob;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Visual panel for the Base Properties step in the Spring Boot project wizard.
 */
public final class SpringBootBasePropertiesVisual extends JPanel implements DocumentListener, ActionListener, AsyncGUIJob {

    private final SpringBootBasePropertiesWizardPanel panel;
    private boolean initialized = false;
    private boolean userEditedPackage = false;
    private boolean updatingPackageAutomatically = false;
    private InitializrMetadata metadata;

    private ButtonGroup serviceButtonGroup;
    private JRadioButton defaultRadioButton;
    private JRadioButton customRadioButton;
    private JTextField customUrlField;
    private JComboBox<String> bootVersionComboBox;
    private JTextField groupTextField;
    private JTextField artifactTextField;
    private JTextField packageNameTextField;
    private JTextField descriptionTextField;
    private ButtonGroup packagingButtonGroup;
    private JRadioButton jarRadioButton;
    private JRadioButton warRadioButton;
    private JComboBox<String> javaVersionComboBox;
    private ButtonGroup languageButtonGroup;
    private JRadioButton javaRadioButton;
    private JRadioButton kotlinRadioButton;
    private JRadioButton groovyRadioButton;
    private ButtonGroup configFormatButtonGroup;
    private JRadioButton propertiesFormatRadioButton;
    private JRadioButton yamlFormatRadioButton;

    public SpringBootBasePropertiesVisual(SpringBootBasePropertiesWizardPanel panel) {
        this.panel = panel;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;

        serviceButtonGroup = new ButtonGroup();
        packagingButtonGroup = new ButtonGroup();
        languageButtonGroup = new ButtonGroup();
        configFormatButtonGroup = new ButtonGroup();

        int row = 0;

        JLabel serviceLabel = new JLabel();
        Mnemonics.setLocalizedText(serviceLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_ChooseServiceURL"));
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        add(serviceLabel, gbc);

        defaultRadioButton = new JRadioButton();
        Mnemonics.setLocalizedText(defaultRadioButton, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Default"));
        defaultRadioButton.setActionCommand("DEFAULT"); //NOI18N
        defaultRadioButton.setSelected(true);
        defaultRadioButton.addActionListener(this);
        serviceButtonGroup.add(defaultRadioButton);

        JLabel defaultUrlLabel = new JLabel(SpringInitializrService.DEFAULT_SERVICE_URL);
        defaultUrlLabel.setFont(defaultUrlLabel.getFont().deriveFont(Font.ITALIC));

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        add(defaultRadioButton, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(defaultUrlLabel, gbc);
        row++;

        customRadioButton = new JRadioButton();
        Mnemonics.setLocalizedText(customRadioButton, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Custom"));
        customRadioButton.setActionCommand("CUSTOM"); //NOI18N
        customRadioButton.addActionListener(this);
        serviceButtonGroup.add(customRadioButton);

        customUrlField = new JTextField(24);
        customUrlField.getDocument().addDocumentListener(this);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(customRadioButton, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(customUrlField, gbc);
        row++;

        JLabel bootVersionLabel = new JLabel();
        Mnemonics.setLocalizedText(bootVersionLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_BootVersion"));
        bootVersionComboBox = new JComboBox<>(new String[]{"3.4.3", "3.3.9", "3.2.12", "2.7.18"}); //NOI18N

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(bootVersionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(bootVersionComboBox, gbc);
        row++;

        JLabel groupLabel = new JLabel();
        Mnemonics.setLocalizedText(groupLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Group"));
        groupTextField = new JTextField("com.example", 24); //NOI18N
        groupTextField.getDocument().addDocumentListener(this);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(groupLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(groupTextField, gbc);
        row++;

        JLabel artifactLabel = new JLabel();
        Mnemonics.setLocalizedText(artifactLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Artifact"));
        artifactTextField = new JTextField(24);
        artifactTextField.getDocument().addDocumentListener(this);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(artifactLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(artifactTextField, gbc);
        row++;

        JLabel packageLabel = new JLabel();
        Mnemonics.setLocalizedText(packageLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_PackageName"));
        packageNameTextField = new JTextField(24);
        packageNameTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onPackageChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onPackageChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onPackageChanged();
            }

            private void onPackageChanged() {
                if (!updatingPackageAutomatically) {
                    userEditedPackage = true;
                }
                panel.fireChangeEvent();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(packageLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(packageNameTextField, gbc);
        row++;

        JLabel descriptionLabel = new JLabel();
        Mnemonics.setLocalizedText(descriptionLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Description"));
        descriptionTextField = new JTextField("Demo project for Spring Boot", 24); //NOI18N

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(descriptionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(descriptionTextField, gbc);
        row++;

        JLabel packagingLabel = new JLabel();
        Mnemonics.setLocalizedText(packagingLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Packaging"));
        jarRadioButton = new JRadioButton("Jar"); //NOI18N
        jarRadioButton.setActionCommand("jar"); //NOI18N
        jarRadioButton.setSelected(true);
        warRadioButton = new JRadioButton("War"); //NOI18N
        warRadioButton.setActionCommand("war"); //NOI18N
        packagingButtonGroup.add(jarRadioButton);
        packagingButtonGroup.add(warRadioButton);
        JPanel packagingPanel = new JPanel(new GridBagLayout());
        packagingPanel.add(jarRadioButton);
        packagingPanel.add(warRadioButton);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(packagingLabel, gbc);
        gbc.gridx = 1;
        add(packagingPanel, gbc);
        row++;

        JLabel javaVersionLabel = new JLabel();
        Mnemonics.setLocalizedText(javaVersionLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_JavaVersion"));
        javaVersionComboBox = new JComboBox<>(new String[]{"21", "17", "11"}); //NOI18N

        gbc.gridx = 0;
        gbc.gridy = row;
        add(javaVersionLabel, gbc);
        gbc.gridx = 1;
        add(javaVersionComboBox, gbc);
        row++;

        JLabel languageLabel = new JLabel();
        Mnemonics.setLocalizedText(languageLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_Language"));
        javaRadioButton = new JRadioButton("Java"); //NOI18N
        javaRadioButton.setActionCommand("java"); //NOI18N
        javaRadioButton.setSelected(true);
        kotlinRadioButton = new JRadioButton("Kotlin"); //NOI18N
        kotlinRadioButton.setActionCommand("kotlin"); //NOI18N
        groovyRadioButton = new JRadioButton("Groovy"); //NOI18N
        groovyRadioButton.setActionCommand("groovy"); //NOI18N
        languageButtonGroup.add(javaRadioButton);
        languageButtonGroup.add(kotlinRadioButton);
        languageButtonGroup.add(groovyRadioButton);
        JPanel langPanel = new JPanel(new GridBagLayout());
        langPanel.add(javaRadioButton);
        langPanel.add(kotlinRadioButton);
        langPanel.add(groovyRadioButton);

        gbc.gridx = 0;
        gbc.gridy = row;
        add(languageLabel, gbc);
        gbc.gridx = 1;
        add(langPanel, gbc);
        row++;

        JLabel configFormatLabel = new JLabel();
        Mnemonics.setLocalizedText(configFormatLabel, NbBundle.getMessage(SpringBootBasePropertiesVisual.class, "LBL_ConfigFormat"));
        propertiesFormatRadioButton = new JRadioButton("Properties"); //NOI18N
        propertiesFormatRadioButton.setActionCommand("properties"); //NOI18N
        propertiesFormatRadioButton.setSelected(true);
        yamlFormatRadioButton = new JRadioButton("YAML"); //NOI18N
        yamlFormatRadioButton.setActionCommand("yaml"); //NOI18N
        configFormatButtonGroup.add(propertiesFormatRadioButton);
        configFormatButtonGroup.add(yamlFormatRadioButton);
        JPanel formatPanel = new JPanel(new GridBagLayout());
        formatPanel.add(propertiesFormatRadioButton);
        formatPanel.add(yamlFormatRadioButton);

        gbc.gridx = 0;
        gbc.gridy = row;
        add(configFormatLabel, gbc);
        gbc.gridx = 1;
        add(formatPanel, gbc);
        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JPanel(), gbc);
    }

    boolean valid(WizardDescriptor wizardDescriptor) {
        if (wizardDescriptor == null) {
            return true;
        }
        if ("CUSTOM".equals(serviceButtonGroup.getSelection().getActionCommand()) && !customURLValid()) { //NOI18N
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Custom Service URL is not valid."); //NOI18N
            return false;
        }
        if (groupTextField.getText().trim().isEmpty() || !SourceVersion.isName(groupTextField.getText().trim())) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Group is not a valid package prefix."); //NOI18N
            return false;
        }
        if (artifactTextField.getText().trim().isEmpty()) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Artifact cannot be empty."); //NOI18N
            return false;
        }
        if (packageNameTextField.getText().trim().isEmpty() || !SourceVersion.isName(packageNameTextField.getText().trim())) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Package Name is not a valid Java package name."); //NOI18N
            return false;
        }
        wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, ""); //NOI18N
        return initialized;
    }

    void store(WizardDescriptor d) {
        d.putProperty(SpringBootProjectWizardIterator.SERVICE_URL, getServiceUrl());
        d.putProperty(SpringBootProjectWizardIterator.BOOT_VERSION, bootVersionComboBox.getSelectedItem());
        d.putProperty(SpringBootProjectWizardIterator.MAVEN_GROUP, groupTextField.getText().trim());
        d.putProperty(SpringBootProjectWizardIterator.MAVEN_ARTIFACT, artifactTextField.getText().trim());
        d.putProperty(SpringBootProjectWizardIterator.PACKAGE_NAME, packageNameTextField.getText().trim());
        d.putProperty(SpringBootProjectWizardIterator.DESCRIPTION, descriptionTextField.getText().trim());
        d.putProperty(SpringBootProjectWizardIterator.PACKAGING, packagingButtonGroup.getSelection().getActionCommand());
        d.putProperty(SpringBootProjectWizardIterator.JAVA_VERSION, javaVersionComboBox.getSelectedItem());
        d.putProperty(SpringBootProjectWizardIterator.LANGUAGE, languageButtonGroup.getSelection().getActionCommand());
        d.putProperty(SpringBootProjectWizardIterator.CONFIG_FORMAT, configFormatButtonGroup.getSelection().getActionCommand());
    }

    void read(WizardDescriptor settings) {
        String group = (String) settings.getProperty(SpringBootProjectWizardIterator.MAVEN_GROUP);
        if (group != null && !group.isEmpty()) {
            groupTextField.setText(group);
        }
        String artifact = (String) settings.getProperty(SpringBootProjectWizardIterator.MAVEN_ARTIFACT);
        if (artifact != null && !artifact.isEmpty()) {
            artifactTextField.setText(artifact);
        }
        syncDerivedPackage();
    }

    private void syncDerivedPackage() {
        if (userEditedPackage) {
            return;
        }
        String g = groupTextField.getText().trim();
        String a = artifactTextField.getText().trim().replaceAll("[^a-zA-Z0-9_]", ""); //NOI18N
        if (!a.isEmpty() && Character.isDigit(a.charAt(0))) {
            a = "_" + a; //NOI18N
        }
        String derived = g.isEmpty() ? a : (a.isEmpty() ? g : g + "." + a); //NOI18N
        updatingPackageAutomatically = true;
        try {
            packageNameTextField.setText(derived);
        } finally {
            updatingPackageAutomatically = false;
        }
    }

    void validate(WizardDescriptor d) throws WizardValidationException {
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        onDocumentChange(e);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        onDocumentChange(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        onDocumentChange(e);
    }

    private void onDocumentChange(DocumentEvent e) {
        if (e.getDocument() == groupTextField.getDocument() || e.getDocument() == artifactTextField.getDocument()) {
            syncDerivedPackage();
        }
        panel.fireChangeEvent();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        refresh();
    }

    @Override
    public void construct() {
        metadata = SpringInitializrService.getInstance().getMetadata(getServiceUrl());
        initialized = true;
    }

    @Override
    public void finished() {
        if (metadata != null) {
            bootVersionComboBox.setModel(new DefaultComboBoxModel<>(metadata.getBootVersions().toArray(new String[0])));
            if (metadata.getDefaultBootVersion() != null) {
                bootVersionComboBox.setSelectedItem(metadata.getDefaultBootVersion());
            }
            javaVersionComboBox.setModel(new DefaultComboBoxModel<>(metadata.getJavaVersions().toArray(new String[0])));
            if (metadata.getDefaultJavaVersion() != null) {
                javaVersionComboBox.setSelectedItem(metadata.getDefaultJavaVersion());
            }
        }
        panel.fireChangeEvent();
    }

    private void refresh() {
        initialized = false;
        RequestProcessor.getDefault().post(() -> {
            construct();
            SwingUtilities.invokeLater(this::finished);
        });
    }

    private String getServiceUrl() {
        if ("CUSTOM".equals(serviceButtonGroup.getSelection().getActionCommand())) { //NOI18N
            return customUrlField.getText().trim();
        }
        return SpringInitializrService.DEFAULT_SERVICE_URL;
    }

    private boolean customURLValid() {
        String customUrl = customUrlField.getText().trim();
        if (customUrl.isEmpty()) {
            return false;
        }
        try {
            URL url = new URL(customUrl);
            return "http".equalsIgnoreCase(url.getProtocol()) || "https".equalsIgnoreCase(url.getProtocol()); //NOI18N
        } catch (MalformedURLException ex) {
            return false;
        }
    }
}
