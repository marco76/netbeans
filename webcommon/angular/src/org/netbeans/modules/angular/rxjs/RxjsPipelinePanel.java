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
package org.netbeans.modules.angular.rxjs;

import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Selectable pipeline stages with explicit navigation back to the analyzed editor. */
final class RxjsPipelinePanel extends JPanel {
    private final JComboBox<RxjsPipeline.Pipe> pipelines = new JComboBox<>();
    private final DefaultListModel<RxjsPipeline.Stage> model = new DefaultListModel<>();
    private final JList<RxjsPipeline.Stage> stages = new JList<>(model);
    private final JEditorPane detail = new JEditorPane("text/html", "");
    private final JLabel status = new JLabel("Use Inspect RxJS Pipeline in a TypeScript editor.");
    private JTextComponent editor;
    private String snapshot;
    private int base;

    RxjsPipelinePanel() {
        super(new BorderLayout(6, 6));
        detail.setEditable(false);
        detail.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        stages.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stages.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.setText((index + 1) + "  →  " + value);
                label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                return label;
            }
        });
        stages.getAccessibleContext().setAccessibleName("Pipeline stages in execution order");
        pipelines.getAccessibleContext().setAccessibleName("Pipelines in analyzed code");
        add(pipelines, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(stages), new JScrollPane(detail));
        split.setResizeWeight(.3);
        add(split, BorderLayout.CENTER);
        JButton navigate = new JButton("Go to selected stage");
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(navigate, BorderLayout.EAST); footer.add(status, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        pipelines.addActionListener(e -> {
            model.clear();
            var pipe = (RxjsPipeline.Pipe) pipelines.getSelectedItem();
            if (pipe != null) pipe.stages().forEach(model::addElement);
            if (!model.isEmpty()) stages.setSelectedIndex(0);
            else detail.setText(RxjsDocumentation.wrap("<p>No stages in this pipeline.</p>"));
        });
        stages.addListSelectionListener(e -> {
            var stage = stages.getSelectedValue();
            if (stage == null) return;
            var op = RxjsOperators.find(stage.name());
            String heading = "<p class='category'>Stage " + (stages.getSelectedIndex() + 1)
                    + " of " + model.size() + " · Source order</p><pre>"
                    + RxjsOperators.escape(stage.expression()) + "</pre><p>Value flow is conceptual; concrete TypeScript types are not inferred.</p>";
            String html = op == null ? RxjsDocumentation.wrap("<p>Custom or unresolved operator. Navigate to the source for its definition.</p>") : op.html();
            detail.setText(html.replace("<body>", "<body>" + heading));
            detail.setCaretPosition(0);
        });
        navigate.addActionListener(e -> {
            var stage = stages.getSelectedValue();
            if (editor == null || stage == null) return;
            try {
                if (!snapshot.equals(editor.getDocument().getText(0, editor.getDocument().getLength()))) {
                    status.setText("Code changed. Inspect the pipeline again before navigating."); return;
                }
                editor.requestFocusInWindow(); editor.setCaretPosition(base + stage.offset());
            } catch (javax.swing.text.BadLocationException ex) { status.setText("Inspect the pipeline again."); }
        });
    }
    void inspect(JTextComponent editor, String source, String snapshot, int base, int caret) {
        this.editor = editor; this.snapshot = snapshot; this.base = base;
        pipelines.removeAllItems();
        detail.setText("");
        var parsed = RxjsPipeline.parse(source);
        RxjsPipeline.Pipe chosen = null;
        for (var pipe : parsed) {
            pipelines.addItem(pipe);
            if (caret >= pipe.offset() && caret <= pipe.end()
                    && (chosen == null || pipe.offset() > chosen.offset())) chosen = pipe;
        }
        if (chosen != null) pipelines.setSelectedItem(chosen);
        status.setText(parsed.isEmpty() ? "No .pipe(...) found in the analyzed code." : "Select a stage for documentation and marble previews.");
    }
}
