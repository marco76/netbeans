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
package org.netbeans.modules.presenter;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.awt.*;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(category = "View", id = "org.netbeans.modules.presenter.DemoCheckpoints")
@ActionRegistration(displayName = "#CTL_DemoCheckpoints", lazy = false)
@Messages({"CTL_DemoCheckpoints=Code Points", "CTL_AddCheckpoint=Add Code Point…",
    "CTL_NextCheckpoint=Next Code Point", "CTL_PreviousCheckpoint=Previous Code Point",
    "CTL_CheckDemo=Validate Code Points", "CTL_ManageCheckpoints=Manage Code Points…",
    "CTL_Walkthrough=Code Walkthrough…", "CTL_ResumeWalkthrough=Resume Walkthrough",
    "LBL_SaveSelection=Spotlight this selection when visiting",
    "LBL_CheckpointName=Checkpoint name:", "LBL_NoCheckpoints=No checkpoints saved",
    "LBL_MoveUp=Move Up", "LBL_MoveDown=Move Down", "LBL_Rename=Rename", "LBL_Remove=Remove",
    "MSG_SelectFile=Focus a saved source file before adding a checkpoint.",
    "MSG_CheckpointLimit=You can save up to 100 checkpoints. Remove one before adding another.",
    "MSG_InvalidName=Use a checkpoint name between 1 and 100 characters.",
    "MSG_NoEditor=The file is missing or cannot be opened in an editor.",
    "MSG_OpeningFailed=Could not activate the checkpoint editor. Try the checkpoint again.",
    "# {0} - checkpoint name", "MSG_CheckpointFailure=Could not open checkpoint: {0}",
    "# {0} - current checkpoint number", "# {1} - total checkpoints", "# {2} - checkpoint name",
    "MSG_CheckpointStatus={0} of {1} · {2}",
    "# {0} - checkpoint count", "MSG_CheckPassed=All {0} checkpoint files are available.",
    "# {0} - checkpoint name", "# {1} - relative file path", "MSG_MissingCheckpoint={0} — {1}",
    "LBL_CheckFailures=Missing or non-editable checkpoint files:", "LBL_DemoReady=Demo check"})
@SuppressWarnings("serial")
public final class DemoCheckpoints extends AbstractAction implements Presenter.Menu {
    private static final RequestProcessor RP = new RequestProcessor(DemoCheckpoints.class);
    private static DemoCheckpointStore store;
    private static boolean busy;
    static boolean isBusy() { return busy; }
    private static final java.beans.PropertyChangeSupport CHANGES = new java.beans.PropertyChangeSupport(DemoCheckpoints.class);
    static void addListener(java.beans.PropertyChangeListener listener) { CHANGES.addPropertyChangeListener(listener); }
    static void removeListener(java.beans.PropertyChangeListener listener) { CHANGES.removePropertyChangeListener(listener); }
    static void changed() { CHANGES.firePropertyChange("walkthrough", null, store()); }
    static void resume() { jump(store().resume()); }
    private static long generation;
    private static long navigationRevision;
    static long navigationRevision() { return navigationRevision; }
    private static Timer pendingNavigation;

    public DemoCheckpoints() { super(Bundle.CTL_DemoCheckpoints()); }

    static DemoCheckpointStore store() {
        if (store == null) store = new DemoCheckpointStore(NbPreferences.forModule(DemoCheckpoints.class).node("demoCheckpoints"));
        return store;
    }

    static void saveBeforeShutdown() {
        WalkthroughConsole.closeBeforeShutdown();
        generation++;
        if (pendingNavigation != null) {
            pendingNavigation.stop();
            pendingNavigation = null;
        }
        busy = false;
        if (store != null) store.save();
    }

    @Override public void actionPerformed(ActionEvent event) { }
    @Override public JMenuItem getMenuPresenter() {
        return new CheckpointMenu(DemoCheckpoints::populate);
    }

    /** Native menu bars need real children before opening the submenu. */
    static final class CheckpointMenu extends JMenu implements DynamicMenuContent {
        private final java.util.function.Consumer<JMenu> fill;
        CheckpointMenu(java.util.function.Consumer<JMenu> fill) {
            super(Bundle.CTL_DemoCheckpoints());
            this.fill = fill;
            fill.accept(this);
            addMenuListener(new MenuListener() {
                @Override public void menuSelected(MenuEvent event) { refresh(); }
                @Override public void menuDeselected(MenuEvent event) { }
                @Override public void menuCanceled(MenuEvent event) { }
            });
        }
        private void refresh() { removeAll(); fill.accept(this); }
        @Override public JComponent[] getMenuPresenters() { refresh(); return new JComponent[]{this}; }
        @Override public JComponent[] synchMenuPresenters(JComponent[] items) { refresh(); return items; }
    }

    private static void item(JMenu menu, String name, boolean enabled, Runnable run) {
        JMenuItem item = new JMenuItem(name);
        item.setEnabled(enabled);
        item.addActionListener(event -> run.run());
        menu.add(item);
    }

    private static void shortcutItem(JMenu menu, String id, boolean enabled) {
        JMenuItem item = new JMenuItem();
        Actions.connect(item, Actions.forID("View", "org.netbeans.modules.presenter." + id), false);
        item.setEnabled(enabled);
        menu.add(item);
    }

    static void populate(JMenu menu) {
        menu.removeAll();
        DemoCheckpointStore data = store();
        item(menu, Bundle.CTL_Walkthrough(), true, WalkthroughConsole::showConsole);
        shortcutItem(menu, "AddCheckpoint", !busy);
        menu.addSeparator();
        shortcutItem(menu, "PreviousCheckpoint", !busy && data.previous() >= 0);
        shortcutItem(menu, "NextCheckpoint", !busy && data.next() >= 0);
        item(menu, Bundle.CTL_CheckDemo(), !busy && !data.entries.isEmpty(), DemoCheckpoints::check);
        item(menu, Bundle.CTL_ManageCheckpoints(), !busy && !data.entries.isEmpty(), DemoCheckpoints::manage);
        menu.addSeparator();
        if (data.entries.isEmpty()) item(menu, Bundle.LBL_NoCheckpoints(), false, () -> { });
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < data.entries.size(); i++) {
            final int index = i;
            JRadioButtonMenuItem stop = new JRadioButtonMenuItem((i + 1) + ". " + data.entries.get(i).name, i == data.current);
            stop.putClientProperty("html.disable", true);
            stop.setEnabled(!busy);
            stop.addActionListener(event -> jump(index));
            group.add(stop);
            menu.add(stop);
        }
    }

    private static void message(String text) {
        JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), text,
                Bundle.CTL_DemoCheckpoints(), JOptionPane.INFORMATION_MESSAGE);
    }

    static void add() {
        if (busy) return;
        DemoCheckpointStore data = store();
        if (data.entries.size() >= DemoCheckpointStore.LIMIT) { message(Bundle.MSG_CheckpointLimit()); return; }
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        Document doc = editor == null ? null : editor.getDocument();
        FileObject file = PresenterEditorContext.file(editor);
        if (file == null || !file.isData()) { message(Bundle.MSG_SelectFile()); return; }
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        int caret = editor.getCaretPosition();
        JTextField name = new JTextField(file.getNameExt(), 32);
        JLabel label = new JLabel(Bundle.LBL_CheckpointName());
        label.setLabelFor(name);
        JCheckBox selection = new JCheckBox(Bundle.LBL_SaveSelection(), end > start);
        selection.setEnabled(end > start);
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        form.add(label); form.add(name); form.add(selection);
        if (JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), form,
                Bundle.CTL_AddCheckpoint(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String title = name.getText().trim();
        if (title.isEmpty() || title.length() > 100) { message(Bundle.MSG_InvalidName()); return; }
        Project project = FileOwnerQuery.getOwner(file);
        FileObject root = project == null ? file.getParent() : project.getProjectDirectory();
        String relative = root == null ? null : FileUtil.getRelativePath(root, file);
        if (relative == null) { root = file.getParent(); relative = FileUtil.getRelativePath(root, file); }
        DemoCheckpoint entry = new DemoCheckpoint(title, root.toURL().toExternalForm(), relative,
                selection.isSelected() ? start : caret, selection.isSelected() ? end : caret);
        try {
            entry.track(doc);
        } catch (javax.swing.text.BadLocationException ex) { message(Bundle.MSG_OpeningFailed()); return; }
        data.entries.add(entry);
        data.save(); changed();
    }

    static void jump(int index) {
        DemoCheckpointStore data = store();
        if (busy || index < 0 || index >= data.entries.size()) return;
        DemoCheckpoint entry = data.entries.get(index);
        long session = generation;
        busy = true; changed();
        RP.post(() -> {
            try {
                FileObject file = entry.resolve();
                if (file == null || !file.isData()) throw new java.io.IOException(Bundle.MSG_NoEditor());
                EditorCookie cookie = DataObject.find(file).getLookup().lookup(EditorCookie.class);
                if (cookie == null) throw new java.io.IOException(Bundle.MSG_NoEditor());
                Document doc = cookie.openDocument();
                SwingUtilities.invokeLater(() -> {
                    if (session == generation) open(index, entry, cookie, doc);
                });
            } catch (java.io.IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> {
                    if (session != generation) return;
                    busy = false; changed();
                    message(Bundle.MSG_CheckpointFailure(entry.name) + "\n" + ex.getMessage());
                });
            }
        });
    }

    private static void open(int index, DemoCheckpoint entry, EditorCookie cookie, Document doc) {
        try {
            entry.track(doc);
            int line = doc.getDefaultRootElement().getElementIndex(entry.start());
            int column = entry.start() - doc.getDefaultRootElement().getElement(line).getStartOffset();
            cookie.getLineSet().getCurrent(line).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS, column);
            int[] attempts = {0};
            Timer timer = new Timer(50, null);
            pendingNavigation = timer;
            timer.addActionListener(event -> {
                try {
                    JEditorPane[] panes = cookie.getOpenedPanes();
                    JEditorPane pane = CheckpointNavigation.choose(panes, doc, EditorRegistry.lastFocusedComponent());
                    if (pane != null) {
                        timer.stop(); pendingNavigation = null;
                        if (pane.getDocument() != doc) throw new IllegalStateException("Document replaced");
                        SpotlightSelectionAction.clearCurrent();
                        pane.requestFocusInWindow();
                        CheckpointNavigation.position(pane, entry);
                        if (entry.end() > entry.start()) SpotlightSelectionAction.showRange(pane, entry.start(), entry.end());
                        store().current = index;
                        navigationRevision++;
                        store().save();
                        busy = false; changed();
                        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_CheckpointStatus(index + 1, store().entries.size(), entry.name));
                    } else if (++attempts[0] >= 60) {
                        timer.stop(); pendingNavigation = null; busy = false; changed(); message(Bundle.MSG_OpeningFailed());
                    }
                } catch (javax.swing.text.BadLocationException | RuntimeException ex) {
                    timer.stop(); pendingNavigation = null; busy = false; changed(); message(Bundle.MSG_OpeningFailed());
                }
            });
            timer.start();
        } catch (javax.swing.text.BadLocationException | RuntimeException ex) {
            busy = false; changed(); message(Bundle.MSG_OpeningFailed());
        }
    }

    static void check() {
        if (busy || store().entries.isEmpty()) return;
        List<DemoCheckpoint> entries = new ArrayList<>(store().entries);
        long session = generation;
        busy = true; changed();
        RP.post(() -> {
            List<String> failures = new ArrayList<>();
            for (DemoCheckpoint entry : entries) {
                try {
                    FileObject file = entry.resolve();
                    if (file == null || !file.isData() || DataObject.find(file).getLookup().lookup(EditorCookie.class) == null) {
                        failures.add(Bundle.MSG_MissingCheckpoint(entry.name, entry.relativePath));
                    }
                } catch (java.io.IOException | RuntimeException ex) {
                    failures.add(Bundle.MSG_MissingCheckpoint(entry.name, entry.relativePath));
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (session != generation) return;
                busy = false; changed();
                JTextArea results = new JTextArea(failures.isEmpty() ? Bundle.MSG_CheckPassed(entries.size())
                        : Bundle.LBL_CheckFailures() + "\n\n" + String.join("\n", failures), 10, 55);
                results.setEditable(false);
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), new JScrollPane(results),
                        Bundle.LBL_DemoReady(), failures.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            });
        });
    }

    static void manage() {
        if (busy) return;
        DefaultListModel<DemoCheckpoint> draft = new DefaultListModel<>();
        store().entries.forEach(entry -> draft.addElement(entry.copy()));
        JList<DemoCheckpoint> list = new JList<>(draft);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(10);
        list.setCellRenderer((view, value, index, selected, focus) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(view, value.name, index, selected, focus);
            label.putClientProperty("html.disable", true);
            return label;
        });
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        for (String label : new String[]{Bundle.LBL_MoveUp(), Bundle.LBL_MoveDown(), Bundle.LBL_Rename(), Bundle.LBL_Remove()}) {
            JButton button = new JButton(label);
            button.addActionListener(event -> {
                int index = list.getSelectedIndex();
                if (index < 0) return;
                if (label.equals(Bundle.LBL_Remove())) { draft.remove(index); return; }
                if (label.equals(Bundle.LBL_Rename())) {
                    String name = JOptionPane.showInputDialog(panel, Bundle.LBL_CheckpointName(), draft.get(index).name);
                    if (name == null) return;
                    if (name.trim().isEmpty() || name.trim().length() > 100) { message(Bundle.MSG_InvalidName()); return; }
                    draft.get(index).name = name.trim(); list.repaint(); return;
                }
                int target = index + (label.equals(Bundle.LBL_MoveUp()) ? -1 : 1);
                if (target >= 0 && target < draft.size()) {
                    DemoCheckpoint entry = draft.remove(index); draft.add(target, entry); list.setSelectedIndex(target);
                }
            });
            buttons.add(button);
        }
        panel.add(buttons, BorderLayout.EAST);
        if (JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), panel, Bundle.CTL_ManageCheckpoints(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            store().entries.clear();
            for (int i = 0; i < draft.size(); i++) store().entries.add(draft.get(i));
            store().current = -1;
            store().save(); changed();
        }
    }

    @ActionID(category = "View", id = "org.netbeans.modules.presenter.ShowWalkthrough")
    @ActionRegistration(displayName = "#CTL_Walkthrough")
    @ActionReference(path = "Menu/View/Presentation", position = 1475)
    public static final class ShowWalkthrough implements ActionListener {
        @Override public void actionPerformed(ActionEvent event) { WalkthroughConsole.showConsole(); }
    }
    @ActionID(category = "View", id = "org.netbeans.modules.presenter.ResumeWalkthrough")
    @ActionRegistration(displayName = "#CTL_ResumeWalkthrough")
    public static final class Resume implements ActionListener {
        @Override public void actionPerformed(ActionEvent event) { resume(); }
    }
    @ActionID(category = "View", id = "org.netbeans.modules.presenter.AddCheckpoint")
    @ActionRegistration(displayName = "#CTL_AddCheckpoint")
    public static final class Add implements ActionListener {
        @Override public void actionPerformed(ActionEvent event) { add(); }
    }
    @ActionID(category = "View", id = "org.netbeans.modules.presenter.NextCheckpoint")
    @ActionRegistration(displayName = "#CTL_NextCheckpoint")
    @ActionReference(path = "Shortcuts", name = "DS-PAGE_DOWN")
    public static final class Next implements ActionListener {
        @Override public void actionPerformed(ActionEvent event) { jump(store().next()); }
    }
    @ActionID(category = "View", id = "org.netbeans.modules.presenter.PreviousCheckpoint")
    @ActionRegistration(displayName = "#CTL_PreviousCheckpoint")
    @ActionReference(path = "Shortcuts", name = "DS-PAGE_UP")
    public static final class Previous implements ActionListener {
        @Override public void actionPerformed(ActionEvent event) { jump(store().previous()); }
    }
}
