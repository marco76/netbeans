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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.awt.Mnemonics;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * Visual panel for the Name and Location step in the Spring Boot project wizard.
 */
public final class SpringBootNameAndLocationVisual extends JPanel implements DocumentListener {

    private final SpringBootNameAndLocationWizardPanel panel;
    private JTextField projectNameTextField;
    private JTextField projectLocationTextField;
    private JTextField createdFolderTextField;

    public SpringBootNameAndLocationVisual(SpringBootNameAndLocationWizardPanel panel) {
        this.panel = panel;
        initComponents();
        projectNameTextField.getDocument().addDocumentListener(this);
        projectLocationTextField.getDocument().addDocumentListener(this);
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel projectNameLabel = new JLabel();
        projectNameTextField = new JTextField(24);
        projectNameLabel.setLabelFor(projectNameTextField);
        Mnemonics.setLocalizedText(projectNameLabel, NbBundle.getMessage(SpringBootNameAndLocationVisual.class, "LBL_ProjectName"));

        gbc.gridx = 0;
        gbc.gridy = 0;
        add(projectNameLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(projectNameTextField, gbc);

        JLabel projectLocationLabel = new JLabel();
        projectLocationTextField = new JTextField(24);
        projectLocationLabel.setLabelFor(projectLocationTextField);
        Mnemonics.setLocalizedText(projectLocationLabel, NbBundle.getMessage(SpringBootNameAndLocationVisual.class, "LBL_ProjectLocation"));

        JButton browseButton = new JButton();
        Mnemonics.setLocalizedText(browseButton, NbBundle.getMessage(SpringBootNameAndLocationVisual.class, "BTN_Browse"));
        browseButton.addActionListener(e -> browseProjectLocation());

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(projectLocationLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(projectLocationTextField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(browseButton, gbc);

        JLabel createdFolderLabel = new JLabel();
        createdFolderTextField = new JTextField(24);
        createdFolderTextField.setEditable(false);
        createdFolderLabel.setLabelFor(createdFolderTextField);
        Mnemonics.setLocalizedText(createdFolderLabel, NbBundle.getMessage(SpringBootNameAndLocationVisual.class, "LBL_ProjectFolder"));

        gbc.gridx = 0;
        gbc.gridy = 2;
        add(createdFolderLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(createdFolderTextField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JPanel(), gbc);
    }

    private void browseProjectLocation() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(SpringBootNameAndLocationVisual.class, "TIT_Select_Project_Location"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String path = projectLocationTextField.getText();
        if (!path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                chooser.setSelectedFile(f);
            }
        }
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            File projectDir = chooser.getSelectedFile();
            projectLocationTextField.setText(FileUtil.normalizeFile(projectDir).getAbsolutePath());
        }
        panel.fireChangeEvent();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        projectNameTextField.requestFocus();
    }

    boolean valid(WizardDescriptor wizardDescriptor) {
        if (wizardDescriptor == null) {
            return true;
        }
        if (projectNameTextField.getText().trim().isEmpty()) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Project Name is not a valid folder name."); //NOI18N
            return false;
        }
        File f = FileUtil.normalizeFile(new File(projectLocationTextField.getText()).getAbsoluteFile());
        if (!f.isDirectory()) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Project Folder is not a valid path."); //NOI18N
            return false;
        }
        final File destFolder = FileUtil.normalizeFile(new File(createdFolderTextField.getText()).getAbsoluteFile());
        File projLoc = destFolder;
        while (projLoc != null && !projLoc.exists()) {
            projLoc = projLoc.getParentFile();
        }
        if (projLoc == null || !projLoc.canWrite() || FileUtil.toFileObject(projLoc) == null) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Project Folder cannot be created."); //NOI18N
            return false;
        }
        File[] kids = destFolder.listFiles();
        if (destFolder.exists() && kids != null && kids.length > 0) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "Project Folder already exists and is not empty."); //NOI18N
            return false;
        }
        wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, ""); //NOI18N
        return true;
    }

    void store(WizardDescriptor d) {
        String name = projectNameTextField.getText().trim();
        String folder = createdFolderTextField.getText().trim();
        d.putProperty(SpringBootProjectWizardIterator.PROJECT_NAME, name);
        d.putProperty(SpringBootProjectWizardIterator.PROJECT_LOCATION, new File(folder));
        d.putProperty(SpringBootProjectWizardIterator.MAVEN_ARTIFACT, toArtifactId(name));
    }

    void read(WizardDescriptor settings) {
        File projectLocation = (File) settings.getProperty(SpringBootProjectWizardIterator.PROJECT_LOCATION);
        if (projectLocation == null || projectLocation.getParentFile() == null || !projectLocation.getParentFile().isDirectory()) {
            projectLocation = ProjectChooser.getProjectsFolder();
        } else {
            projectLocation = projectLocation.getParentFile();
        }
        projectLocationTextField.setText(projectLocation.getAbsolutePath());
        String projectName = (String) settings.getProperty(SpringBootProjectWizardIterator.PROJECT_NAME);
        if (projectName == null) {
            projectName = "SpringBootProject"; //NOI18N
        }
        projectNameTextField.setText(projectName);
        projectNameTextField.selectAll();
    }

    void validate(WizardDescriptor d) throws WizardValidationException {
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        updateTexts(e);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        updateTexts(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        updateTexts(e);
    }

    private void updateTexts(DocumentEvent e) {
        Document doc = e.getDocument();
        if (doc == projectNameTextField.getDocument() || doc == projectLocationTextField.getDocument()) {
            String projectName = projectNameTextField.getText();
            String projectFolder = projectLocationTextField.getText();
            createdFolderTextField.setText(projectFolder + File.separatorChar + projectName);
        }
        panel.fireChangeEvent();
    }

    static String toArtifactId(String name) {
        if (name == null || name.isEmpty()) {
            return "demo"; //NOI18N
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && Character.isLowerCase(name.charAt(i - 1))) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('-');
            }
        }
        String res = sb.toString().replaceAll("-+", "-").replaceAll("^-|-$", ""); //NOI18N
        return res.isEmpty() ? "demo" : res.toLowerCase(Locale.ROOT); //NOI18N
    }
}
