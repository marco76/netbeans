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

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import org.netbeans.modules.spring.beans.wizards.NewSpringDataRepositoryWizardIterator.EntityInfo;
import org.netbeans.modules.spring.beans.wizards.NewSpringDataRepositoryWizardIterator.RepositoryKind;
import org.openide.WizardDescriptor;
import org.openide.awt.Mnemonics;
import org.openide.util.NbBundle;

/**
 * Visual Swing component for the Spring Data Repository wizard step.
 */
public final class SpringDataRepositoryVisual extends JPanel {

    private static final String[] DEFAULT_ID_TYPES = new String[]{
        "java.lang.Long", //NOI18N
        "java.lang.Integer", //NOI18N
        "java.lang.String", //NOI18N
        "java.util.UUID" //NOI18N
    };

    private final SpringDataRepositoryPanel controller;
    private final Map<String, EntityInfo> entityByFqn = new LinkedHashMap<String, EntityInfo>();

    private final JComboBox<String> entityComboBox = new JComboBox<String>();
    private final JComboBox<String> idTypeComboBox = new JComboBox<String>(DEFAULT_ID_TYPES);
    private final JComboBox<RepositoryKind> repoTypeComboBox = new JComboBox<RepositoryKind>(RepositoryKind.values());
    private final JCheckBox specExecutorCheckBox = new JCheckBox();
    private final JCheckBox repositoryAnnotationCheckBox = new JCheckBox();
    private final JCheckBox sampleMethodsCheckBox = new JCheckBox();
    private final JCheckBox ensureDependencyCheckBox = new JCheckBox();

    public SpringDataRepositoryVisual(SpringDataRepositoryPanel controller) {
        this.controller = controller;
        setName(NbBundle.getMessage(SpringDataRepositoryVisual.class, "LBL_SpringDataRepo_Step"));
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 12));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(
                NbBundle.getMessage(SpringDataRepositoryVisual.class, "LBL_SpringDataRepo_TargetGroup")));

        JLabel entityLabel = new JLabel();
        Mnemonics.setLocalizedText(entityLabel, NbBundle.getMessage(SpringDataRepositoryVisual.class, "LBL_EntityClass"));
        entityComboBox.setEditable(true);
        entityLabel.setLabelFor(entityComboBox);

        JLabel idTypeLabel = new JLabel();
        Mnemonics.setLocalizedText(idTypeLabel, NbBundle.getMessage(SpringDataRepositoryVisual.class, "LBL_IdType"));
        idTypeComboBox.setEditable(true);
        idTypeLabel.setLabelFor(idTypeComboBox);

        JLabel repoTypeLabel = new JLabel();
        Mnemonics.setLocalizedText(repoTypeLabel, NbBundle.getMessage(SpringDataRepositoryVisual.class, "LBL_RepoBaseInterface"));
        repoTypeLabel.setLabelFor(repoTypeComboBox);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(entityLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(entityComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(idTypeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(idTypeComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(repoTypeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(repoTypeComboBox, gbc);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                NbBundle.getMessage(SpringDataRepositoryVisual.class, "LBL_SpringDataRepo_OptionsGroup")));

        Mnemonics.setLocalizedText(specExecutorCheckBox,
                NbBundle.getMessage(SpringDataRepositoryVisual.class, "CHK_JpaSpecExecutor"));
        Mnemonics.setLocalizedText(repositoryAnnotationCheckBox,
                NbBundle.getMessage(SpringDataRepositoryVisual.class, "CHK_AddRepositoryAnnotation"));
        Mnemonics.setLocalizedText(sampleMethodsCheckBox,
                NbBundle.getMessage(SpringDataRepositoryVisual.class, "CHK_GenerateSampleQueryMethods"));
        Mnemonics.setLocalizedText(ensureDependencyCheckBox,
                NbBundle.getMessage(SpringDataRepositoryVisual.class, "CHK_EnsureSpringDataDep"));

        sampleMethodsCheckBox.setSelected(true);
        ensureDependencyCheckBox.setSelected(true);

        optionsPanel.add(specExecutorCheckBox);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(repositoryAnnotationCheckBox);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(sampleMethodsCheckBox);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(ensureDependencyCheckBox);

        add(formPanel, BorderLayout.NORTH);
        add(optionsPanel, BorderLayout.CENTER);

        entityComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onEntitySelected();
                controller.fireChangeEvent();
            }
        });
        attachEditorListener(entityComboBox);
        attachEditorListener(idTypeComboBox);
    }

    private void attachEditorListener(JComboBox<String> combo) {
        if (combo.getEditor().getEditorComponent() instanceof JTextComponent) {
            JTextComponent tc = (JTextComponent) combo.getEditor().getEditorComponent();
            tc.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    controller.fireChangeEvent();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    controller.fireChangeEvent();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    controller.fireChangeEvent();
                }
            });
        }
    }

    void configureEntities(List<EntityInfo> entities) {
        entityByFqn.clear();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>();
        List<EntityInfo> list = entities != null ? entities : Collections.<EntityInfo>emptyList();
        for (EntityInfo info : list) {
            entityByFqn.put(info.getEntityFqn(), info);
            model.addElement(info.getEntityFqn());
        }
        entityComboBox.setModel(model);
        if (!list.isEmpty()) {
            entityComboBox.setSelectedIndex(0);
            onEntitySelected();
        }
    }

    private void onEntitySelected() {
        String selectedEntity = getComboText(entityComboBox);
        EntityInfo info = entityByFqn.get(selectedEntity);
        if (info != null && info.getIdTypeFqn() != null && !info.getIdTypeFqn().isEmpty()) {
            idTypeComboBox.setSelectedItem(info.getIdTypeFqn());
        }
    }

    private static String getComboText(JComboBox<String> combo) {
        Object editorItem = combo.getEditor().getItem();
        if (editorItem != null && !editorItem.toString().trim().isEmpty()) {
            return editorItem.toString().trim();
        }
        Object sel = combo.getSelectedItem();
        return sel != null ? sel.toString().trim() : ""; //NOI18N
    }

    String getErrorMessage() {
        String entity = getComboText(entityComboBox);
        if (entity.isEmpty()) {
            return NbBundle.getMessage(SpringDataRepositoryVisual.class, "ERR_EmptyEntityClass");
        }
        String idType = getComboText(idTypeComboBox);
        if (idType.isEmpty()) {
            return NbBundle.getMessage(SpringDataRepositoryVisual.class, "ERR_EmptyIdType");
        }
        return null;
    }

    void read(WizardDescriptor settings) {
        String entity = (String) settings.getProperty(SpringDataRepositoryPanel.ENTITY_CLASS);
        if (entity != null && !entity.isEmpty()) {
            entityComboBox.setSelectedItem(entity);
        }
        String idType = (String) settings.getProperty(SpringDataRepositoryPanel.ID_TYPE);
        if (idType != null && !idType.isEmpty()) {
            idTypeComboBox.setSelectedItem(idType);
        }
        RepositoryKind repoKind = (RepositoryKind) settings.getProperty(SpringDataRepositoryPanel.REPOSITORY_TYPE);
        if (repoKind != null) {
            repoTypeComboBox.setSelectedItem(repoKind);
        }
        Boolean specExec = (Boolean) settings.getProperty(SpringDataRepositoryPanel.SPECIFICATION_EXECUTOR);
        if (specExec != null) {
            specExecutorCheckBox.setSelected(specExec);
        }
        Boolean addRepoAnn = (Boolean) settings.getProperty(SpringDataRepositoryPanel.ADD_REPOSITORY_ANNOTATION);
        if (addRepoAnn != null) {
            repositoryAnnotationCheckBox.setSelected(addRepoAnn);
        }
        Boolean sampleMethods = (Boolean) settings.getProperty(SpringDataRepositoryPanel.GENERATE_SAMPLE_METHODS);
        if (sampleMethods != null) {
            sampleMethodsCheckBox.setSelected(sampleMethods);
        }
        Boolean ensureDep = (Boolean) settings.getProperty(SpringDataRepositoryPanel.ENSURE_DATA_DEPENDENCY);
        if (ensureDep != null) {
            ensureDependencyCheckBox.setSelected(ensureDep);
        }
    }

    void store(WizardDescriptor settings) {
        String selectedEntity = getComboText(entityComboBox);
        settings.putProperty(SpringDataRepositoryPanel.ENTITY_CLASS, selectedEntity);
        settings.putProperty(SpringDataRepositoryPanel.ID_TYPE, getComboText(idTypeComboBox));
        settings.putProperty(SpringDataRepositoryPanel.REPOSITORY_TYPE, repoTypeComboBox.getSelectedItem());
        settings.putProperty(SpringDataRepositoryPanel.SPECIFICATION_EXECUTOR, specExecutorCheckBox.isSelected());
        settings.putProperty(SpringDataRepositoryPanel.ADD_REPOSITORY_ANNOTATION, repositoryAnnotationCheckBox.isSelected());
        settings.putProperty(SpringDataRepositoryPanel.GENERATE_SAMPLE_METHODS, sampleMethodsCheckBox.isSelected());
        settings.putProperty(SpringDataRepositoryPanel.ENSURE_DATA_DEPENDENCY, ensureDependencyCheckBox.isSelected());

        EntityInfo info = entityByFqn.get(selectedEntity);
        if (info != null) {
            settings.putProperty(SpringDataRepositoryPanel.ENTITY_SAMPLE_PROP_NAME, info.getSamplePropertyName());
            settings.putProperty(SpringDataRepositoryPanel.ENTITY_SAMPLE_PROP_TYPE, info.getSamplePropertyType());
        } else {
            settings.putProperty(SpringDataRepositoryPanel.ENTITY_SAMPLE_PROP_NAME, null);
            settings.putProperty(SpringDataRepositoryPanel.ENTITY_SAMPLE_PROP_TYPE, null);
        }
    }
}
