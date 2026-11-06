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
package org.netbeans.modules.java.editor.semantic;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.*;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.*;
import org.netbeans.modules.java.editor.semantic.MethodUsagesSideBarFactory.Usage;

/** A bounded, keyboard navigable view of semantic usage results. */
final class MethodUsagesPopup extends JPopupMenu {
    private final List<Usage> usages;
    final JTextField filter = new JTextField();
    final DefaultListModel<Usage> model = new DefaultListModel<>();
    final JList<Usage> results = new JList<>(model);
    private final JLabel count = new JLabel();

    MethodUsagesPopup(List<Usage> usages, Action findAll) {
        this.usages = List.copyOf(usages);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel heading = new JPanel(new BorderLayout(0, 6));
        heading.add(new JLabel("Usages in the source classpath"), BorderLayout.NORTH);
        filter.putClientProperty("JTextField.placeholderText", "Filter by caller, code or file…");
        filter.getAccessibleContext().setAccessibleName("Filter method usages");
        heading.add(filter, BorderLayout.CENTER);
        heading.add(new JLabel("Exact references • excludes binaries and reflection"), BorderLayout.SOUTH);
        content.add(heading, BorderLayout.NORTH);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.getAccessibleContext().setAccessibleName("Method usage results");
        results.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                Usage usage = (Usage) value;
                String preview = html(usage.preview);
                String reference = html(usage.reference);
                if (!reference.isEmpty()) preview = preview.replace(reference, "<b>" + reference + "</b>");
                label.setText("<html><b>" + html(usage.caller) + "</b> — "
                        + html(usage.file.getNameExt()) + ":" + usage.line
                        + "<br><code>" + preview + "</code><br>" + html(usage.file.getPath()) + "</html>");
                label.setToolTipText(usage.file.getPath());
                label.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                return label;
            }
        });
        JScrollPane scroll = new JScrollPane(results);
        scroll.setPreferredSize(new Dimension(620, 300));
        content.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(count, BorderLayout.CENTER);
        JButton all = new JButton("Find All Usages…");
        all.setEnabled(findAll != null && findAll.isEnabled());
        all.setToolTipText("Open Find Usages to choose a broader search scope");
        all.addActionListener(e -> {
            setVisible(false);
            if (findAll != null) findAll.actionPerformed(new ActionEvent(all, ActionEvent.ACTION_PERFORMED, "find-usages"));
        });
        footer.add(all, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        add(content);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });
        filter.addActionListener(e -> openSelected());
        filter.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next-result");
        filter.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous-result");
        for (String name : List.of("next-result", "previous-result")) {
            filter.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    int index = results.getSelectedIndex() + (name.equals("next-result") ? 1 : -1);
                    results.setSelectedIndex(Math.max(0, Math.min(model.size() - 1, index)));
                    results.ensureIndexIsVisible(results.getSelectedIndex());
                }
            });
        }
        results.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-usage");
        results.getActionMap().put("open-usage", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openSelected(); }
        });
        results.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int index = results.locationToIndex(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && index >= 0
                        && results.getCellBounds(index, index).contains(e.getPoint())) openSelected();
            }
        });
        content.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-usages");
        content.getActionMap().put("close-usages", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { setVisible(false); }
        });
        addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> filter.requestFocusInWindow());
            }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { }
        });
        update();
    }

    private void update() {
        model.clear();
        String query = filter.getText().strip().toLowerCase(Locale.ROOT);
        for (Usage usage : usages) {
            if ((usage.caller + " " + usage.preview + " " + usage.file.getPath())
                    .toLowerCase(Locale.ROOT).contains(query)) model.addElement(usage);
        }
        if (!model.isEmpty()) results.setSelectedIndex(0);
        count.setText(model.isEmpty() ? (usages.isEmpty() ? "No usages in this scope" : "No matching usages")
                : model.size() + " of " + usages.size() + " usages • ↑↓ select · Enter open");
    }

    private void openSelected() {
        Usage usage = results.getSelectedValue();
        if (usage == null) return;
        setVisible(false);
        try {
            org.openide.text.NbDocument.openDocument(org.openide.loaders.DataObject.find(usage.file), usage.offset,
                    org.openide.text.Line.ShowOpenType.OPEN, org.openide.text.Line.ShowVisibilityType.FOCUS);
        } catch (org.openide.loaders.DataObjectNotFoundException ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
        }
    }

    static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
