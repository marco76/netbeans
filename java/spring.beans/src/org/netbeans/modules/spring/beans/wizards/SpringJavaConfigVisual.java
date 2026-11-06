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
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.modules.spring.api.SpringUtilities;
import org.netbeans.modules.spring.beans.wizards.ProjectSpringDependencyModifier.BuildType;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.EnableAnnotation;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.SpringModule;
import org.openide.WizardDescriptor;
import org.openide.awt.Mnemonics;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * Visual component for configuring Spring modules/dependencies and {@code @Configuration}
 * annotations in the Spring Java Configuration wizard.
 */
public final class SpringJavaConfigVisual extends JPanel {

    private final SpringJavaConfigPanel panel;
    private final Map<SpringModule, JCheckBox> moduleCheckBoxes = new LinkedHashMap<>();
    private final Map<EnableAnnotation, JCheckBox> enableCheckBoxes = new LinkedHashMap<>();
    private final List<Library> antSpringLibraries = new ArrayList<>();
    private Set<String> presentModuleIds = Collections.emptySet();

    private JTabbedPane tabbedPane;
    private JRadioButton springBootRadio;
    private JRadioButton springFrameworkRadio;
    private JTextField springVersionField;
    private JTextField filterField;
    private JPanel modulesContainer;
    private JCheckBox addAntLibraryCheckBox;
    private JComboBox<String> antLibraryComboBox;
    private JPanel antLibraryPanel;

    private JCheckBox componentScanCheckBox;
    private JTextField componentScanField;
    private JCheckBox propertySourceCheckBox;
    private JComboBox<String> propertySourceComboBox;
    private JCheckBox importConfigsCheckBox;
    private JTextField importConfigsField;
    private JCheckBox sampleBeanCheckBox;

    public SpringJavaConfigVisual(SpringJavaConfigPanel panel) {
        this.panel = panel;
        initComponents();
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(SpringJavaConfigVisual.class, "LBL_SpringJavaConfig_Step");
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 8));
        tabbedPane = new JTabbedPane();

        JPanel modulesTab = createModulesTab();
        JPanel annotationsTab = createAnnotationsTab();

        tabbedPane.addTab(NbBundle.getMessage(SpringJavaConfigVisual.class, "TAB_SpringModules"), modulesTab);
        tabbedPane.addTab(NbBundle.getMessage(SpringJavaConfigVisual.class, "TAB_ConfigurationAnnotations"), annotationsTab);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createModulesTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel modeLabel = new JLabel();
        Mnemonics.setLocalizedText(modeLabel, NbBundle.getMessage(SpringJavaConfigVisual.class, "LBL_DependencyType"));
        gbc.gridx = 0;
        gbc.gridy = 0;
        headerPanel.add(modeLabel, gbc);

        springBootRadio = new JRadioButton();
        Mnemonics.setLocalizedText(springBootRadio, NbBundle.getMessage(SpringJavaConfigVisual.class, "RBTN_SpringBootStarters"));
        springFrameworkRadio = new JRadioButton();
        Mnemonics.setLocalizedText(springFrameworkRadio, NbBundle.getMessage(SpringJavaConfigVisual.class, "RBTN_SpringFrameworkModules"));

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(springBootRadio);
        modeGroup.add(springFrameworkRadio);
        springBootRadio.setSelected(true);

        springBootRadio.addActionListener(e -> updateModuleLabelsAndCoordinates());
        springFrameworkRadio.addActionListener(e -> updateModuleLabelsAndCoordinates());

        gbc.gridx = 1;
        headerPanel.add(springBootRadio, gbc);
        gbc.gridx = 2;
        headerPanel.add(springFrameworkRadio, gbc);

        JLabel versionLabel = new JLabel();
        Mnemonics.setLocalizedText(versionLabel, NbBundle.getMessage(SpringJavaConfigVisual.class, "LBL_SpringVersion"));
        gbc.gridx = 0;
        gbc.gridy = 1;
        headerPanel.add(versionLabel, gbc);

        springVersionField = new JTextField(ProjectSpringDependencyModifier.DEFAULT_SPRING_FRAMEWORK_VERSION, 12);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        headerPanel.add(springVersionField, gbc);

        JLabel filterLabel = new JLabel();
        Mnemonics.setLocalizedText(filterLabel, NbBundle.getMessage(SpringJavaConfigVisual.class, "LBL_FilterModules"));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        headerPanel.add(filterLabel, gbc);

        filterField = new JTextField(24);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildModulesList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildModulesList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildModulesList();
            }
        });
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        headerPanel.add(filterField, gbc);

        tab.add(headerPanel, BorderLayout.NORTH);

        modulesContainer = new JPanel(new GridBagLayout());
        for (SpringModule module : SpringModuleCatalog.getModules()) {
            JCheckBox cb = new JCheckBox();
            cb.addActionListener(e -> {
                if (cb.isSelected() && module.getEnableAnnotationFqn() != null && springFrameworkRadio.isSelected()) {
                    for (Map.Entry<EnableAnnotation, JCheckBox> entry : enableCheckBoxes.entrySet()) {
                        if (module.getEnableAnnotationFqn().equals(entry.getKey().getFqn())) {
                            entry.getValue().setSelected(true);
                        }
                    }
                }
                panel.fireChangeEvent();
            });
            moduleCheckBoxes.put(module, cb);
        }
        updateModuleLabelsAndCoordinates();
        rebuildModulesList();

        JScrollPane scrollPane = new JScrollPane(modulesContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tab.add(scrollPane, BorderLayout.CENTER);

        antLibraryPanel = new JPanel(new GridBagLayout());
        GridBagConstraints antGbc = new GridBagConstraints();
        antGbc.insets = new Insets(4, 4, 2, 4);
        antGbc.anchor = GridBagConstraints.WEST;
        addAntLibraryCheckBox = new JCheckBox();
        Mnemonics.setLocalizedText(addAntLibraryCheckBox, NbBundle.getMessage(SpringJavaConfigVisual.class, "LBL_AddSpringFramework"));
        antLibraryComboBox = new JComboBox<>();
        antGbc.gridx = 0;
        antLibraryPanel.add(addAntLibraryCheckBox, antGbc);
        antGbc.gridx = 1;
        antGbc.weightx = 1.0;
        antGbc.fill = GridBagConstraints.HORIZONTAL;
        antLibraryPanel.add(antLibraryComboBox, antGbc);
        antLibraryPanel.setVisible(false);
        tab.add(antLibraryPanel, BorderLayout.SOUTH);

        return tab;
    }

    private JPanel createAnnotationsTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topOptions = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;

        componentScanCheckBox = new JCheckBox();
        Mnemonics.setLocalizedText(componentScanCheckBox, NbBundle.getMessage(SpringJavaConfigVisual.class, "CHK_ComponentScan"));
        componentScanCheckBox.setSelected(true);
        componentScanField = new JTextField(25);
        componentScanCheckBox.addActionListener(e -> componentScanField.setEnabled(componentScanCheckBox.isSelected()));

        gbc.gridx = 0;
        gbc.gridy = 0;
        topOptions.add(componentScanCheckBox, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        topOptions.add(componentScanField, gbc);

        propertySourceCheckBox = new JCheckBox();
        Mnemonics.setLocalizedText(propertySourceCheckBox, NbBundle.getMessage(SpringJavaConfigVisual.class, "CHK_PropertySource"));
        propertySourceComboBox = new JComboBox<>();
        propertySourceComboBox.setEditable(true);
        propertySourceComboBox.setEnabled(false);
        propertySourceCheckBox.addActionListener(e -> propertySourceComboBox.setEnabled(propertySourceCheckBox.isSelected()));

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        topOptions.add(propertySourceCheckBox, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        topOptions.add(propertySourceComboBox, gbc);

        importConfigsCheckBox = new JCheckBox();
        Mnemonics.setLocalizedText(importConfigsCheckBox, NbBundle.getMessage(SpringJavaConfigVisual.class, "CHK_ImportConfigs"));
        importConfigsField = new JTextField(25);
        importConfigsField.setEnabled(false);
        importConfigsCheckBox.addActionListener(e -> importConfigsField.setEnabled(importConfigsCheckBox.isSelected()));

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        topOptions.add(importConfigsCheckBox, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        topOptions.add(importConfigsField, gbc);

        sampleBeanCheckBox = new JCheckBox();
        Mnemonics.setLocalizedText(sampleBeanCheckBox, NbBundle.getMessage(SpringJavaConfigVisual.class, "CHK_SampleBean"));
        sampleBeanCheckBox.setSelected(true);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        topOptions.add(sampleBeanCheckBox, gbc);

        tab.add(topOptions, BorderLayout.NORTH);

        JPanel enablePanel = new JPanel(new GridBagLayout());
        GridBagConstraints egbc = new GridBagConstraints();
        egbc.gridx = 0;
        egbc.anchor = GridBagConstraints.WEST;
        egbc.fill = GridBagConstraints.HORIZONTAL;
        egbc.weightx = 1.0;
        egbc.insets = new Insets(2, 6, 2, 6);

        int row = 0;
        JLabel enableHeader = new JLabel(NbBundle.getMessage(SpringJavaConfigVisual.class, "LBL_EnableModuleAnnotations"));
        enableHeader.setFont(enableHeader.getFont().deriveFont(Font.BOLD));
        egbc.gridy = row++;
        enablePanel.add(enableHeader, egbc);

        for (EnableAnnotation ea : SpringModuleCatalog.getEnableAnnotations()) {
            JCheckBox cb = new JCheckBox(ea.getDescription());
            cb.setToolTipText(ea.getFqn());
            enableCheckBoxes.put(ea, cb);
            egbc.gridy = row++;
            enablePanel.add(cb, egbc);
        }

        egbc.gridy = row;
        egbc.weighty = 1.0;
        enablePanel.add(new JPanel(), egbc);

        JScrollPane enableScroll = new JScrollPane(enablePanel);
        enableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        tab.add(enableScroll, BorderLayout.CENTER);

        return tab;
    }

    void configureContext(Project project, ClassPath compileClassPath, String defaultPackage) {
        BuildType buildType = ProjectSpringDependencyModifier.detectBuildType(project);
        boolean isBoot = ProjectSpringDependencyModifier.isSpringBootProject(project, compileClassPath);
        presentModuleIds = ProjectSpringDependencyModifier.detectPresentModuleIds(project, compileClassPath);

        if (isBoot || buildType == BuildType.MAVEN || buildType == BuildType.GRADLE) {
            springBootRadio.setSelected(isBoot);
            springFrameworkRadio.setSelected(!isBoot);
        }
        updateModuleLabelsAndCoordinates();

        if (buildType == BuildType.ANT) {
            boolean hasSpring = compileClassPath != null && SpringUtilities.containsSpring(compileClassPath);
            antLibraryPanel.setVisible(!hasSpring);
            if (!hasSpring && antSpringLibraries.isEmpty()) {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                for (Library lib : SpringUtilities.getJavaLibraries()) {
                    if (SpringUtilities.isSpringLibrary(lib)) {
                        antSpringLibraries.add(lib);
                        model.addElement(lib.getDisplayName());
                    }
                }
                antLibraryComboBox.setModel(model);
            }
        } else {
            antLibraryPanel.setVisible(false);
        }

        if (componentScanField.getText().trim().isEmpty() && defaultPackage != null && !defaultPackage.isEmpty()) {
            componentScanField.setText(defaultPackage);
        }

        List<String> propResources = discoverPropertiesFiles(project);
        DefaultComboBoxModel<String> propModel = new DefaultComboBoxModel<>();
        for (String res : propResources) {
            propModel.addElement(res);
        }
        if (propModel.getSize() == 0) {
            propModel.addElement("classpath:application.properties"); //NOI18N
        }
        propertySourceComboBox.setModel(propModel);
    }

    private static List<String> discoverPropertiesFiles(Project project) {
        if (project == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        Sources sources = ProjectUtils.getSources(project);
        List<SourceGroup> groups = new ArrayList<>();
        Collections.addAll(groups, sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_RESOURCES));
        Collections.addAll(groups, sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA));
        for (SourceGroup sg : groups) {
            FileObject root = sg.getRootFolder();
            if (root == null) {
                continue;
            }
            Enumeration<? extends FileObject> children = root.getChildren(true);
            while (children.hasMoreElements()) {
                FileObject fo = children.nextElement();
                if (fo.isData() && "properties".equalsIgnoreCase(fo.getExt())) { //NOI18N
                    String rel = FileUtil.getRelativePath(root, fo);
                    if (rel != null && !rel.startsWith("META-INF/")) { //NOI18N
                        String uri = "classpath:" + rel; //NOI18N
                        if (!result.contains(uri)) {
                            result.add(uri);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void updateModuleLabelsAndCoordinates() {
        boolean boot = springBootRadio.isSelected();
        springVersionField.setEnabled(!boot);
        for (Map.Entry<SpringModule, JCheckBox> entry : moduleCheckBoxes.entrySet()) {
            SpringModule m = entry.getKey();
            JCheckBox cb = entry.getValue();
            boolean present = presentModuleIds.contains(m.getId());
            String coords = m.getCoordinates(boot);
            String label = m.getTitle() + " (" + coords + ")" + (present ? " [configured]" : ""); //NOI18N
            cb.setText(label);
            cb.setToolTipText(m.getDescription());
        }
    }

    private void rebuildModulesList() {
        modulesContainer.removeAll();
        String query = filterField != null ? filterField.getText().trim().toLowerCase(Locale.ROOT) : ""; //NOI18N

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;
        for (Map.Entry<String, List<SpringModule>> catEntry : SpringModuleCatalog.getModulesByCategory().entrySet()) {
            List<SpringModule> matching = new ArrayList<>();
            for (SpringModule m : catEntry.getValue()) {
                if (query.isEmpty()
                        || m.getTitle().toLowerCase(Locale.ROOT).contains(query)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(query)
                        || m.getCategory().toLowerCase(Locale.ROOT).contains(query)
                        || m.getCoordinates(springBootRadio.isSelected()).toLowerCase(Locale.ROOT).contains(query)) {
                    matching.add(m);
                }
            }
            if (matching.isEmpty()) {
                continue;
            }
            JLabel catLabel = new JLabel(catEntry.getKey());
            catLabel.setFont(catLabel.getFont().deriveFont(Font.BOLD));
            gbc.gridy = row++;
            gbc.insets = new Insets(6, 6, 2, 6);
            modulesContainer.add(catLabel, gbc);

            for (SpringModule m : matching) {
                JCheckBox cb = moduleCheckBoxes.get(m);
                gbc.gridy = row++;
                gbc.insets = new Insets(1, 16, 1, 6);
                modulesContainer.add(cb, gbc);

                JLabel descLabel = new JLabel(m.getDescription());
                descLabel.setFont(descLabel.getFont().deriveFont(descLabel.getFont().getStyle() & ~Font.BOLD, descLabel.getFont().getSize() - 1f));
                gbc.gridy = row++;
                gbc.insets = new Insets(0, 36, 3, 6);
                modulesContainer.add(descLabel, gbc);
            }
        }

        gbc.gridy = row;
        gbc.weighty = 1.0;
        modulesContainer.add(new JPanel(), gbc);
        modulesContainer.revalidate();
        modulesContainer.repaint();
    }

    @SuppressWarnings("unchecked")
    void read(WizardDescriptor settings) {
        Boolean bootMode = (Boolean) settings.getProperty(SpringJavaConfigPanel.SPRING_BOOT_MODE);
        if (bootMode != null) {
            springBootRadio.setSelected(bootMode);
            springFrameworkRadio.setSelected(!bootMode);
            updateModuleLabelsAndCoordinates();
        }
        String ver = (String) settings.getProperty(SpringJavaConfigPanel.SPRING_VERSION);
        if (ver != null && !ver.isEmpty()) {
            springVersionField.setText(ver);
        }
        Set<SpringModule> selectedMods = (Set<SpringModule>) settings.getProperty(SpringJavaConfigPanel.SELECTED_MODULES);
        if (selectedMods != null) {
            for (Map.Entry<SpringModule, JCheckBox> entry : moduleCheckBoxes.entrySet()) {
                entry.getValue().setSelected(selectedMods.contains(entry.getKey()));
            }
        }
        Set<EnableAnnotation> selectedEnables = (Set<EnableAnnotation>) settings.getProperty(SpringJavaConfigPanel.ENABLE_ANNOTATIONS);
        if (selectedEnables != null) {
            for (Map.Entry<EnableAnnotation, JCheckBox> entry : enableCheckBoxes.entrySet()) {
                entry.getValue().setSelected(selectedEnables.contains(entry.getKey()));
            }
        }
        Boolean compScan = (Boolean) settings.getProperty(SpringJavaConfigPanel.COMPONENT_SCAN);
        if (compScan != null) {
            componentScanCheckBox.setSelected(compScan);
            componentScanField.setEnabled(compScan);
        }
        String compScanPkgs = (String) settings.getProperty(SpringJavaConfigPanel.COMPONENT_SCAN_PACKAGES);
        if (compScanPkgs != null) {
            componentScanField.setText(compScanPkgs);
        }
        Boolean propSourceEnabled = (Boolean) settings.getProperty(SpringJavaConfigPanel.PROPERTY_SOURCE_ENABLED);
        if (propSourceEnabled != null) {
            propertySourceCheckBox.setSelected(propSourceEnabled);
            propertySourceComboBox.setEnabled(propSourceEnabled);
        }
        String propSource = (String) settings.getProperty(SpringJavaConfigPanel.PROPERTY_SOURCE);
        if (propSource != null && !propSource.isEmpty()) {
            propertySourceComboBox.setSelectedItem(propSource);
        }
        Boolean impEnabled = (Boolean) settings.getProperty(SpringJavaConfigPanel.IMPORT_CONFIGS_ENABLED);
        if (impEnabled != null) {
            importConfigsCheckBox.setSelected(impEnabled);
            importConfigsField.setEnabled(impEnabled);
        }
        String impConfigs = (String) settings.getProperty(SpringJavaConfigPanel.IMPORT_CONFIGS);
        if (impConfigs != null) {
            importConfigsField.setText(impConfigs);
        }
        Boolean sampleBean = (Boolean) settings.getProperty(SpringJavaConfigPanel.GENERATE_SAMPLE_BEAN);
        if (sampleBean != null) {
            sampleBeanCheckBox.setSelected(sampleBean);
        }
    }

    void store(WizardDescriptor settings) {
        Set<SpringModule> selectedMods = new LinkedHashSet<>();
        for (Map.Entry<SpringModule, JCheckBox> entry : moduleCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedMods.add(entry.getKey());
            }
        }
        settings.putProperty(SpringJavaConfigPanel.SELECTED_MODULES, selectedMods);
        settings.putProperty(SpringJavaConfigPanel.SPRING_BOOT_MODE, springBootRadio.isSelected());
        settings.putProperty(SpringJavaConfigPanel.SPRING_VERSION, springVersionField.getText().trim());
        settings.putProperty(SpringJavaConfigPanel.ADD_SPRING_LIBRARY, addAntLibraryCheckBox.isSelected());
        int libIdx = antLibraryComboBox.getSelectedIndex();
        if (libIdx >= 0 && libIdx < antSpringLibraries.size()) {
            settings.putProperty(SpringJavaConfigPanel.SPRING_LIBRARY, antSpringLibraries.get(libIdx));
        }

        Set<EnableAnnotation> selectedEnables = new LinkedHashSet<>();
        for (Map.Entry<EnableAnnotation, JCheckBox> entry : enableCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedEnables.add(entry.getKey());
            }
        }
        settings.putProperty(SpringJavaConfigPanel.ENABLE_ANNOTATIONS, selectedEnables);
        settings.putProperty(SpringJavaConfigPanel.COMPONENT_SCAN, componentScanCheckBox.isSelected());
        settings.putProperty(SpringJavaConfigPanel.COMPONENT_SCAN_PACKAGES, componentScanField.getText().trim());
        settings.putProperty(SpringJavaConfigPanel.PROPERTY_SOURCE_ENABLED, propertySourceCheckBox.isSelected());
        Object propItem = propertySourceComboBox.getEditor().getItem();
        if (propItem == null) {
            propItem = propertySourceComboBox.getSelectedItem();
        }
        settings.putProperty(SpringJavaConfigPanel.PROPERTY_SOURCE, propItem != null ? propItem.toString().trim() : ""); //NOI18N
        settings.putProperty(SpringJavaConfigPanel.IMPORT_CONFIGS_ENABLED, importConfigsCheckBox.isSelected());
        settings.putProperty(SpringJavaConfigPanel.IMPORT_CONFIGS, importConfigsField.getText().trim());
        settings.putProperty(SpringJavaConfigPanel.GENERATE_SAMPLE_BEAN, sampleBeanCheckBox.isSelected());
    }
}
