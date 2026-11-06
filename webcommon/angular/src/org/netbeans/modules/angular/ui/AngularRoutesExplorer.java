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

import java.awt.BorderLayout;
import java.awt.event.*;
import java.net.URI;
import javax.swing.*;
import javax.swing.tree.*;
import org.netbeans.modules.angular.model.AngularRouteScanner;
import org.netbeans.modules.angular.model.AngularRouteScanner.Route;
import org.openide.*;
import org.openide.awt.HtmlBrowser;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.RequestProcessor;

/** Project-scoped, asynchronously refreshed route map. */
public final class AngularRoutesExplorer {
    private static final RequestProcessor WORKER = new RequestProcessor(AngularRoutesExplorer.class);
    private record Entry(Route route, String url) {
        @Override public String toString() {
            return "/" + route.path() + (route.detail().isEmpty() ? "" : "   —   " + route.detail());
        }
    }
    private AngularRoutesExplorer() {}
    public static void show(FileObject root) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new java.awt.Dimension(820, 480));
        JTree tree = new JTree(new DefaultMutableTreeNode("Loading routes…"));
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);
        JPanel controls = new JPanel();
        JTextField base = new JTextField("http://localhost:4200", 23);
        controls.add(new JLabel("Local server:")); controls.add(base);
        JButton definition = new JButton("Open definition");
        JButton component = new JButton("Open component");
        JButton browser = new JButton("Open URL");
        JButton refresh = new JButton("Refresh");
        controls.add(definition); controls.add(component); controls.add(browser); controls.add(refresh);
        JPanel footer = new JPanel(new BorderLayout());
        JPanel server = new JPanel();
        controls.remove(base);
        controls.remove(0);
        server.add(new JLabel("Local server:")); server.add(base);
        footer.add(server, BorderLayout.NORTH);
        footer.add(controls, BorderLayout.SOUTH);
        panel.add(footer, BorderLayout.SOUTH);
        JLabel status = new JLabel("Static routes, nested layouts, guards and lazy loading. Double-click to navigate.");
        panel.add(status, BorderLayout.NORTH);
        Runnable selection = () -> {
            Entry entry = selected(tree);
            definition.setEnabled(entry != null);
            component.setEnabled(entry != null && entry.route.component() != null);
            browser.setEnabled(entry != null && !entry.url.contains("**") && !entry.url.contains("[custom matcher]"));
            if (entry != null) status.setText(entry.route.file().getNameExt() + ":" + (entry.route.line()+1) + "   /" + entry.url);
        };
        tree.addTreeSelectionListener(e -> selection.run());
        definition.addActionListener(e -> { Entry entry = selected(tree); if (entry != null) open(entry.route.file(), entry.route.line()); });
        component.addActionListener(e -> { Entry entry = selected(tree); if (entry != null && entry.route.component()!=null) open(entry.route.component(), -1); });
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount()==2 && selected(tree)!=null) definition.doClick(); }
        });
        browser.addActionListener(e -> {
            Entry entry = selected(tree);
            if (entry == null) return;
            String path = entry.url;
            java.util.regex.Matcher parameters = java.util.regex.Pattern.compile(":([A-Za-z0-9_]+)").matcher(path);
            StringBuffer filled = new StringBuffer();
            while (parameters.find()) {
                NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine("Value for " + parameters.group(1), "Route parameter");
                if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) return;
                if (input.getInputText().isBlank()) return;
                parameters.appendReplacement(filled, java.util.regex.Matcher.quoteReplacement(java.net.URLEncoder.encode(input.getInputText(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")));
            }
            parameters.appendTail(filled);
            try {
                URI origin = URI.create(base.getText().trim());
                if (!java.util.Set.of("http", "https").contains(origin.getScheme()) || origin.getHost()==null || origin.getRawQuery()!=null || origin.getRawFragment()!=null) throw new IllegalArgumentException();
                HtmlBrowser.URLDisplayer.getDefault().showURL(URI.create(origin.toString().replaceAll("/+$", "") + "/" + filled).toURL());
            } catch (Exception ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("Enter a valid HTTP or HTTPS server URL and route values.", NotifyDescriptor.WARNING_MESSAGE));
            }
        });
        Runnable load = () -> {
            refresh.setEnabled(false);
            WORKER.post(() -> {
                DefaultMutableTreeNode routes = new DefaultMutableTreeNode("Angular routes");
                String error = null;
                try { for (Route route : AngularRouteScanner.scan(root)) append(routes, route, ""); }
                catch (java.io.IOException ex) { error = "Could not read routes: " + ex.getMessage(); }
                String message = error;
                SwingUtilities.invokeLater(() -> {
                    if (routes.getChildCount()==0) routes.add(new DefaultMutableTreeNode(message == null ? "No static route definitions found" : message));
                    tree.setModel(new DefaultTreeModel(routes));
                    for (int row=0; row<tree.getRowCount(); row++) tree.expandRow(row);
                    refresh.setEnabled(true); selection.run();
                });
            });
        };
        refresh.addActionListener(e -> load.run());
        selection.run(); load.run();
        DialogDescriptor dialog = new DialogDescriptor(panel, "Angular Routes", false, new Object[]{"Close"}, "Close", DialogDescriptor.DEFAULT_ALIGN, null, null);
        DialogDisplayer.getDefault().createDialog(dialog).setVisible(true);
    }
    private static Entry selected(JTree tree) {
        Object node = tree.getLastSelectedPathComponent();
        return node instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof Entry entry ? entry : null;
    }
    private static void append(DefaultMutableTreeNode parent, Route route, String prefix) {
        String url = prefix + (prefix.isEmpty() || route.path().isEmpty() ? "" : "/") + route.path();
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Entry(route, url));
        parent.add(node);
        for (Route child : route.children()) append(node, child, url);
    }
    private static void open(FileObject file, int line) {
        try {
            DataObject data = DataObject.find(file);
            LineCookie lines = data.getLookup().lookup(LineCookie.class);
            if (line >= 0 && lines != null) lines.getLineSet().getCurrent(line).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
            else { OpenCookie open = data.getLookup().lookup(OpenCookie.class); if (open!=null) open.open(); }
        } catch (java.io.IOException | IndexOutOfBoundsException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("Source is no longer available. Refresh the routes explorer."));
        }
    }
}
