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
package org.netbeans.modules.markdown;

import java.awt.BorderLayout;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.LinkNodeBase;
import com.vladsch.flexmark.util.ast.Node;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.text.JTextComponent;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.util.Lookup;

/** Markdown editing tools reuse the standard editor and its save/undo lifecycle. */
public final class MarkdownSourceElement extends MultiViewEditorElement {
    private transient MarkdownViewerElement preview;
    private transient JPanel panel;
    private transient JSplitPane split;
    private transient JToolBar tools;
    private transient boolean previewShowing;
    private transient boolean synchronizing;
    private transient boolean scrollInstalled;

    public MarkdownSourceElement(Lookup lookup) {
        super(lookup);
        preview = new MarkdownViewerElement(lookup);
    }

    private MarkdownViewerElement preview() {
        if (preview == null) preview = new MarkdownViewerElement(getLookup());
        return preview;
    }

    @Override public JComponent getVisualRepresentation() {
        if (panel == null) {
            panel = new JPanel(new BorderLayout());
            split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, super.getVisualRepresentation(), null);
            split.setResizeWeight(0.5);
            panel.add(split);
        }
        return panel;
    }

    @Override public JComponent getToolbarRepresentation() {
        if (tools == null) {
            tools = new JToolBar();
            tools.setFloatable(false);
            addTool(tools, "Heading", "## ", "");
            addTool(tools, "Bold", "**", "**");
            addTool(tools, "Italic", "*", "*");
            JButton insert = new JButton("Insert");
            JPopupMenu insertMenu = new JPopupMenu();
            addTool(insertMenu, "Link", "[", "](https://)");
            addTool(insertMenu, "List", "- ", "");
            addTool(insertMenu, "Inline code", "`", "`");
            addTool(insertMenu, "Code block", "\n```bash\n", "\n```\n");
            addTool(insertMenu, "Table", "\n| Column | Column |\n| --- | --- |\n| ", " | |\n");
            insert.addActionListener(e -> insertMenu.show(insert, 0, insert.getHeight()));
            tools.add(insert);
            JButton outline = new JButton("Outline");
            outline.addActionListener(e -> showOutline(outline));
            tools.add(outline);
            JButton code = new JButton("Code actions");
            JPopupMenu codeMenu = new JPopupMenu();
            JMenuItem copy = new JMenuItem("Copy code block");
            copy.addActionListener(e -> copyCodeBlock());
            codeMenu.add(copy);
            JMenuItem run = new JMenuItem("Run command");
            run.addActionListener(e -> runCommand());
            codeMenu.add(run);
            code.addActionListener(e -> codeMenu.show(code, 0, code.getHeight()));
            tools.add(code);
            JButton check = new JButton("Check document");
            check.addActionListener(e -> checkLinks());
            tools.add(check);
            JToggleButton toggle = new JToggleButton("Split preview");
            toggle.addActionListener(e -> {
                getVisualRepresentation();
                previewShowing = toggle.isSelected();
                if (previewShowing) {
                    split.setRightComponent(preview().getVisualRepresentation());
                    split.setDividerLocation(0.5);
                    preview().componentShowing();
                    installScrollSync();
                } else {
                    preview().componentHidden();
                    split.setRightComponent(null);
                }
            });
            tools.add(toggle);
        }
        return tools;
    }

    private void installScrollSync() {
        if (scrollInstalled) return;
        JScrollPane left = findScroll(split.getLeftComponent());
        JScrollPane right = findScroll(split.getRightComponent());
        if (left == null || right == null) return;
        scrollInstalled = true;
        left.getVerticalScrollBar().addAdjustmentListener(e -> sync(left, right));
        right.getVerticalScrollBar().addAdjustmentListener(e -> sync(right, left));
    }

    private static JScrollPane findScroll(java.awt.Component component) {
        if (component instanceof JScrollPane scroll) return scroll;
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JScrollPane found = findScroll(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void sync(JScrollPane from, JScrollPane to) {
        if (synchronizing || !previewShowing) return;
        synchronizing = true;
        try {
            JScrollBar a = from.getVerticalScrollBar();
            JScrollBar b = to.getVerticalScrollBar();
            int range = a.getMaximum() - a.getVisibleAmount();
            if (range > 0) b.setValue((int) ((long) a.getValue() * (b.getMaximum() - b.getVisibleAmount()) / range));
        } finally { synchronizing = false; }
    }

    private String codeAtCaret() {
        if (getEditorPane() == null) return null;
        int caret = getEditorPane().getCaretPosition();
        for (Node node : preview().parser.parse(getEditorPane().getText()).getDescendants()) {
            if (node instanceof FencedCodeBlock block && caret >= block.getStartOffset() && caret <= block.getEndOffset()) {
                return block.getContentChars().toString();
            }
        }
        return null;
    }

    private void copyCodeBlock() {
        String code = codeAtCaret();
        if (code == null) {
            JOptionPane.showMessageDialog(panel, "Place the caret inside a fenced code block to copy its contents.");
        } else {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(code), null);
        }
    }

    private void runCommand() {
        String code = codeAtCaret();
        // Only a single recognized project command is offered; never interpret document text as shell code.
        if (code == null || !code.strip().matches("(?:ng (?:serve|build|test|generate|version)|npm (?:run|start|test|install|ci))(?: [A-Za-z0-9_./=@:-]+)*")) {
            JOptionPane.showMessageDialog(panel, "Place the caret inside a block containing one npm or Angular CLI command.");
            return;
        }
        org.openide.loaders.DataObject owner = getLookup().lookup(org.openide.loaders.DataObject.class);
        if (owner == null) return;
        org.openide.filesystems.FileObject folder = owner.getPrimaryFile().getParent();
        for (org.openide.filesystems.FileObject current = folder; current != null; current = current.getParent()) {
            if (current.getFileObject("package.json") != null) { folder = current; break; }
        }
        java.io.File directory = org.openide.filesystems.FileUtil.toFile(folder);
        if (directory == null) return;
        String command = code.strip();
        if (JOptionPane.showConfirmDialog(panel, command + "\n\nWorking directory: " + directory,
                "Run project command", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        java.util.List<String> arguments = new java.util.ArrayList<>(java.util.List.of(command.split(" +")));
        if (arguments.get(0).equals("ng")) {
            java.io.File local = new java.io.File(directory, "node_modules/.bin/ng");
            if (local.isFile()) arguments.set(0, local.getAbsolutePath());
        }
        org.netbeans.api.extexecution.ExecutionService.newService(
                () -> new ProcessBuilder(arguments).directory(directory).start(),
                new org.netbeans.api.extexecution.ExecutionDescriptor().controllable(true).frontWindow(true).inputVisible(true),
                command + " — " + directory.getName()).run();
    }

    private void checkLinks() {
        if (getEditorPane() == null) return;
        org.openide.loaders.DataObject owner = getLookup().lookup(org.openide.loaders.DataObject.class);
        if (owner == null) return;
        org.openide.filesystems.FileObject parent = owner.getPrimaryFile().getParent();
        StringBuilder missing = new StringBuilder();
        int previousLevel = 0;
        for (Node node : preview().parser.parse(getEditorPane().getText()).getDescendants()) {
            if (node instanceof Heading heading) {
                int level = heading.getLevel();
                if (level > previousLevel + 1) missing.append("Skipped heading level: ").append(heading.getText())
                        .append(" — use ").append("#".repeat(previousLevel + 1)).append(" to keep a clear hierarchy.\n");
                previousLevel = level;
            }
            if (!(node instanceof LinkNodeBase link)) continue;
            String target = link.getUrl().toString();
            if (target.isEmpty() || target.startsWith("#") || target.startsWith("/") || target.matches("[A-Za-z][A-Za-z0-9+.-]*:.*")) continue;
            try {
                String path = new java.net.URI(target).getPath();
                if (path != null && !path.isEmpty() && parent.getFileObject(path) == null) missing.append("Missing file: ").append(target)
                        .append(" — check the path relative to this Markdown file.\n");
            } catch (java.net.URISyntaxException ex) { missing.append("Invalid link: ").append(target).append('\n'); }
        }
        JTextArea report = new JTextArea(missing.length() == 0 ? "No missing local files or skipped heading levels found." : missing.toString(), 12, 55);
        report.setEditable(false);
        JOptionPane.showMessageDialog(panel, new JScrollPane(report), "Markdown checks", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addTool(JComponent target, String title, String before, String after) {
        AbstractButton button = target instanceof JPopupMenu ? new JMenuItem(title) : new JButton(title);
        button.setFocusable(false);
        button.addActionListener(e -> {
            JTextComponent editor = getEditorPane();
            if (editor == null || !editor.isEditable()) return;
            String selected = editor.getSelectedText();
            int start = editor.getSelectionStart();
            editor.replaceSelection(before + (selected == null ? "" : selected) + after);
            editor.select(start + before.length(), start + before.length() + (selected == null ? 0 : selected.length()));
            editor.requestFocusInWindow();
        });
        target.add(button);
    }

    private void showOutline(JButton button) {
        if (getEditorPane() == null) return;
        JPopupMenu menu = new JPopupMenu();
        for (Node node : preview().parser.parse(getEditorPane().getText()).getDescendants()) {
            if (node instanceof Heading heading) {
                int position = heading.getStartOffset();
                JMenuItem item = new JMenuItem("  ".repeat(heading.getLevel() - 1) + heading.getText());
                item.addActionListener(e -> { getEditorPane().setCaretPosition(position); getEditorPane().requestFocusInWindow(); });
                menu.add(item);
            }
        }
        if (menu.getComponentCount() == 0) { JMenuItem empty = new JMenuItem("No headings"); empty.setEnabled(false); menu.add(empty); }
        menu.show(button, 0, button.getHeight());
    }

    @Override public void componentHidden() { if (preview != null) preview.componentHidden(); super.componentHidden(); }
    @Override public void componentShowing() { super.componentShowing(); if (previewShowing) preview().componentShowing(); }
    @Override public void componentClosed() { if (preview != null) preview.componentHidden(); super.componentClosed(); }
}
