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
package org.netbeans.modules.fullstack;

import org.netbeans.modules.fullstack.spi.EndpointProvider;
import org.openide.util.Lookup;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.text.Line;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/** Small, index-backed view of frontend calls and backend routes. */
@TopComponent.Description(preferredID = "FullStackConnections", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "output", openAtStartup = false)
@ActionID(category = "Window", id = "org.netbeans.modules.micronaut.symbol.FullStackConnectionsTopComponent")
@ActionReference(path = "Menu/Window", position = 1850)
@TopComponent.OpenActionRegistration(displayName = "#CTL_FullStackConnections", preferredID = "FullStackConnections")
@Messages("CTL_FullStackConnections=Full Stack Connections")
public final class FullStackConnectionsTopComponent extends TopComponent {
    private static final RequestProcessor WORKER = new RequestProcessor(FullStackConnectionsTopComponent.class);
    // Intentionally limited to conventional HttpClient receivers and quoted URLs.
    private static final Pattern CALL = Pattern.compile(
            "(?:this\\.)?(?:http|httpClient)\\.(get|post|put|delete|patch|head|options)\\s*(?:<[^;\\n]*?>)?\\s*\\(\\s*(['\"`])([^'\"`\\r\\n]+)\\2");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Angular call", "HTTP route", "Backend source", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField filter = new JTextField(24);
    private final JCheckBox unresolvedOnly = new JCheckBox("Unresolved only");
    private final JLabel visibleCount = new JLabel();
    private final JButton refresh = new JButton("Refresh");
    private final JLabel status = new JLabel("Scans saved TypeScript and indexed backend endpoints in open projects.");
    private final List<Runnable[]> navigation = new ArrayList<>();

    public FullStackConnectionsTopComponent() {
        setName(Bundle.CTL_FullStackConnections());
        setLayout(new BorderLayout(8, 8));
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.add(refresh, BorderLayout.WEST);
        toolbar.add(status, BorderLayout.CENTER);
        JPanel filters = new JPanel(new BorderLayout(8, 0));
        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setLabelFor(filter);
        filters.add(filterLabel, BorderLayout.WEST);
        filters.add(filter, BorderLayout.CENTER);
        JPanel options = new JPanel();
        options.add(unresolvedOnly);
        options.add(visibleCount);
        filters.add(options, BorderLayout.EAST);
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(toolbar, BorderLayout.NORTH);
        header.add(filters, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        table.setRowSorter(sorter);
        filter.setToolTipText("Search URLs, file names, HTTP methods, or connection status.");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
        unresolvedOnly.addActionListener(event -> updateFilter());
        updateFilter();
        table.setFillsViewportHeight(true);
        table.setToolTipText("Double-click the Angular or backend column to open its source.");
        refresh.addActionListener(event -> scan());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (event.getClickCount() == 2 && row >= 0 && column >= 0) {
                    int sourceColumn = table.convertColumnIndexToModel(column);
                    Runnable[] actions = navigation.get(table.convertRowIndexToModel(row));
                    Runnable action = actions[sourceColumn == 2 || actions[0] == null ? 1 : 0];
                    if (action != null) { action.run(); }
                }
            }
        });
    }

    @Override protected void componentOpened() { scan(); }

    private void updateFilter() {
        final String query = filter.getText().trim().toLowerCase(Locale.ROOT);
        final boolean unresolved = unresolvedOnly.isSelected();
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (unresolved && !"Unresolved".equals(entry.getStringValue(3))) { return false; }
                for (int column = 0; column < entry.getValueCount(); column++) {
                    if (entry.getStringValue(column).toLowerCase(Locale.ROOT).contains(query)) { return true; }
                }
                return false;
            }
        });
        visibleCount.setText(table.getRowCount() + " / " + model.getRowCount());
    }

    private void scan() {
        refresh.setEnabled(false);
        status.setText("Finding connections...");
        Project[] projects = OpenProjects.getDefault().getOpenProjects();
        WORKER.post(() -> {
            List<Object[]> rows = new ArrayList<>();
            List<Runnable[]> targets = new ArrayList<>();
            try {
                List<EndpointProvider.Endpoint> endpoints = new ArrayList<>();
                java.util.Collection<? extends EndpointProvider> providers = Lookup.getDefault().lookupAll(EndpointProvider.class);
                for (Project project : projects) {
                    for (EndpointProvider provider : providers) {
                        endpoints.addAll(provider.findEndpoints(project));
                    }
                }
                List<ClientCall> calls = new ArrayList<>();
                for (Project project : projects) { collect(project.getProjectDirectory(), calls); }
                java.util.Set<EndpointProvider.Endpoint> matched = new java.util.HashSet<>();
                for (ClientCall call : calls) {
                    int count = 0;
                    for (EndpointProvider.Endpoint endpoint : endpoints) {
                        if (matches(call.method, call.path, endpoint.methods(), endpoint.path())) {
                            count++;
                            matched.add(endpoint);
                        }
                    }
                    if (count == 0) {
                        rows.add(new Object[]{call.file.getNameExt() + ":" + (call.line + 1), call.method + " " + call.path, "", "Unresolved"});
                        targets.add(new Runnable[]{call::open, null});
                    } else {
                        for (EndpointProvider.Endpoint endpoint : endpoints) {
                            if (matches(call.method, call.path, endpoint.methods(), endpoint.path())) {
                                rows.add(new Object[]{call.file.getNameExt() + ":" + (call.line + 1), call.method + " " + call.path,
                                    endpoint.file().getNameExt(), count == 1 ? "Candidate" : "Ambiguous"});
                                targets.add(new Runnable[]{call::open, endpoint.navigate()});
                            }
                        }
                    }
                }
                for (EndpointProvider.Endpoint endpoint : endpoints) {
                    if (!matched.contains(endpoint)) {
                        rows.add(backendRow(endpoint));
                        targets.add(new Runnable[]{null, endpoint.navigate()});
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    navigation.clear();
                    navigation.addAll(targets);
                    for (Object[] row : rows) { model.addRow(row); }
                    updateFilter();
                    status.setText(calls.size() + " calls, " + endpoints.size() + " indexed routes. "
                            + (providers.isEmpty() ? "Backend integration is unavailable; enable the Spring/Micronaut module and restart."
                            : endpoints.isEmpty() ? "No indexed routes: open the backend project, wait for indexing, then Refresh."
                            : "Backend-only routes have no detected Angular call. Matches are candidates."));
                });
            } catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> status.setText("Scan failed: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> refresh.setEnabled(true));
            }
        });
    }

    static Object[] backendRow(EndpointProvider.Endpoint endpoint) {
        return new Object[]{"", endpoint.methods() + " " + endpoint.path(),
            endpoint.file().getNameExt(), "No detected Angular call"};
    }

    static boolean matches(String method, String path, String methods, String route) {
        if (!(methods.equals("ANY") || java.util.Arrays.asList(methods.split(",\\s*")).contains(method))) { return false; }
        if (!path.startsWith("/")) { return false; }
        String[] actual = path.split("\\?", 2)[0].split("/", -1);
        String[] expected = route.split("/", -1);
        if (actual.length != expected.length) { return false; }
        for (int i = 0; i < actual.length; i++) {
            boolean variable = expected[i].matches("\\{[^{}:]+\\}");
            if (actual[i].contains("${")) {
                // Only whole-segment property expressions are safe to infer statically.
                if (!variable || !actual[i].matches("\\$\\{[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*\\}")) { return false; }
            } else if (!actual[i].equals(expected[i]) && !(variable && !actual[i].isEmpty())) {
                return false;
            }
        }
        return true;
    }

    private static void collect(FileObject folder, List<ClientCall> calls) throws IOException {
        for (FileObject file : folder.getChildren()) {
            if (file.isFolder()) {
                String name = file.getNameExt();
                if (!name.startsWith(".") && !java.util.Arrays.asList("node_modules", "target", "build", "dist", "coverage").contains(name)) {
                    collect(file, calls);
                }
            } else if (file.hasExt("ts") && file.getSize() < 1024 * 1024) {
                String source = file.asText();
                Matcher matcher = CALL.matcher(source);
                while (matcher.find()) {
                    int line = 0;
                    for (int i = 0; i < matcher.start(); i++) { if (source.charAt(i) == '\n') { line++; } }
                    calls.add(new ClientCall(file, line, matcher.group(1).toUpperCase(Locale.ROOT), matcher.group(3)));
                }
            }
        }
    }

    private static final class ClientCall {
        final FileObject file;
        final int line;
        final String method;
        final String path;
        ClientCall(FileObject file, int line, String method, String path) {
            this.file = file; this.line = line; this.method = method; this.path = path;
        }
        void open() {
            LineCookie cookie = file.getLookup().lookup(LineCookie.class);
            if (cookie != null) {
                cookie.getLineSet().getOriginal(line).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
            }
        }
    }
}
