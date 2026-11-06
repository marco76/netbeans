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
import java.awt.GridLayout;
import java.awt.event.*;
import java.util.EnumMap;
import javax.swing.*;
import org.openide.awt.*;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.windows.WindowManager;

@ActionID(category = "View", id = "org.netbeans.modules.presenter.PresenterSettingsAction")
@ActionRegistration(displayName = "#CTL_PresenterSettings")
@ActionReference(path = "Menu/View/Presentation", position = 1460)
@Messages({"CTL_PresenterSettings=Presentation Settings…", "LBL_PresentationPreset=Optional preset:",
    "LBL_Recording=Recording", "LBL_Talk=Live Talk", "LBL_EditorSize=Editor text size (pt):",
    "LBL_OutputSize=Terminal / Output text size (pt):", "LBL_Layout=Layout:",
    "LBL_CommandCorner=Command overlay corner:", "LBL_BottomRight=Bottom right", "LBL_BottomLeft=Bottom left",
    "LBL_TopRight=Top right", "LBL_TopLeft=Top left", "LBL_PreviewWorkspace=Preview in workspace",
    "LBL_ApplyPresentation=Apply", "LBL_CancelPresentation=Cancel",
    "LBL_PreviewHelp=Preview changes your workspace. Cancel restores the previous presentation state.",
    "LBL_LayoutHelp=Layouts arrange open docked panels. Main toolbars and floating windows stay available."})
public final class PresenterSettingsAction implements ActionListener {
    private static JDialog dialog;
    static void closeBeforeShutdown() {
        if (dialog != null) {
            dialog.getRootPane().putClientProperty("presentationShutdown", true);
            dialog.dispose();
        }
    }
    @Override public void actionPerformed(ActionEvent event) {
        if (dialog != null) { dialog.toFront(); return; }
        PresenterModeAction owner = PresenterModeAction.instance();
        PresentationPreview snapshot = new PresentationPreview(owner);
        boolean originallyActive = owner.isPresenting();
        PresenterPresets.Settings original = owner.currentSettings();
        var prefs = NbPreferences.forModule(PresenterModeAction.class);
        var drafts = new EnumMap<PresenterPresets.Preset, PresenterPresets.Settings>(PresenterPresets.Preset.class);
        for (var preset : PresenterPresets.Preset.values()) drafts.put(preset, PresenterPresets.load(prefs, preset));
        PresenterPresets.Preset[] selected = {PresenterPresets.selected(prefs)};
        if (originallyActive) drafts.put(selected[0], original);
        JComboBox<String> presetBox = new JComboBox<>(new String[]{Bundle.LBL_Talk(), Bundle.LBL_Recording()});
        presetBox.setSelectedIndex(selected[0].ordinal());
        JSpinner editor = new JSpinner(new SpinnerNumberModel(22, 14, 48, 2));
        JSpinner output = new JSpinner(new SpinnerNumberModel(20, 12, 40, 2));
        JComboBox<String> layout = new JComboBox<>(PresentationControls.layouts());
        JCheckBox commands = new JCheckBox(Bundle.LBL_Commands());
        JComboBox<String> corner = new JComboBox<>(new String[]{Bundle.LBL_BottomRight(), Bundle.LBL_BottomLeft(), Bundle.LBL_TopRight(), Bundle.LBL_TopLeft()});
        boolean[] previewing = {false}, updating = {false}, accepted = {false};
        java.util.function.Supplier<PresenterPresets.Settings> read = () -> new PresenterPresets.Settings(
                ((Number) editor.getValue()).intValue(), ((Number) output.getValue()).intValue(),
                PresenterPresets.Layout.values()[layout.getSelectedIndex()], commands.isSelected(), PresenterPresets.Corner.values()[corner.getSelectedIndex()]);
        Runnable load = () -> {
            updating[0] = true;
            var s = drafts.get(selected[0]);
            editor.setValue(s.editorSize()); output.setValue(s.outputSize()); layout.setSelectedIndex(s.layout().ordinal());
            commands.setSelected(s.commandOverlay()); corner.setSelectedIndex(s.corner().ordinal());
            updating[0] = false;
        };
        Runnable preview = () -> { if (!updating[0] && previewing[0]) owner.configure(read.get()); };
        load.run();
        editor.addChangeListener(e -> preview.run()); output.addChangeListener(e -> preview.run());
        layout.addActionListener(e -> preview.run()); commands.addActionListener(e -> preview.run()); corner.addActionListener(e -> preview.run());
        presetBox.addActionListener(e -> {
            drafts.put(selected[0], read.get()); selected[0] = PresenterPresets.Preset.values()[presetBox.getSelectedIndex()];
            load.run(); preview.run();
        });
        dialog = new JDialog(WindowManager.getDefault().getMainWindow(), Bundle.CTL_PresenterSettings(), false);
        JDialog window = dialog;
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
        Object[][] fields = {{Bundle.LBL_PresentationPreset(), presetBox}, {Bundle.LBL_EditorSize(), editor},
            {Bundle.LBL_OutputSize(), output}, {Bundle.LBL_Layout(), layout}, {Bundle.LBL_CommandCorner(), corner}};
        for (Object[] field : fields) {
            JLabel label = new JLabel((String) field[0]); label.setLabelFor((JComponent) field[1]);
            form.add(label); form.add((JComponent) field[1]);
        }
        form.add(commands); form.add(new JLabel());
        JPanel content = new JPanel(new BorderLayout(0, 16)); content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel help = new JPanel(new GridLayout(0, 1, 0, 5));
        help.add(new JLabel(Bundle.LBL_PreviewHelp())); help.add(new JLabel(Bundle.LBL_LayoutHelp()));
        content.add(help, BorderLayout.NORTH); content.add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton previewButton = new JButton(Bundle.LBL_PreviewWorkspace());
        previewButton.addActionListener(e -> { previewing[0] = true; preview.run(); });
        JButton apply = new JButton(Bundle.LBL_ApplyPresentation());
        apply.addActionListener(e -> {
            try { editor.commitEdit(); output.commitEdit(); } catch (java.text.ParseException ex) { return; }
            drafts.put(selected[0], read.get()); drafts.forEach((p, s) -> PresenterPresets.save(prefs, p, s));
            prefs.put("selectedPreset", selected[0].key);
            owner.configure(read.get()); accepted[0] = true; window.dispose();
        });
        JButton cancel = new JButton(Bundle.LBL_CancelPresentation()); cancel.addActionListener(e -> window.dispose());
        buttons.add(previewButton); buttons.add(apply); buttons.add(cancel); content.add(buttons, BorderLayout.SOUTH);
        window.setContentPane(content); window.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (!accepted[0] && previewing[0] && !Boolean.TRUE.equals(window.getRootPane().getClientProperty("presentationShutdown"))) snapshot.restore();
                dialog = null;
            }
        });
        window.getRootPane().setDefaultButton(apply);
        window.getRootPane().registerKeyboardAction(e -> window.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.pack();
        java.awt.Frame main = WindowManager.getDefault().getMainWindow();
        window.setLocation(main.getX() + Math.max(0, main.getWidth() - window.getWidth() - 24), main.getY() + 64);
        window.setVisible(true);
    }
}
