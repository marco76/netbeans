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
import java.io.IOException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import org.netbeans.modules.angular.debug.*;
import org.netbeans.modules.angular.model.*;
import org.openide.*;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.RequestProcessor;

/** Browser selection and reverse highlighting using development-mode Angular globals. */
public final class AngularComponentPicker {
    private static final RequestProcessor WORKER=new RequestProcessor(AngularComponentPicker.class);
    private AngularComponentPicker() {}
    public static void show(FileObject root) {
        RenderTrace trace=new RenderTrace(); BrowserCommands commands=new BrowserCommands();
        RenderTraceReceiver[] receiver={null};
        JPanel panel=new JPanel(new BorderLayout(8,8)); panel.setPreferredSize(new Dimension(950,550));
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JTextField origin=new JTextField("http://localhost:4200",23);
        JButton connect=new JButton("Start connection"), pick=new JButton("Pick in browser"), highlight=new JButton("Highlight in browser"), refresh=new JButton("Refresh components");
        pick.setEnabled(false); highlight.setEnabled(false);
        toolbar.add(new JLabel("Application origin:")); toolbar.add(origin); toolbar.add(connect); toolbar.add(pick); toolbar.add(highlight); toolbar.add(refresh);
        panel.add(toolbar,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Component","Selector","Source"},0) {
            @Override public boolean isCellEditable(int row,int col) { return false; }
        };
        JTable table=new JTable(model); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        var sorter=new TableRowSorter<TableModel>(model); table.setRowSorter(sorter);
        panel.add(new JScrollPane(table));
        List<AngularArtifact> artifacts=new ArrayList<>();
        JTextArea command=new JTextArea("Start a connection, then paste the connection script once into Firefox's web console. No application source changes are required.",3,80);
        command.setEditable(false); command.setLineWrap(true); command.setWrapStyleWord(true);
        JLabel status=new JLabel("Development builds only. Browser selection opens matching workspace source; Escape cancels picking.");
        JButton script=new JButton("Show full connection script…"), source=new JButton("Open source"), template=new JButton("Open template");
        script.setEnabled(false); source.setEnabled(false); template.setEnabled(false);
        JPanel footer=new JPanel(new BorderLayout(6,6)); footer.add(status,BorderLayout.NORTH); footer.add(new JScrollPane(command));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEADING));
        buttons.add(script); buttons.add(source); buttons.add(template); footer.add(buttons,BorderLayout.SOUTH); panel.add(footer,BorderLayout.SOUTH);
        Runnable selection=() -> {
            int row=table.getSelectedRow(); AngularArtifact selected=row<0 ? null : artifacts.get(table.convertRowIndexToModel(row));
            source.setEnabled(selected!=null); template.setEnabled(selected!=null && selected.getTemplateFile()!=null);
            highlight.setEnabled(selected!=null && receiver[0]!=null);
        };
        table.getSelectionModel().addListSelectionListener(e -> selection.run());
        Runnable load=() -> {
            refresh.setEnabled(false);
            WORKER.post(() -> {
                List<AngularArtifact> scanned=AngularComponentScanner.scanWorkspace(root).stream().filter(a -> a.getKind()==AngularArtifact.Kind.COMPONENT).toList();
                SwingUtilities.invokeLater(() -> {
                    artifacts.clear(); artifacts.addAll(scanned); model.setRowCount(0);
                    for (var artifact : artifacts) model.addRow(new Object[]{artifact.getClassName(),artifact.getSelector(),org.openide.filesystems.FileUtil.getRelativePath(root,artifact.getSourceFile())});
                    refresh.setEnabled(true); selection.run();
                });
            });
        };
        refresh.addActionListener(e -> load.run()); load.run();
        source.addActionListener(e -> { int row=table.getSelectedRow(); if (row>=0) open(artifacts.get(table.convertRowIndexToModel(row)),false); });
        template.addActionListener(e -> { int row=table.getSelectedRow(); if (row>=0) open(artifacts.get(table.convertRowIndexToModel(row)),true); });
        highlight.addActionListener(e -> {
            int row=table.getSelectedRow(); if (row<0) return;
            var artifact=artifacts.get(table.convertRowIndexToModel(row)); commands.highlight(artifact.getClassName(),artifact.getSelector());
            status.setText("Requested highlight of the first mounted "+artifact.getClassName()+" instance. Check Firefox for the outline or a console message.");
        });
        pick.addActionListener(e -> { commands.pick(); status.setText("Click an Angular component in Firefox. Escape cancels."); });
        script.addActionListener(e -> {
            if (receiver[0]==null) return;
            try {
                JTextArea full=new JTextArea(RenderTraceReceiver.pickerScript(receiver[0].endpoint()),24,95); full.setEditable(false); full.setCaretPosition(0);
                DialogDisplayer.getDefault().notify(new NotifyDescriptor(new JScrollPane(full),"Paste this script into Firefox's web console",NotifyDescriptor.DEFAULT_OPTION,NotifyDescriptor.PLAIN_MESSAGE,new Object[]{"Close"},"Close"));
            } catch (IOException ex) { message(ex.getMessage()); }
        });
        connect.addActionListener(e -> {
            if (receiver[0]!=null) {
                receiver[0].close(); receiver[0]=null; connect.setText("Start connection"); origin.setEnabled(true); pick.setEnabled(false); script.setEnabled(false); selection.run();
                command.setText("Connection stopped. Run window.alpComponentPicker.dispose() in Firefox to remove the picker and its polling timer."); return;
            }
            try {
                receiver[0]=new RenderTraceReceiver(origin.getText(),trace,commands); origin.setEnabled(false); connect.setText("Stop connection"); pick.setEnabled(true); script.setEnabled(true); selection.run();
                String endpoint=receiver[0].endpoint();
                command.setText("fetch('"+endpoint+"/picker.js').then(r => { if (!r.ok) throw new Error('Connection rejected'); return r.text(); }).then(code => (0, eval)(code))");
                status.setText("Paste the connection command once into Firefox's console. If CSP blocks it, paste the full connection script instead.");
                command.selectAll(); command.requestFocusInWindow();
            } catch (IOException ex) { message(ex.getMessage()); }
        });
        Set<RenderTrace.Event> handled=new HashSet<>();
        javax.swing.Timer timer=new javax.swing.Timer(350,e -> {
            for (var event : trace.events()) {
                if (!event.kind().equals("pick") || !handled.add(event)) continue;
                List<AngularArtifact> matches=matches(artifacts,event.component(),event.changes());
                if (matches.size()!=1) {
                    status.setText(matches.isEmpty() ? "No workspace match for "+event.component()+". It may belong to a library or a minified build." : "Several components match "+event.component()+"; select the intended source in the table.");
                    continue;
                }
                AngularArtifact artifact=matches.get(0); int index=artifacts.indexOf(artifact); int row=table.convertRowIndexToView(index);
                if (row>=0) { table.setRowSelectionInterval(row,row); table.scrollRectToVisible(table.getCellRect(row,0,true)); }
                status.setText("Selected "+event.component()+" instance #"+event.instance()+" · "+event.detail());
                open(artifact,false);
            }
            if (handled.size()>RenderTrace.LIMIT*2) { handled.clear(); handled.addAll(trace.events()); }
        }); timer.start();
        DialogDescriptor descriptor=new DialogDescriptor(panel,"Angular Component Picker",false,new Object[]{"Close"},"Close",DialogDescriptor.DEFAULT_ALIGN,null,null);
        Dialog dialog=DialogDisplayer.getDefault().createDialog(descriptor);
        dialog.addWindowListener(new WindowAdapter() {
            private void close() { timer.stop(); if (receiver[0]!=null) { receiver[0].close(); receiver[0]=null; } }
            @Override public void windowClosing(WindowEvent e) { close(); }
            @Override public void windowClosed(WindowEvent e) { close(); }
        }); dialog.setVisible(true);
    }
    static List<AngularArtifact> matches(List<AngularArtifact> artifacts,String component,String metadata) {
        List<AngularArtifact> named=artifacts.stream().filter(a -> a.getClassName().equals(component)).toList();
        if (named.size()<=1) return named;
        try {
            String selector=com.google.gson.JsonParser.parseString(metadata).getAsJsonObject().get("selector").getAsString();
            return named.stream().filter(a -> selector.equals(a.getSelector())).toList();
        } catch (RuntimeException ex) { return named; }
    }
    private static void open(AngularArtifact artifact,boolean template) {
        WORKER.post(() -> {
            FileObject file=template ? artifact.getTemplateFile() : artifact.getSourceFile(); if (file==null) return;
            try {
                int offset=0;
                if (!template) for (var fresh : AngularComponentScanner.scanTypeScriptFile(file)) if (fresh.getClassName().equals(artifact.getClassName())) { offset=fresh.getClassNameStartOffset(); break; }
                String text=file.asText(); int line=0; for (int i=0;i<Math.min(offset,text.length());i++) if (text.charAt(i)=='\n') line++;
                int target=line;
                SwingUtilities.invokeLater(() -> {
                    try { LineCookie cookie=DataObject.find(file).getLookup().lookup(LineCookie.class); if (cookie!=null) cookie.getLineSet().getCurrent(target).show(Line.ShowOpenType.OPEN,Line.ShowVisibilityType.FOCUS); }
                    catch (IOException | IndexOutOfBoundsException ex) { message("Component source is no longer available; refresh components."); }
                });
            } catch (IOException ex) { SwingUtilities.invokeLater(() -> message(ex.getMessage())); }
        });
    }
    private static void message(String text) { DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(text)); }
}
