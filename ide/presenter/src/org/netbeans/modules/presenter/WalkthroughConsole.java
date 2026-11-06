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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import javax.swing.*;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;

@Messages({"LBL_WalkthroughTitle=Code Walkthrough", "LBL_WalkthroughEmpty=Add code points to prepare your presentation.",
    "LBL_WalkthroughNotes=Presenter notes (shown in this window)", "LBL_WalkthroughPoint=Point title:",
    "LBL_JumpPoint=Jump to point", "LBL_Resume=Resume walkthrough", "LBL_ResumeButton=Resume", "LBL_AddPoint=Add current code…",
    "LBL_EditSequence=Reorder / remove…", "LBL_SavePoint=Save details", "LBL_PreviousPoint=Previous", "LBL_NextPoint=Next",
    "# {0} - number of points", "LBL_ReadyWalkthrough=Ready · {0} points",
    "MSG_WalkthroughDetails=Use a title of 1–100 characters and notes up to 4000 characters."})
final class WalkthroughConsole {
    private static WalkthroughConsole console;
    private final JDialog window = new JDialog(WindowManager.getDefault().getMainWindow(), Bundle.LBL_WalkthroughTitle(), false);
    private final DefaultListModel<DemoCheckpoint> model = new DefaultListModel<>();
    private final JList<DemoCheckpoint> points = new JList<>(model);
    private final JTextField title = new JTextField();
    private final JTextArea notes = new JTextArea(8, 30);
    private final JLabel target = new JLabel(" ");
    private final JLabel progress = new JLabel();
    private final JButton previous = new JButton(Bundle.LBL_PreviousPoint());
    private final JButton next = new JButton(Bundle.LBL_NextPoint());
    private final JButton resume = new JButton(Bundle.LBL_Resume());
    private final JButton jump = new JButton(Bundle.LBL_JumpPoint());
    private DemoCheckpoint editing;
    private boolean updating;
    private long shownNavigation = -1;
    private final PropertyChangeListener listener = event -> refresh();

    static void showConsole() {
        if (console == null) console = new WalkthroughConsole();
        console.window.setVisible(true); console.window.toFront();
    }
    static void closeBeforeShutdown() {
        if (console != null) { console.saveDetails(false); console.window.dispose(); }
    }
    private WalkthroughConsole() {
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(progress, BorderLayout.NORTH);
        points.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        points.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(list,
                    (index + 1) + " · " + value.name, index, selected, focused);
            label.putClientProperty("html.disable", true);
            label.setToolTipText(value.relativePath);
            label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            return label;
        });
        points.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || updating) return;
            if (!saveDetails(true)) {
                updating = true; points.setSelectedValue(editing, true); updating = false; return;
            }
            editing = points.getSelectedValue(); loadDetails();
        });
        points.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) navigate(() -> DemoCheckpoints.jump(points.getSelectedIndex())); }
        });
        JScrollPane listScroll = new JScrollPane(points); listScroll.setPreferredSize(new Dimension(250, 300));
        JPanel details = new JPanel(new BorderLayout(0, 8));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6));
        JLabel titleLabel = new JLabel(Bundle.LBL_WalkthroughPoint()); titleLabel.setLabelFor(title);
        heading.add(titleLabel); heading.add(title); heading.add(target); details.add(heading, BorderLayout.NORTH);
        notes.setLineWrap(true); notes.setWrapStyleWord(true);
        JScrollPane noteScroll = new JScrollPane(notes);
        noteScroll.setBorder(BorderFactory.createTitledBorder(Bundle.LBL_WalkthroughNotes()));
        notes.getAccessibleContext().setAccessibleName(Bundle.LBL_WalkthroughNotes());
        details.add(noteScroll, BorderLayout.CENTER);
        JButton save = new JButton(Bundle.LBL_SavePoint()); save.addActionListener(e -> { if (saveDetails(true)) DemoCheckpoints.changed(); });
        details.add(save, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, details); split.setResizeWeight(0.4);
        content.add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(0, 3, 6, 6));
        previous.addActionListener(e -> navigate(() -> DemoCheckpoints.jump(DemoCheckpoints.store().previous())));
        next.addActionListener(e -> navigate(() -> DemoCheckpoints.jump(DemoCheckpoints.store().next())));
        resume.addActionListener(e -> navigate(DemoCheckpoints::resume));
        jump.addActionListener(e -> navigate(() -> DemoCheckpoints.jump(points.getSelectedIndex())));
        JButton add = new JButton(Bundle.LBL_AddPoint()); add.addActionListener(e -> navigate(DemoCheckpoints::add));
        JButton sequence = new JButton(Bundle.LBL_EditSequence()); sequence.addActionListener(e -> navigate(DemoCheckpoints::manage));
        JButton check = new JButton(Bundle.CTL_CheckDemo()); check.addActionListener(e -> navigate(DemoCheckpoints::check));
        for (JButton button : new JButton[]{previous, next, resume, jump, add, sequence, check}) actions.add(button);
        content.add(actions, BorderLayout.SOUTH);
        window.setContentPane(content); window.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { if (saveDetails(true)) window.dispose(); }
            @Override public void windowClosed(WindowEvent event) { DemoCheckpoints.removeListener(listener); console = null; }
        });
        DemoCheckpoints.addListener(listener);
        refresh(); loadDetails(); window.pack(); window.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
    }
    private void navigate(Runnable action) { if (saveDetails(true)) action.run(); }
    private boolean saveDetails(boolean showError) {
        if (editing == null || updating) return true;
        String name = title.getText().trim();
        if (name.isEmpty() || name.length() > 100 || notes.getText().length() > 4000) {
            if (showError) JOptionPane.showMessageDialog(window, Bundle.MSG_WalkthroughDetails());
            return false;
        }
        editing.name = name; editing.notes = notes.getText();
        DemoCheckpoints.store().save(); points.repaint(); return true;
    }
    private void loadDetails() {
        title.setEnabled(editing != null); notes.setEnabled(editing != null);
        title.setText(editing == null ? "" : editing.name); notes.setText(editing == null ? "" : editing.notes);
        target.putClientProperty("html.disable", true);
        target.setText(editing == null ? Bundle.LBL_WalkthroughEmpty() : editing.relativePath);
        updateButtons();
    }
    private void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        var data = DemoCheckpoints.store();
        boolean structural = model.size() != data.entries.size();
        if (!structural) for (int i = 0; i < model.size(); i++) if (model.get(i) != data.entries.get(i)) { structural = true; break; }
        if (structural) {
            saveDetails(false);
            updating = true;
            model.clear(); data.entries.forEach(model::addElement);
            int index = data.entries.indexOf(editing);
            if (index < 0) index = data.current >= 0 ? data.current : data.entries.isEmpty() ? -1 : 0;
            points.setSelectedIndex(index); editing = points.getSelectedValue();
            updating = false; loadDetails();
        }
        if (!DemoCheckpoints.isBusy() && DemoCheckpoints.navigationRevision() != shownNavigation && data.current >= 0 && saveDetails(false)) {
            updating = true;
            points.setSelectedIndex(data.current); editing = points.getSelectedValue();
            updating = false; loadDetails(); shownNavigation = DemoCheckpoints.navigationRevision();
        }
        if (data.current < 0) shownNavigation = -1;
        progress.setText(data.current >= 0 && data.current < data.entries.size()
                ? Bundle.MSG_CheckpointStatus(data.current + 1, data.entries.size(), data.entries.get(data.current).name)
                : Bundle.LBL_ReadyWalkthrough(data.entries.size()));
        progress.putClientProperty("html.disable", true); points.repaint(); updateButtons();
    }
    private void updateButtons() {
        var data = DemoCheckpoints.store(); boolean ready = !DemoCheckpoints.isBusy();
        previous.setEnabled(ready && data.previous() >= 0); next.setEnabled(ready && data.next() >= 0);
        resume.setEnabled(ready && !data.entries.isEmpty()); jump.setEnabled(ready && points.getSelectedIndex() >= 0);
    }
}
