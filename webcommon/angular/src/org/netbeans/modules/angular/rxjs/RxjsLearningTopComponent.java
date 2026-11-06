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
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.*;
import org.openide.util.NbBundle.Messages;
import org.openide.util.datatransfer.ExClipboard;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** Dockable offline reference and explicit editor-selection analysis. */
@TopComponent.Description(preferredID="RxjsLearningTopComponent", persistenceType=TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode="output", openAtStartup=false)
@ActionID(category="Window", id="org.netbeans.modules.angular.rxjs.RxjsLearningTopComponent")
@ActionReference(path="Menu/Window", position=1850)
@TopComponent.OpenActionRegistration(displayName="#CTL_RxjsLearning", preferredID="RxjsLearningTopComponent")
@Messages("CTL_RxjsLearning=RxJS Guide and Pipeline Explorer")
public final class RxjsLearningTopComponent extends TopComponent {
    private final RxjsPipelinePanel inspector = new RxjsPipelinePanel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField search = new JTextField();
    private final DefaultListModel<RxjsOperators.Operator> model = new DefaultListModel<>();
    private final JList<RxjsOperators.Operator> operators = new JList<>(model);
    private final JEditorPane reference = html();
    private final JEditorPane explanation = html();
    private final JLabel status = new JLabel("Select TypeScript code in the editor, then click Explain selection / file.");
    public RxjsLearningTopComponent() {
        setName(Bundle.CTL_RxjsLearning());
        setToolTipText("Search RxJS operators, Angular interop and pipeline behavior");
        setLayout(new BorderLayout(8, 8));
        search.setToolTipText("Filter by operator name, category or purpose");
        search.getAccessibleContext().setAccessibleName("Search RxJS operators");
        operators.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        operators.getAccessibleContext().setAccessibleName("RxJS operators");
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Operators and Angular interop"), BorderLayout.NORTH);
        JPanel list = new JPanel(new BorderLayout(4, 4));
        list.add(search, BorderLayout.NORTH); list.add(new JScrollPane(operators), BorderLayout.CENTER);
        left.add(list, BorderLayout.CENTER);

        tabs.addTab("Documentation", new JScrollPane(reference));
        tabs.addTab("Pipeline", new JScrollPane(explanation));
        tabs.addTab("Visual inspector", inspector);
        JPanel mapping = new JPanel(new BorderLayout(6, 6));
        JComboBox<String> behavior = new JComboBox<>(new String[]{"Cancel previous → switchMap", "Queue → concatMap", "Concurrent → mergeMap", "Ignore while busy → exhaustMap"});
        JEditorPane mappingInfo = html();
        Runnable chooseMapping = () -> {
            String name = new String[]{"switchMap", "concatMap", "mergeMap", "exhaustMap"}[behavior.getSelectedIndex()];
            mappingInfo.setText(RxjsOperators.find(name).html()); mappingInfo.setCaretPosition(0);
        };
        behavior.addActionListener(e -> chooseMapping.run());
        chooseMapping.run();
        mapping.add(behavior, BorderLayout.NORTH);
        mapping.add(new JScrollPane(mappingInfo), BorderLayout.CENTER);
        tabs.addTab("Choose mapping operator", mapping);
        tabs.addTab("Marble testing", new RxjsMarblePanel(this::copy));
        tabs.addTab("Stream comparison", new RxjsTimeline());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
        split.setResizeWeight(.25); split.setDividerLocation(250);
        add(split, BorderLayout.CENTER);
        JPanel toolbar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
        JButton analyze = new JButton("Explain selection / file");
        analyze.addActionListener(e -> {
            JTextComponent editor = EditorRegistry.lastFocusedComponent();
            if (editor == null || !java.util.Set.of("text/typescript", "application/x-typescript").contains(editor.getDocument().getProperty("mimeType"))) {
                status.setText("Open a TypeScript editor first."); return;
            }
            String selected = editor.getSelectedText();
            try {
                String source = selected == null ? editor.getDocument().getText(0, editor.getDocument().getLength()) : selected;
                if (source.length() > 200000) { status.setText("Select a pipeline smaller than 200 KB to analyze."); return; }
                explanation.setText(RxjsAnalysis.explain(source)); explanation.setCaretPosition(0);
                tabs.setSelectedIndex(1);
                status.setText(selected == null ? "Analyzed current file. Review suggestions are syntax-based." : "Analyzed selected code. Review suggestions are syntax-based.");
            } catch (javax.swing.text.BadLocationException ex) { status.setText("Editor changed; select the code again."); }
        });
        JButton copy = new JButton("Copy example");
        copy.addActionListener(e -> { var op = operators.getSelectedValue(); if (op != null) copy(op.example()); });
        toolbar.add(analyze); toolbar.add(copy);
        add(toolbar, BorderLayout.NORTH); add(status, BorderLayout.SOUTH);
        operators.addListSelectionListener(e -> {
            var op = operators.getSelectedValue();
            if (op != null) { reference.setText(op.html()); reference.setCaretPosition(0); }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
        filter();
    }
    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (var op : RxjsOperators.ALL) if ((op.name() + " " + op.category() + " " + op.summary()).toLowerCase(Locale.ROOT).contains(query)) model.addElement(op);
        if (!model.isEmpty()) operators.setSelectedIndex(0);
        else reference.setText("<html><p>No matching operators.</p></html>");
    }
    public static void showOperator(String name) {
        TopComponent component = WindowManager.getDefault().findTopComponent("RxjsLearningTopComponent");
        if (component == null) component = new RxjsLearningTopComponent();
        component.open(); component.requestActive();
        if (component instanceof RxjsLearningTopComponent guide) {
            guide.search.setText(name); guide.filter();
        }
    }
    public static void inspectPipeline(JTextComponent editor) {
        try {
            String snapshot = editor.getDocument().getText(0, editor.getDocument().getLength());
            String selected = editor.getSelectedText();
            String source = selected == null ? snapshot : selected;
            if (source.length() > 200000) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText("Select a pipeline smaller than 200 KB to inspect.");
                return;
            }
            TopComponent component = WindowManager.getDefault().findTopComponent("RxjsLearningTopComponent");
            if (component == null) component = new RxjsLearningTopComponent();
            if (component instanceof RxjsLearningTopComponent guide) {
                int base = selected == null ? 0 : editor.getSelectionStart();
                guide.inspector.inspect(editor, source, snapshot, base, editor.getCaretPosition() - base);
                guide.tabs.setSelectedComponent(guide.inspector);
                component.open(); component.requestActive();
            }
        } catch (javax.swing.text.BadLocationException ex) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText("Editor changed; inspect the pipeline again.");
        }
    }
    private static JEditorPane html() {
        JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false); pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try {
                    URI uri = e.getURL().toURI();
                    if ("https".equals(uri.getScheme()) && java.util.Set.of("rxjs.dev", "angular.dev").contains(uri.getHost()))
                        HtmlBrowser.URLDisplayer.getDefault().showURL(e.getURL());
                } catch (java.net.URISyntaxException ex) { /* malformed link */ }
            }
        });
        return pane;
    }
    private void copy(String text) {
        ExClipboard clipboard = Lookup.getDefault().lookup(ExClipboard.class);
        if (clipboard != null) { clipboard.setContents(new StringSelection(text), null); status.setText("Copied example. Adapt imports and identifiers to your project."); }
        else status.setText("Clipboard unavailable; select and copy the example manually.");
    }
}
