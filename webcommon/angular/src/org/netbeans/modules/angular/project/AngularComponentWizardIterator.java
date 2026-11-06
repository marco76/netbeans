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
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.netbeans.modules.angular.model.AngularWorkspace.AngularSubProject;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;

/** Uses workspace schematics for coherent, version-compatible component files. */
public final class AngularComponentWizardIterator implements WizardDescriptor.ProgressInstantiatingIterator<WizardDescriptor> {
    private final AngularProject suppliedProject;
    private AngularProject project;
    private OptionsPanel panel;
    private List<String> arguments;
    private File outputFolder;

    public AngularComponentWizardIterator() { this(null); }
    AngularComponentWizardIterator(AngularProject project) { this.suppliedProject = project; }

    static void open(AngularProject project) {
        WizardDescriptor wizard = new WizardDescriptor(new AngularComponentWizardIterator(project));
        wizard.setTitle("Generate Angular Component");
        wizard.setTitleFormat(new java.text.MessageFormat("{0}"));
        if (DialogDisplayer.getDefault().notify(wizard) == WizardDescriptor.FINISH_OPTION) {
            for (Object result : wizard.getInstantiatedObjects()) {
                if (result instanceof FileObject file) {
                    try {
                        var open = org.openide.loaders.DataObject.find(file).getLookup().lookup(org.openide.cookies.OpenCookie.class);
                        if (open != null) open.open();
                    } catch (org.openide.loaders.DataObjectNotFoundException ex) {
                        org.openide.util.Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    @Override public void initialize(WizardDescriptor wizard) {
        project = suppliedProject;
        if (project == null) {
            Project owner = Templates.getProject(wizard);
            if (owner != null) project = owner.getLookup().lookup(AngularProject.class);
        }
        panel = new OptionsPanel(wizard);
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, new String[]{"Component Options"});
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, 0);
        panel.view.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
        panel.view.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
    }

    @Override public Set<FileObject> instantiate(ProgressHandle handle) throws IOException {
        handle.start();
        Future<Integer> execution = null;
        try {
            Set<Path> before = sourceFiles(outputFolder.toPath());
            handle.progress("Generating component, template, styles and tests...");
            execution = AngularCliExecutor.executeNgCommand(project, project.getProjectDirectory(), arguments.toArray(String[]::new));
            if (execution == null) throw new IOException("Cannot start Angular CLI. Check Angular settings.");
            if (execution.get() != 0) throw new IOException("Component generation failed. See Angular Output for details.");
            FileUtil.refreshFor(outputFolder);
            Set<Path> created = sourceFiles(outputFolder.toPath());
            created.removeAll(before);
            Set<FileObject> generated = new java.util.LinkedHashSet<>();
            Set<FileObject> result = new java.util.LinkedHashSet<>();
            for (Path path : created.stream().sorted().toList()) {
                FileObject file = FileUtil.toFileObject(path.toFile());
                if (file == null) continue;
                generated.add(file);
                if (path.toString().endsWith(".ts") && !path.toString().endsWith(".spec.ts")) result.add(file);
            }
            try {
                List<AngularGeneratedFiles.Entry> tour = AngularGeneratedFiles.describe(project.getProjectDirectory(), generated);
                SwingUtilities.invokeLater(() -> AngularGeneratedFilesPanel.offer(tour));
            } catch (IOException ex) {
                // Educational content must not turn successful generation into a failure.
                org.openide.util.Exceptions.printStackTrace(ex);
            }
            return result;
        } catch (InterruptedException ex) {
            if (execution != null) execution.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Component generation interrupted.", ex);
        } catch (ExecutionException | CancellationException ex) {
            throw new IOException("Component generation stopped. See Angular Output for details.", ex);
        } finally {
            handle.finish();
        }
    }

    static Set<Path> sourceFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return new HashSet<>();
        Set<Path> files = new HashSet<>();
        Files.walkFileTree(directory, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attributes) {
                return List.of("node_modules", ".git", ".angular", "dist").contains(dir.getFileName().toString())
                        ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && AngularGeneratedFiles.supported(file.toString())) files.add(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    static String validationError(String name, String path) {
        if (name == null || !name.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*(?:/[a-z][a-z0-9]*(?:-[a-z0-9]+)*)*")) {
            return "Use a component name such as user-card or features/user-card.";
        }
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                || path.contains(":") || List.of(path.split("/", -1)).contains("..")) {
            return "Choose a folder relative to the workspace, without parent-directory segments.";
        }
        return null;
    }

    static List<String> componentArguments(String name, String path, String projectName,
            String template, String styles, String style, String tests, String standalone,
            String changeDetection, String flat, boolean preview) {
        String error = validationError(name, path);
        if (error != null) throw new IllegalArgumentException(error);
        if (projectName == null || projectName.isBlank()) throw new IllegalArgumentException("Choose an Angular project.");
        List<String> args = new ArrayList<>(List.of("generate", "component", name,
                "--project=" + projectName, "--path=" + path, "--interactive=false"));
        addChoice(args, "inline-template", template, "Separate file", "false", "Inline", "true");
        addChoice(args, "inline-style", styles, "Separate file", "false", "Inline", "true");
        addChoice(args, "skip-tests", tests, "Generate", "false", "Skip", "true");
        addChoice(args, "standalone", standalone, "Standalone", "true", "NgModule", "false");
        addChoice(args, "flat", flat, "Create subfolder", "false", "Same folder", "true");
        if (!"Workspace default".equals(style)) {
            if (!List.of("css", "scss", "sass", "less", "none").contains(style)) throw new IllegalArgumentException("Invalid stylesheet format");
            args.add("--style=" + style);
        }
        if (!"Workspace default".equals(changeDetection)) {
            if (!"OnPush".equals(changeDetection)) throw new IllegalArgumentException("Invalid change detection strategy");
            args.add("--change-detection=OnPush");
        }
        if (preview) args.add("--dry-run");
        return args;
    }

    private static void addChoice(List<String> args, String flag, String choice,
            String first, String firstValue, String second, String secondValue) {
        if ("Workspace default".equals(choice)) return;
        if (!first.equals(choice) && !second.equals(choice)) throw new IllegalArgumentException("Invalid " + flag);
        args.add("--" + flag + "=" + (first.equals(choice) ? firstValue : secondValue));
    }

    @Override public Set<FileObject> instantiate() { throw new UnsupportedOperationException(); }
    @Override public void uninitialize(WizardDescriptor wizard) { panel = null; }
    @Override public WizardDescriptor.Panel<WizardDescriptor> current() { return panel; }
    @Override public String name() { return "Component Options"; }
    @Override public boolean hasNext() { return false; }
    @Override public boolean hasPrevious() { return false; }
    @Override public void nextPanel() { throw new NoSuchElementException(); }
    @Override public void previousPanel() { throw new NoSuchElementException(); }
    @Override public void addChangeListener(ChangeListener listener) { }
    @Override public void removeChangeListener(ChangeListener listener) { }

    private final class OptionsPanel implements WizardDescriptor.Panel<WizardDescriptor> {
        private final JPanel view = new JPanel(new GridBagLayout());
        private final WizardDescriptor wizard;
        private final ChangeSupport changes = new ChangeSupport(this);
        private final JTextField name = new JTextField("user-card", 25);
        private final JTextField folder = new JTextField(25);
        private final JComboBox<String> projects = new JComboBox<>();
        private final JComboBox<String> template = choice("Separate file", "Inline");
        private final JComboBox<String> styles = choice("Separate file", "Inline");
        private final JComboBox<String> style = choice("css", "scss", "sass", "less", "none");
        private final JComboBox<String> tests = choice("Generate", "Skip");
        private final JComboBox<String> standalone = choice("Standalone", "NgModule");
        private final JComboBox<String> detection = choice("OnPush");
        private final JComboBox<String> flat = choice("Create subfolder", "Same folder");
        private final JTextArea command = new JTextArea(4, 40);
        private final AngularLearningPanel learning = new AngularLearningPanel();
        private int cliMajor = -1;
        private String cliVersion;
        private final JButton preview = new JButton("Preview Files (dry run)");

        OptionsPanel(WizardDescriptor wizard) {
            this.wizard = wizard;
            view.setName("Component Options");
            view.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            if (project != null) {
                project.getWorkspace().getProjects().keySet().forEach(projects::addItem);
                AngularSubProject primary = project.getWorkspace().getPrimaryProject();
                if (primary != null) projects.setSelectedItem(primary.getName());
            }
            row(0, "Angular project:", projects);
            row(1, "Component name:", name);
            row(2, "Folder (workspace relative):", folder);
            row(3, "HTML template:", template);
            row(4, "Styles:", styles);
            row(5, "Stylesheet format:", style);
            row(6, "Unit tests:", tests);
            row(7, "Component type:", standalone);
            row(8, "Change detection:", detection);
            row(9, "File layout:", flat);
            command.setEditable(false);
            command.setLineWrap(true);
            command.setWrapStyleWord(true);
            row(10, "Command:", new JScrollPane(command));
            row(11, "", preview);
            row(12, "", new JLabel("Workspace defaults come from angular.json and the installed CLI."));
            row(13, "", learning);
            detectVersion();
            defaultFolder();
            FileObject selected = Templates.getTargetFolder(wizard);
            if (project != null && selected != null && selected.isFolder()) {
                String relative = FileUtil.getRelativePath(project.getProjectDirectory(), selected);
                if (relative != null && !relative.isEmpty()) {
                    project.getWorkspace().getProjects().values().stream()
                            .filter(sub -> sub.getSourceRoot() != null && (relative.equals(sub.getSourceRoot()) || relative.startsWith(sub.getSourceRoot() + "/")))
                            .max(java.util.Comparator.comparingInt(sub -> sub.getSourceRoot().length()))
                            .ifPresent(sub -> projects.setSelectedItem(sub.getName()));
                    folder.setText(relative);
                }
            }
            DocumentListener listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { changed(); }
                @Override public void removeUpdate(DocumentEvent event) { changed(); }
                @Override public void changedUpdate(DocumentEvent event) { changed(); }
            };
            name.getDocument().addDocumentListener(listener);
            folder.getDocument().addDocumentListener(listener);
            for (JComboBox<String> combo : List.of(template, styles, style, tests, standalone, detection, flat)) {
                combo.addActionListener(event -> changed());
            }
            projects.addActionListener(event -> { defaultFolder(); changed(); });
            preview.addActionListener(event -> {
                if (isValid()) AngularCliExecutor.executeNgCommand(project, project.getProjectDirectory(), options(true).toArray(String[]::new));
            });
            changed();
        }

        private void detectVersion() {
            if (project == null) return;
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    return AngularProjectWizardIterator.cliVersion(
                            org.netbeans.modules.angular.lsp.AngularOptions.getInstance().resolveNgCli(project.getProjectDirectory()));
                }
                @Override protected void done() {
                    try {
                        cliVersion = get();
                        cliMajor = AngularLearningPanel.major(cliVersion);
                        if (cliMajor > 0 && cliMajor < 14) {
                            standalone.setSelectedItem("Workspace default");
                            standalone.setEnabled(false);
                        }
                        changed();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        // Keep workspace defaults when package metadata cannot be read.
                    }
                }
            }.execute();
        }

        private void updateLearning() {
            List<String> content = new ArrayList<>();
            content.add(AngularLearningPanel.message("Learning.version", cliVersion == null
                    ? AngularLearningPanel.message("Learning.unknown") : cliVersion));
            content.add(AngularLearningPanel.message("Learning.default"));
            content.add(AngularLearningPanel.message("Learning.component"));
            content.add(AngularLearningPanel.message("Learning.classRole"));
            content.add(AngularLearningPanel.message("Learning.template", value(template)));
            content.add(AngularLearningPanel.message("Learning.styles", value(styles), value(style)));
            content.add(AngularLearningPanel.message("Learning.tests", value(tests)));
            content.add(cliMajor > 0 && cliMajor < 14 ? AngularLearningPanel.message("Learning.legacy")
                    : AngularLearningPanel.message("Learning.standalone", value(standalone)));
            content.add(AngularLearningPanel.message("Learning.detection", value(detection)));
            if (cliMajor >= 16) content.add(AngularLearningPanel.message("Learning.signals"));
            content.add(AngularLearningPanel.message("Learning.layout", value(flat)));
            learning.explain(content.toArray(String[]::new));
        }

        private void defaultFolder() {
            if (project == null || projects.getSelectedItem() == null) return;
            AngularSubProject sub = project.getWorkspace().getProjects().get((String) projects.getSelectedItem());
            String root = sub.getSourceRoot();
            if (root == null || root.isBlank()) root = sub.getRoot() == null || sub.getRoot().isBlank() ? "src" : sub.getRoot() + "/src";
            folder.setText(root + ("library".equals(sub.getProjectType()) ? "/lib" : "/app"));
        }

        private List<String> options(boolean dryRun) {
            return componentArguments(name.getText().trim(), folder.getText().trim(), (String) projects.getSelectedItem(),
                    value(template), value(styles), value(style), value(tests), value(standalone), value(detection), value(flat), dryRun);
        }
        private String value(JComboBox<String> combo) { return (String) combo.getSelectedItem(); }
        private void changed() {
            updateLearning();
            boolean valid = isValid();
            preview.setEnabled(valid);
            command.setText(valid ? "ng " + String.join(" ", options(false).stream()
                    .map(arg -> arg.contains(" ") ? "\"" + arg + "\"" : arg).toList()) : "");
            changes.fireChange();
        }
        private void row(int y, String text, Component field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = y;
            c.insets = new Insets(3, 5, 3, 5);
            c.anchor = GridBagConstraints.WEST;
            JLabel label = new JLabel(text);
            label.setLabelFor(field);
            view.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            view.add(field, c);
        }
        @Override public Component getComponent() { return view; }
        @Override public HelpCtx getHelp() { return HelpCtx.DEFAULT_HELP; }
        @Override public boolean isValid() {
            String error = project == null || projects.getSelectedItem() == null ? "Open an Angular workspace to generate a component."
                    : validationError(name.getText().trim(), folder.getText().trim());
            if (error == null) {
                File root = FileUtil.toFile(project.getProjectDirectory());
                File destination = FileUtil.normalizeFile(new File(root, folder.getText().trim()));
                try {
                    Path existing = destination.toPath();
                    while (existing != null && !Files.exists(existing)) existing = existing.getParent();
                    if (existing == null || !existing.toRealPath().startsWith(root.toPath().toRealPath())) {
                        error = "The destination must stay inside the Angular workspace.";
                    }
                } catch (IOException ex) { error = "Cannot read the destination folder."; }
            }
            wizard.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, error);
            return error == null;
        }
        @Override public void readSettings(WizardDescriptor settings) { }
        @Override public void storeSettings(WizardDescriptor settings) {
            arguments = options(false);
            outputFolder = FileUtil.normalizeFile(new File(FileUtil.toFile(project.getProjectDirectory()), folder.getText().trim()));
        }
        @Override public void addChangeListener(ChangeListener listener) { changes.addChangeListener(listener); }
        @Override public void removeChangeListener(ChangeListener listener) { changes.removeChangeListener(listener); }
    }

    private static JComboBox<String> choice(String... values) {
        List<String> items = new ArrayList<>(List.of("Workspace default"));
        items.addAll(List.of(values));
        return new JComboBox<>(items.toArray(String[]::new));
    }
}
