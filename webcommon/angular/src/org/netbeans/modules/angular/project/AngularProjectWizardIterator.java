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
package org.netbeans.modules.angular.project;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.io.Reader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.base.ProcessBuilder;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.angular.lsp.AngularOptions;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.Utilities;

/** Creates a workspace through the installed Angular CLI, outside the event thread. */
public final class AngularProjectWizardIterator implements WizardDescriptor.ProgressInstantiatingIterator<WizardDescriptor> {
    private ProjectPanel panel;

    @Override public void initialize(WizardDescriptor wizard) {
        panel = new ProjectPanel(wizard);
        wizard.putProperty("NewProjectWizard_Title", "Angular Application");
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, new String[]{"Name and Location"});
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, 0);
        panel.view.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
    }

    @Override public Set<FileObject> instantiate(ProgressHandle handle) throws IOException {
        handle.start();
        Future<Integer> execution = null;
        File target = panel.target;
        try {
            AngularOptions options = AngularOptions.getInstance();
            String node = options.resolveNodeExecutable(null);
            File cli = options.resolveNgCli(null);
            if (node == null || cli == null) {
                throw new IOException("Install Node.js and Angular CLI, then configure Tools > Options > HTML/JS > Angular.");
            }
            List<String> command = AngularCliExecutor.buildCommand(node, cli,
                    newProjectArguments(panel.projectName, target, panel.styleValue,
                            panel.routingValue, panel.ssrValue, panel.installValue), Utilities.isWindows());
            String error = destinationError(panel.projectName, target.getParentFile());
            if (error != null) throw new IOException(error);
            if (!target.exists()) Files.createDirectory(target.toPath());
            handle.progress("Creating Angular application; see Output for details...");
            execution = runCommand(command, node, target.getParentFile(),
                    "Create Angular Application: " + panel.projectName);
            int result = execution.get();
            if (result != 0) {
                throw new IOException("Angular CLI exited with code " + result
                        + ". See Output for details. Generated files were kept in " + target);
            }
            if (!new File(target, "angular.json").isFile()) {
                throw new IOException("Angular CLI did not create angular.json in " + target);
            }
            if (panel.materialValue) {
                handle.progress("Installing Angular Material and configuring its theme...");
                List<String> materialCommand = AngularCliExecutor.buildCommand(node,
                        new File(target, "node_modules/@angular/cli/bin/ng.js"),
                        materialArguments(target, panel.themeValue), Utilities.isWindows());
                execution = runCommand(materialCommand, node, target, "Add Angular Material: " + panel.projectName);
                int materialResult = execution.get();
                if (materialResult != 0) {
                    throw new IOException("The application was created in " + target
                            + ", but Angular Material setup failed (exit code " + materialResult
                            + "). See Output. Open the existing application to finish setup.");
                }
            }
            FileUtil.refreshFor(target);
            FileObject directory = FileUtil.toFileObject(target);
            if (directory == null) throw new IOException("Cannot open generated project: " + target);
            ProjectManager.getDefault().clearNonProjectCache();
            ProjectChooser.setProjectsFolder(target.getParentFile());
            return Set.of(directory);
        } catch (InterruptedException ex) {
            if (execution != null) execution.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Angular project creation interrupted. Check " + target + " for generated files.", ex);
        } catch (ExecutionException | CancellationException | IllegalArgumentException ex) {
            throw new IOException("Angular project creation failed. See Output; generated files may remain in " + target, ex);
        } finally {
            FileUtil.refreshFor(target);
            handle.finish();
        }
    }

    static List<String> newProjectArguments(String name, File directory, String style,
            boolean routing, boolean ssr, boolean install) {
        if (!validName(name) || !List.of("css", "scss", "sass", "less").contains(style)) {
            throw new IllegalArgumentException("Invalid Angular project name or stylesheet format");
        }
        return List.of("new", name, "--directory=" + directory.getName(),
                "--style=" + style, "--routing=" + routing, "--ssr=" + ssr,
                "--skip-install=" + !install, "--package-manager=npm", "--standalone=true",
                "--strict=true", "--skip-git=true", "--interactive=false");
    }

    private static Future<Integer> runCommand(List<String> command, String node, File directory, String title) {
        ProcessBuilder builder = ProcessBuilder.getLocal();
        builder.setExecutable(command.get(0));
        builder.setArguments(command.subList(1, command.size()));
        builder.setWorkingDirectory(directory.getAbsolutePath());
        String path = builder.getEnvironment().getVariable("PATH");
        builder.getEnvironment().setVariable("PATH", new File(node).getParent()
                + File.pathSeparator + (path == null ? "" : path));
        builder.getEnvironment().setVariable("NG_CLI_ANALYTICS", "false");
        return ExecutionService.newService(builder,
                new ExecutionDescriptor().controllable(true).frontWindow(true).showProgress(true), title).run();
    }

    static String cliVersion(File cli) {
        if (cli == null) return null;
        try {
            for (File dir = cli.toPath().toRealPath().toFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
                File manifest = new File(dir, "package.json");
                if (manifest.isFile()) {
                    JsonObject json = readJson(manifest);
                    if (json.has("name") && "@angular/cli".equals(json.get("name").getAsString()) && json.has("version")) {
                        return json.get("version").getAsString();
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            // A custom executable may not have a package manifest; do not invent a version.
        }
        return null;
    }

    private static JsonObject readJson(File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath())) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static List<String> materialArguments(File workspace, String theme) throws IOException {
        JsonObject json = readJson(new File(workspace, "package.json"));
        String version = json.getAsJsonObject("dependencies").get("@angular/core").getAsString();
        Matcher major = Pattern.compile("^[~^]?(\\d+)(?:\\.|$)").matcher(version);
        if (!major.find()) throw new IOException("Cannot determine Angular version for Material: " + version);
        List<String> args = new ArrayList<>(List.of("add", "@angular/material@^" + major.group(1),
                "--skip-confirmation", "--interactive=false"));
        if (!"CLI default".equals(theme)) {
            if (!List.of("azure-blue", "rose-red", "magenta-violet", "cyan-orange").contains(theme)) {
                throw new IOException("Unknown Angular Material theme: " + theme);
            }
            if (Integer.parseInt(major.group(1)) < 18) {
                throw new IOException("This Material theme needs Angular 18 or newer. Select CLI default for older versions.");
            }
            args.add("--theme=" + theme);
        }
        return args;
    }

    static boolean validName(String name) {
        return name != null && name.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
                && !List.of("node_modules", "favicon", "test").contains(name);
    }

    static String destinationError(String name, File parent) {
        if (!validName(name)) return "Use a lowercase name such as my-angular-app (letters, digits and hyphens).";
        if (!parent.isDirectory() || !parent.canWrite()) return "Choose an existing writable project location.";
        File target = new File(parent, name);
        if (Files.isSymbolicLink(target.toPath())) return "Choose a project folder that is not a symbolic link.";
        if (target.exists()) {
            String[] children = target.list();
            if (!target.isDirectory() || !target.canWrite() || children == null || children.length > 0) {
                return "The project folder is not empty or writable. Choose another name or location.";
            }
        }
        return null;
    }

    @Override public Set<FileObject> instantiate() { throw new UnsupportedOperationException(); }
    @Override public void uninitialize(WizardDescriptor wizard) { panel = null; }
    @Override public WizardDescriptor.Panel<WizardDescriptor> current() { return panel; }
    @Override public String name() { return "Name and Location"; }
    @Override public boolean hasNext() { return false; }
    @Override public boolean hasPrevious() { return false; }
    @Override public void nextPanel() { throw new NoSuchElementException(); }
    @Override public void previousPanel() { throw new NoSuchElementException(); }
    @Override public void addChangeListener(ChangeListener listener) { }
    @Override public void removeChangeListener(ChangeListener listener) { }

    private static final class ProjectPanel implements WizardDescriptor.Panel<WizardDescriptor> {
        private final WizardDescriptor wizard;
        private final ChangeSupport changes = new ChangeSupport(this);
        private final JPanel view = new JPanel(new GridBagLayout());
        private final JTextField name = new JTextField("my-angular-app", 28);
        private final JTextField location = new JTextField(ProjectChooser.getProjectsFolder().getAbsolutePath(), 28);
        private final JTextField destination = new JTextField(28);
        private final JComboBox<String> style = new JComboBox<>(new String[]{"css", "scss", "sass", "less"});
        private final JCheckBox routing = new JCheckBox("Add routing", true);
        private final JCheckBox ssr = new JCheckBox("Enable server-side rendering (SSR)", false);
        private final JCheckBox material = new JCheckBox("Add Angular Material", false);
        private final JComboBox<String> theme = new JComboBox<>(new String[]{"CLI default"});
        private final JLabel version = new JLabel("Detecting Angular CLI...");
        private final JButton refreshVersion = new JButton("Refresh");
        private final JCheckBox install = new JCheckBox("Install npm dependencies (requires internet)", true);
        private final AngularLearningPanel learning = new AngularLearningPanel();
        private String detectedVersion;
        private File target;
        private String projectName;
        private String styleValue;
        private boolean routingValue;
        private boolean ssrValue;
        private boolean installValue;
        private boolean materialValue;
        private String themeValue;

        ProjectPanel(WizardDescriptor wizard) {
            this.wizard = wizard;
            view.setName("Name and Location");
            view.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            addRow(0, "Project name:", name);
            addRow(1, "Project location:", location);
            JButton browse = new JButton("Browse...");
            GridBagConstraints b = constraints(2, 1);
            view.add(browse, b);
            browse.addActionListener(event -> {
                JFileChooser chooser = new JFileChooser(location.getText());
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
                    location.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            });
            destination.setEditable(false);
            addRow(2, "Project folder:", destination);
            addRow(3, "Stylesheet format:", style);
            addRow(4, "", routing);
            addRow(5, "", ssr);
            addRow(6, "", install);
            addRow(7, "", material);
            addRow(8, "Material theme:", theme);
            theme.setEnabled(false);
            material.addActionListener(event -> {
                theme.setEnabled(material.isSelected());
                if (material.isSelected()) install.setSelected(true);
                install.setEnabled(!material.isSelected());
                changes.fireChange();
            });
            theme.setToolTipText("CLI default applies the default theme of the matching Material version.");
            addRow(9, "Angular CLI version:", version);
            view.add(refreshVersion, constraints(2, 9));
            refreshVersion.addActionListener(event -> refreshVersion());
            addRow(10, "", new JLabel("Standalone app with strict checks. Material requires npm dependencies."));
            addRow(11, "", new JLabel("Setup: npm install -g @angular/cli (HTML/JS > Angular options)"));
            addRow(12, "", learning);
            for (JComboBox<String> combo : List.of(style, theme)) combo.addActionListener(event -> updateLearning());
            for (JCheckBox box : List.of(routing, ssr, install, material)) box.addActionListener(event -> updateLearning());
            updateLearning();
            DocumentListener listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { changed(); }
                @Override public void removeUpdate(DocumentEvent event) { changed(); }
                @Override public void changedUpdate(DocumentEvent event) { changed(); }
            };
            name.getDocument().addDocumentListener(listener);
            location.getDocument().addDocumentListener(listener);
            changed();
        }

        private String state(JCheckBox box) {
            return AngularLearningPanel.message(box.isSelected() ? "Learning.enabled" : "Learning.disabled");
        }

        private void updateLearning() {
            learning.explain(
                    AngularLearningPanel.message("Learning.version", detectedVersion == null
                            ? AngularLearningPanel.message("Learning.unknown") : detectedVersion),
                    AngularLearningPanel.message("Learning.project"),
                    AngularLearningPanel.message("Learning.projectStyles", style.getSelectedItem()),
                    AngularLearningPanel.message("Learning.routing", state(routing)),
                    AngularLearningPanel.message("Learning.ssr", state(ssr)),
                    AngularLearningPanel.message("Learning.material", state(material), theme.getSelectedItem()),
                    AngularLearningPanel.message("Learning.install", state(install)));
        }

        private static GridBagConstraints constraints(int x, int y) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = x;
            c.gridy = y;
            c.insets = new Insets(5, 5, 5, 5);
            c.anchor = GridBagConstraints.WEST;
            return c;
        }

        private void addRow(int row, String text, Component component) {
            JLabel label = new JLabel(text);
            label.setLabelFor(component);
            view.add(label, constraints(0, row));
            GridBagConstraints c = constraints(1, row);
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            view.add(component, c);
        }

        private void changed() {
            destination.setText(new File(location.getText().trim(), name.getText().trim()).getAbsolutePath());
            changes.fireChange();
        }

        @Override public Component getComponent() { return view; }
        @Override public HelpCtx getHelp() { return HelpCtx.DEFAULT_HELP; }
        @Override public boolean isValid() {
            String error = destinationError(name.getText().trim(), new File(location.getText().trim()));
            wizard.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, error);
            return error == null;
        }
        private void refreshVersion() {
            refreshVersion.setEnabled(false);
            version.setText("Detecting...");
            new SwingWorker<String, Void>() {
                private File cli;
                @Override protected String doInBackground() {
                    cli = AngularOptions.getInstance().resolveNgCli(null);
                    String detected = cliVersion(cli);
                    return cli == null ? "Not found — configure Angular options"
                            : detected == null ? "Unknown (no CLI package metadata)" : detected;
                }
                @Override protected void done() {
                    try {
                        version.setText(get());
                        detectedVersion = cliVersion(cli);
                        int major = AngularLearningPanel.major(detectedVersion);
                        Object selectedTheme = theme.getSelectedItem();
                        theme.removeAllItems();
                        theme.addItem("CLI default");
                        if (major >= 18) {
                            for (String item : List.of("azure-blue", "rose-red", "magenta-violet", "cyan-orange")) theme.addItem(item);
                            theme.setSelectedItem(selectedTheme);
                        }
                        updateLearning();
                        version.setToolTipText(cli == null ? null : cli.getAbsolutePath());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        version.setText("Detection interrupted");
                    } catch (ExecutionException ex) {
                        version.setText("Could not detect version");
                    } finally {
                        refreshVersion.setEnabled(true);
                    }
                }
            }.execute();
        }

        @Override public void readSettings(WizardDescriptor settings) { refreshVersion(); }
        @Override public void storeSettings(WizardDescriptor settings) {
            projectName = name.getText().trim();
            target = FileUtil.normalizeFile(new File(location.getText().trim(), projectName));
            styleValue = (String) style.getSelectedItem();
            routingValue = routing.isSelected();
            ssrValue = ssr.isSelected();
            installValue = install.isSelected();
            materialValue = material.isSelected();
            themeValue = (String) theme.getSelectedItem();
        }
        @Override public void addChangeListener(ChangeListener listener) { changes.addChangeListener(listener); }
        @Override public void removeChangeListener(ChangeListener listener) { changes.removeChangeListener(listener); }
    }
}
