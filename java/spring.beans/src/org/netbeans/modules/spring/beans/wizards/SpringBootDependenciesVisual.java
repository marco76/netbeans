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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.modules.spring.beans.wizards.SpringInitializrService.DependencyItem;
import org.netbeans.modules.spring.beans.wizards.SpringInitializrService.InitializrMetadata;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.awt.Mnemonics;
import org.openide.util.AsyncGUIJob;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Visual panel for selecting Spring Boot starter modules/dependencies.
 */
public final class SpringBootDependenciesVisual extends JPanel implements AsyncGUIJob {

    private final SpringBootDependenciesWizardPanel panel;
    private boolean initialized = false;
    private final Map<DependencyItem, JCheckBox> dependencyCheckBoxes = new LinkedHashMap<>();

    private JTextField filterField;
    private JPanel nestedPanel;
    private JLabel selectedSummaryLabel;

    public SpringBootDependenciesVisual(SpringBootDependenciesWizardPanel panel) {
        this.panel = panel;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 6));

        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.insets = new Insets(2, 4, 2, 4);
        fgbc.anchor = GridBagConstraints.WEST;

        JLabel filterLabel = new JLabel();
        Mnemonics.setLocalizedText(filterLabel, NbBundle.getMessage(SpringBootDependenciesVisual.class, "LBL_FilterModules"));
        filterField = new JTextField(24);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildNestedPanel();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildNestedPanel();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildNestedPanel();
            }
        });

        fgbc.gridx = 0;
        fgbc.gridy = 0;
        filterPanel.add(filterLabel, fgbc);
        fgbc.gridx = 1;
        fgbc.weightx = 1.0;
        fgbc.fill = GridBagConstraints.HORIZONTAL;
        filterPanel.add(filterField, fgbc);

        add(filterPanel, BorderLayout.NORTH);

        nestedPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(nestedPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        selectedSummaryLabel = new JLabel(" "); //NOI18N
        selectedSummaryLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(selectedSummaryLabel, BorderLayout.SOUTH);
    }

    boolean valid(WizardDescriptor wizardDescriptor) {
        if (wizardDescriptor != null) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, ""); //NOI18N
        }
        return initialized;
    }

    void store(WizardDescriptor wd) {
        Set<DependencyItem> selected = new LinkedHashSet<>();
        for (Map.Entry<DependencyItem, JCheckBox> entry : dependencyCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        wd.putProperty(SpringBootProjectWizardIterator.DEPENDENCIES, selected);
    }

    @SuppressWarnings("unchecked")
    void read(WizardDescriptor wd) {
        Set<DependencyItem> selected = (Set<DependencyItem>) wd.getProperty(SpringBootProjectWizardIterator.DEPENDENCIES);
        if (selected != null && !selected.isEmpty()) {
            for (Map.Entry<DependencyItem, JCheckBox> entry : dependencyCheckBoxes.entrySet()) {
                entry.getValue().setSelected(selected.contains(entry.getKey()));
            }
            updateSelectedSummary();
        }
    }

    void validate(WizardDescriptor d) throws WizardValidationException {
    }

    @Override
    public void construct() {
        String serviceUrl = panel.wizardDescriptor != null
                ? (String) panel.wizardDescriptor.getProperty(SpringBootProjectWizardIterator.SERVICE_URL)
                : SpringInitializrService.DEFAULT_SERVICE_URL;
        InitializrMetadata metadata = SpringInitializrService.getInstance().getMetadata(serviceUrl);
        SwingUtilities.invokeLater(() -> {
            dependencyCheckBoxes.clear();
            for (DependencyItem dep : metadata.getDependencies()) {
                JCheckBox cb = new JCheckBox(dep.getName());
                cb.setToolTipText(dep.getDescription());
                cb.addActionListener(e -> updateSelectedSummary());
                dependencyCheckBoxes.put(dep, cb);
            }
            rebuildNestedPanel();
            updateSelectedSummary();
        });
        if (panel.wizardDescriptor != null) {
            panel.wizardDescriptor.addPropertyChangeListener(evt -> {
                if (SpringBootProjectWizardIterator.SERVICE_URL.equals(evt.getPropertyName())) {
                    refresh();
                }
            });
        }
        initialized = true;
    }

    @Override
    public void finished() {
        panel.fireChangeEvent();
    }

    private void refresh() {
        initialized = false;
        RequestProcessor.getDefault().post(() -> {
            construct();
            SwingUtilities.invokeLater(this::finished);
        });
    }

    private void rebuildNestedPanel() {
        nestedPanel.removeAll();
        String query = filterField != null ? filterField.getText().trim().toLowerCase(Locale.ROOT) : ""; //NOI18N

        Map<String, List<DependencyItem>> byCategory = new LinkedHashMap<>();
        for (DependencyItem item : dependencyCheckBoxes.keySet()) {
            if (query.isEmpty()
                    || item.getName().toLowerCase(Locale.ROOT).contains(query)
                    || item.getId().toLowerCase(Locale.ROOT).contains(query)
                    || item.getCategory().toLowerCase(Locale.ROOT).contains(query)
                    || item.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                byCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
            }
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;

        int row = 0;
        for (Map.Entry<String, List<DependencyItem>> entry : byCategory.entrySet()) {
            JLabel catLabel = new JLabel(entry.getKey());
            catLabel.setFont(catLabel.getFont().deriveFont(Font.BOLD));
            gbc.gridy = row++;
            gbc.insets = new Insets(6, 8, 2, 4);
            nestedPanel.add(catLabel, gbc);

            for (DependencyItem dep : entry.getValue()) {
                JCheckBox cb = dependencyCheckBoxes.get(dep);
                gbc.gridy = row++;
                gbc.insets = new Insets(1, 16, 0, 4);
                nestedPanel.add(cb, gbc);

                if (dep.getDescription() != null && !dep.getDescription().isEmpty()) {
                    JLabel descLabel = new JLabel(dep.getDescription());
                    descLabel.setFont(descLabel.getFont().deriveFont(descLabel.getFont().getStyle() & ~Font.BOLD, descLabel.getFont().getSize() - 1f));
                    gbc.gridy = row++;
                    gbc.insets = new Insets(0, 36, 3, 4);
                    nestedPanel.add(descLabel, gbc);
                }
            }
        }

        gbc.gridy = row;
        gbc.weighty = 1.0;
        nestedPanel.add(new JPanel(), gbc);
        nestedPanel.revalidate();
        nestedPanel.repaint();
    }

    private void updateSelectedSummary() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<DependencyItem, JCheckBox> entry : dependencyCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                names.add(entry.getKey().getName());
            }
        }
        if (names.isEmpty()) {
            selectedSummaryLabel.setText(NbBundle.getMessage(SpringBootDependenciesVisual.class, "LBL_NoModulesSelected"));
        } else {
            selectedSummaryLabel.setText(NbBundle.getMessage(SpringBootDependenciesVisual.class, "LBL_SelectedModules", String.join(", ", names)));
        }
    }
}
