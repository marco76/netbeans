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
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.*;
import org.openide.awt.Actions;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;

@Messages({"LBL_ExplainCode=Explain code", "LBL_RunDemo=Run a demo", "LBL_DebugDemo=Debug",
    "LBL_Commands=Show commands", "LBL_Checkpoints=Checkpoints", "LBL_ExitPresentation=Exit",
    "LBL_PresentationSettings=Settings", "TIP_Smaller=Smaller editor text", "TIP_Larger=Larger editor text"})
final class PresentationControls {
    private final PresenterModeAction owner;
    private final java.util.function.Supplier<RootPaneContainer> roots;
    private JLayeredPane pane;
    private JPanel panel;
    private Container content;
    private JPanel header;
    private Component originalNorth;
    private JLabel size;
    private JComboBox<String> layout;
    private JToggleButton commands;
    private JLabel progress;
    private JButton previousPoint, nextPoint, resumePoint;
    private final java.beans.PropertyChangeListener walkthroughChanged = event -> updateWalkthrough();
    private void updateWalkthrough() {
        if (panel == null || progress == null) return;
        var data = DemoCheckpoints.store();
        progress.setText(data.current >= 0 && data.current < data.entries.size()
                ? Bundle.MSG_CheckpointStatus(data.current + 1, data.entries.size(), data.entries.get(data.current).name)
                : Bundle.LBL_ReadyWalkthrough(data.entries.size()));
        progress.setToolTipText(progress.getText());
        previousPoint.setEnabled(!DemoCheckpoints.isBusy() && data.previous() >= 0);
        nextPoint.setEnabled(!DemoCheckpoints.isBusy() && data.next() >= 0);
        resumePoint.setEnabled(!DemoCheckpoints.isBusy() && !data.entries.isEmpty());
    }
    private boolean updating;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { position(); }
    };
    PresentationControls(PresenterModeAction owner) {
        this(owner, () -> WindowManager.getDefault().getMainWindow() instanceof RootPaneContainer root ? root : null);
    }
    PresentationControls(PresenterModeAction owner, java.util.function.Supplier<RootPaneContainer> roots) {
        this.owner = owner; this.roots = roots;
    }
    static String[] layouts() { return new String[]{Bundle.LBL_ExplainCode(), Bundle.LBL_RunDemo(), Bundle.LBL_DebugDemo()}; }
    void show(PresenterPresets.Settings settings) {
        if (panel == null) {
            RootPaneContainer root = roots.get();
            if (root == null) return;
            pane = root.getLayeredPane();
            panel = new JPanel(new BorderLayout(8, 0));
            panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            JToolBar tools = new JToolBar(); tools.setFloatable(false);
            JButton checkpoints = new JButton(Bundle.LBL_WalkthroughTitle());
            checkpoints.addActionListener(e -> WalkthroughConsole.showConsole()); tools.add(checkpoints);
            previousPoint = new JButton("◀"); previousPoint.setToolTipText(Bundle.LBL_PreviousPoint());
            previousPoint.addActionListener(e -> DemoCheckpoints.jump(DemoCheckpoints.store().previous())); tools.add(previousPoint);
            nextPoint = new JButton("▶"); nextPoint.setToolTipText(Bundle.LBL_NextPoint());
            nextPoint.addActionListener(e -> DemoCheckpoints.jump(DemoCheckpoints.store().next())); tools.add(nextPoint);
            resumePoint = new JButton(Bundle.LBL_ResumeButton());
            resumePoint.setToolTipText(Bundle.LBL_Resume());
            resumePoint.addActionListener(e -> DemoCheckpoints.resume()); tools.add(resumePoint);
            progress = new JLabel(); progress.putClientProperty("html.disable", true);
            progress.setPreferredSize(new Dimension(200, 24)); tools.add(progress);
            DemoCheckpoints.addListener(walkthroughChanged);
            updateWalkthrough();
            tools.addSeparator();
            JButton smaller = new JButton("A−"); smaller.setToolTipText(Bundle.TIP_Smaller());
            smaller.addActionListener(e -> owner.resize(-2)); tools.add(smaller);
            size = new JLabel(); tools.add(size);
            JButton larger = new JButton("A+"); larger.setToolTipText(Bundle.TIP_Larger());
            larger.addActionListener(e -> owner.resize(2)); tools.add(larger);
            tools.addSeparator();
            layout = new JComboBox<>(layouts());
            layout.addActionListener(e -> { if (!updating) {
                var s = owner.currentSettings();
                owner.configure(new PresenterPresets.Settings(s.editorSize(), s.outputSize(), PresenterPresets.Layout.values()[layout.getSelectedIndex()], s.commandOverlay(), s.corner()));
            }});
            tools.add(layout);
            JToggleButton spotlight = new JToggleButton(Actions.forID("View", "org.netbeans.modules.presenter.SpotlightSelectionAction"));
            spotlight.setText("Spotlight"); tools.add(spotlight);
            commands = new JToggleButton(Bundle.LBL_Commands());
            commands.addActionListener(e -> owner.toggleCommands(commands.isSelected())); tools.add(commands);
            JButton settingsButton = new JButton(Bundle.LBL_PresentationSettings());
            settingsButton.addActionListener(e -> Actions.forID("View", "org.netbeans.modules.presenter.PresenterSettingsAction").actionPerformed(e)); tools.add(settingsButton);
            JScrollPane scroll = new JScrollPane(tools, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scroll.setBorder(BorderFactory.createEmptyBorder()); panel.add(scroll, BorderLayout.CENTER);
            JButton exit = new JButton(Bundle.LBL_ExitPresentation());
            exit.addActionListener(e -> owner.stop()); panel.add(exit, BorderLayout.EAST);
            content = root.getContentPane();
            if (content.getLayout() instanceof BorderLayout border) {
                originalNorth = border.getLayoutComponent(BorderLayout.NORTH);
                header = new JPanel(new BorderLayout());
                if (originalNorth != null) { content.remove(originalNorth); header.add(originalNorth, BorderLayout.NORTH); }
                panel.setPreferredSize(new Dimension(400, panel.getPreferredSize().height + 16));
                header.add(panel, BorderLayout.SOUTH);
                content.add(header, BorderLayout.NORTH);
                content.revalidate();
            } else {
                pane.add(panel, JLayeredPane.PALETTE_LAYER); pane.addComponentListener(resize);
            }
        }
        updating = true;
        size.setText(" " + settings.editorSize() + " pt ");
        layout.setSelectedIndex(settings.layout().ordinal()); commands.setSelected(settings.commandOverlay());
        updating = false;
        position(); panel.setVisible(true);
    }
    private void position() {
        if (pane == null) return;
        if (header != null) { content.revalidate(); return; }
        Dimension preferred = panel.getPreferredSize();
        int width = Math.min(Math.max(0, pane.getWidth() - 24), Math.max(780, preferred.width));
        int height = preferred.height + 16;
        panel.setBounds(Math.max(0, (pane.getWidth() - width) / 2), Math.max(0, pane.getHeight() - height - 28), width, height);
        UIManager.put("nb.presenter.controlsHeight", height + 28);
        panel.revalidate();
    }
    void hide() {
        DemoCheckpoints.removeListener(walkthroughChanged);
        if (header != null) {
            if (content.getLayout() instanceof BorderLayout border && border.getLayoutComponent(BorderLayout.NORTH) == header) {
                content.remove(header);
                if (originalNorth != null) { header.remove(originalNorth); content.add(originalNorth, BorderLayout.NORTH); }
            }
            header.remove(panel); content.revalidate(); content.repaint();
            header = null; originalNorth = null; content = null;
        } else if (pane != null) { pane.removeComponentListener(resize); pane.remove(panel); pane.repaint(); }
        UIManager.put("nb.presenter.controlsHeight", null);
        pane = null; panel = null;
    }
}
