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
package org.netbeans.modules.angular.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import org.netbeans.modules.angular.debug.*;
import org.netbeans.modules.angular.model.*;
import org.openide.*;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.*;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.RequestProcessor;

/** Lifecycle observation timeline shared by Firefox and other development browsers. */
public final class AngularRenderTimeline {
    private static final RequestProcessor WORKER=new RequestProcessor(AngularRenderTimeline.class);
    private AngularRenderTimeline() {}
    public static void show(FileObject root) {
        RenderTrace trace=new RenderTrace();
        RenderTraceReceiver[] receiver={null};
        JPanel panel=new JPanel(new BorderLayout(8,8)); panel.setPreferredSize(new Dimension(1000,620));
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JTextField origin=new JTextField("http://localhost:4200",22);
        JButton listen=new JButton("Start receiver"); JButton setup=new JButton("Set up companion…");
        JButton load=new JButton("Import trace…"); JButton clear=new JButton("Clear");
        JToggleButton pause=new JToggleButton("Pause display");
        toolbar.add(new JLabel("Application origin:")); toolbar.add(origin); toolbar.add(listen); toolbar.add(setup); toolbar.add(load); toolbar.add(clear); toolbar.add(pause);
        panel.add(toolbar,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Time","Component / instance","Observed event","Details"},0) {
            @Override public boolean isCellEditable(int r,int c) { return false; }
        };
        JTable table=new JTable(model); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<TableModel> sorter=new TableRowSorter<>(model); table.setRowSorter(sorter);
        JTextArea details=new JTextArea(8,80); details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setText("Checks are lifecycle observations, not proof that the DOM rendered. Input changes are recorded by ngOnChanges. Signal/event causality is unknown unless you add an explicit mark. Object values show reference IDs; mutation details are not captured.");
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),new JScrollPane(details)); split.setResizeWeight(.7); panel.add(split);
        JPanel footer=new JPanel(new BorderLayout(8,8));
        JTextField filter=new JTextField(); filter.setToolTipText("Filter component, instance, event, or detail");
        JButton navigate=new JButton("Open component source"); navigate.setEnabled(false);
        JTextField connect=new JTextField("Start the receiver, then run the connection command in Firefox's console."); connect.setEditable(false);
        footer.add(connect,BorderLayout.NORTH);
        JPanel search=new JPanel(new BorderLayout(8,0)); search.add(new JLabel("Filter:"),BorderLayout.WEST); search.add(filter); search.add(navigate,BorderLayout.EAST);
        footer.add(search,BorderLayout.SOUTH); panel.add(footer,BorderLayout.SOUTH);
        java.util.List<RenderTrace.Event> shown=new java.util.ArrayList<>();
        Runnable refresh=() -> {
            List<RenderTrace.Event> current=trace.events();
            if (current.equals(shown)) return;
            shown.clear(); shown.addAll(current); model.setRowCount(0);
            for (var event : shown) model.addRow(new Object[]{java.time.Instant.ofEpochMilli(event.time()).toString(),event.component()+" #"+event.instance(),event.kind(),event.detail()});
        };
        javax.swing.Timer timer=new javax.swing.Timer(500,e -> { if (!pause.isSelected()) refresh.run(); }); timer.start();
        table.getSelectionModel().addListSelectionListener(e -> {
            int row=table.getSelectedRow();
            if (row<0) { navigate.setEnabled(false); return; }
            var event=shown.get(table.convertRowIndexToModel(row));
            navigate.setEnabled(RenderTrace.source(root,event.source())!=null);
            details.setText(event.component()+" #"+event.instance()+"\n"+event.source()+"\n"+event.detail()+"\n\n"
                    +event.changes()+"\n\nA check does not prove a DOM update. Sequence and proximity do not prove causality.");
            details.setCaretPosition(0);
        });
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() { sorter.setRowFilter(filter.getText().isBlank() ? null : RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(filter.getText()))); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        navigate.addActionListener(e -> {
            int row=table.getSelectedRow(); if (row<0) return;
            var event=shown.get(table.convertRowIndexToModel(row));
            FileObject source=RenderTrace.source(root,event.source()); if (source==null) return;
            WORKER.post(() -> {
                int offset=0;
                for (var artifact : AngularComponentScanner.scanTypeScriptFile(source)) if (artifact.getClassName().equals(event.component())) { offset=artifact.getClassNameStartOffset(); break; }
                try {
                    String text=source.asText(); int line=0;
                    for (int i=0;i<Math.min(offset,text.length());i++) if (text.charAt(i)=='\n') line++;
                    int target=line;
                    SwingUtilities.invokeLater(() -> {
                        try { LineCookie cookie=DataObject.find(source).getLookup().lookup(LineCookie.class); if (cookie!=null) cookie.getLineSet().getCurrent(target).show(Line.ShowOpenType.OPEN,Line.ShowVisibilityType.FOCUS); }
                        catch (IOException | IndexOutOfBoundsException ex) { message("Component source is no longer available."); }
                    });
                } catch (IOException ex) { SwingUtilities.invokeLater(() -> message(ex.getMessage())); }
            });
        });
        clear.addActionListener(e -> { trace.clear(); refresh.run(); });
        listen.addActionListener(e -> {
            if (receiver[0]!=null) {
                receiver[0].close(); receiver[0]=null; listen.setText("Start receiver"); origin.setEnabled(true); connect.setText("Receiver stopped. In the browser, call window.alpRenderProbe.disconnect()."); return;
            }
            try {
                receiver[0]=new RenderTraceReceiver(origin.getText(),trace); origin.setEnabled(false); listen.setText("Stop receiver");
                connect.setText("window.alpRenderProbe.connect('"+receiver[0].endpoint()+"')"); connect.selectAll(); connect.requestFocusInWindow();
            } catch (IOException ex) { message(ex.getMessage()); }
        });
        load.addActionListener(e -> {
            JFileChooser chooser=new JFileChooser(); if (chooser.showOpenDialog(panel)!=JFileChooser.APPROVE_OPTION) return;
            Path path=chooser.getSelectedFile().toPath();
            WORKER.post(() -> {
                try {
                    if (Files.size(path)>4*1024*1024) throw new IOException("Trace files must be smaller than 4 MiB.");
                    List<RenderTrace.Event> events=RenderTrace.parse(Files.readString(path));
                    trace.add(events); SwingUtilities.invokeLater(refresh);
                } catch (IOException ex) { SwingUtilities.invokeLater(() -> message(ex.getMessage())); }
            });
        });
        setup.addActionListener(e -> install(root,panel));
        DialogDescriptor descriptor=new DialogDescriptor(panel,"Angular · Why was this component checked?",false,new Object[]{"Close"},"Close",DialogDescriptor.DEFAULT_ALIGN,null,null);
        Dialog dialog=DialogDisplayer.getDefault().createDialog(descriptor);
        dialog.addWindowListener(new WindowAdapter() {
            private void release() {
                timer.stop();
                if (receiver[0]!=null) { receiver[0].close(); receiver[0]=null; }
            }
            @Override public void windowClosing(WindowEvent e) { release(); }
            @Override public void windowClosed(WindowEvent e) { release(); }
        });
        dialog.setVisible(true);
    }
    private static void install(FileObject root,Component parent) {
        JFileChooser chooser=new JFileChooser(FileUtil.toFile(root)); chooser.setSelectedFile(new File(FileUtil.toFile(root),"src/app/alplibre-render-probe.ts"));
        if (chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION) return;
        Path target=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        Path workspace=FileUtil.toFile(root).toPath().toAbsolutePath().normalize();
        if (!target.startsWith(workspace) || !target.toString().endsWith(".ts")) { message("Choose a new TypeScript file inside this workspace."); return; }
        WORKER.post(() -> {
            try {
                // CREATE_NEW protects existing source; no application files are rewritten.
                try (InputStream resource=AngularRenderTimeline.class.getResourceAsStream("/org/netbeans/modules/angular/resources/render-probe.ts.template")) {
                    if (resource==null) throw new IOException("Companion resource is missing.");
                    Files.write(target,resource.readAllBytes(),StandardOpenOption.CREATE_NEW);
                }
                FileUtil.refreshFor(target.toFile());
                var components=AngularComponentScanner.scanWorkspace(root).stream().filter(a -> a.getKind()==AngularArtifact.Kind.COMPONENT).toList();
                SwingUtilities.invokeLater(() -> {
                    if (components.isEmpty()) { message("Companion created at "+workspace.relativize(target)+". Import AlpRenderProbe and forward your component's ngOnChanges and ngDoCheck hooks to it."); return; }
                    JComboBox<String> pick=new JComboBox<>(components.stream().map(a -> a.getClassName()+" · "+FileUtil.getRelativePath(root,a.getSourceFile())).toArray(String[]::new));
                    JTextArea snippet=new JTextArea(15,85); snippet.setEditable(false);
                    Runnable update=() -> {
                        var component=components.get(pick.getSelectedIndex());
                        Path source=FileUtil.toFile(component.getSourceFile()).toPath();
                        String relative=source.getParent().relativize(target).toString().replace(File.separatorChar,'/').replaceAll("\\.ts$", "");
                        if (!relative.startsWith(".")) relative="./"+relative;
                        snippet.setText("// Merge these calls into existing hooks; keep your current logic.\n"
                                +"import { SimpleChanges } from '@angular/core';\nimport { AlpRenderProbe } from '"+relative+"';\n\n"
                                +"// Inside "+component.getClassName()+":\nprivate readonly renderProbe = new AlpRenderProbe('"+component.getClassName()+"', '"+FileUtil.getRelativePath(root,component.getSourceFile())+"');\n\n"
                                +"ngOnChanges(changes: SimpleChanges): void {\n  this.renderProbe.changes(changes);\n}\n\nngDoCheck(): void {\n  this.renderProbe.check();\n}\n\n// Optional in an event handler: this.renderProbe.mark('Search changed');");
                        snippet.setCaretPosition(0);
                    };
                    pick.addActionListener(e -> update.run()); update.run();
                    JPanel instructions=new JPanel(new BorderLayout(8,8)); instructions.add(pick,BorderLayout.NORTH); instructions.add(new JScrollPane(snippet));
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor(instructions,"Companion created · component setup",NotifyDescriptor.DEFAULT_OPTION,NotifyDescriptor.PLAIN_MESSAGE,new Object[]{"Close"},"Close"));
                });
            } catch (IOException ex) { SwingUtilities.invokeLater(() -> message("Could not create companion: "+ex.getMessage())); }
        });
    }
    private static void message(String text) { DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(text)); }
}
