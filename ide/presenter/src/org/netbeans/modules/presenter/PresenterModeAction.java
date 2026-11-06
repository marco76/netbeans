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

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.openide.awt.*;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(category = "View", id = "org.netbeans.modules.presenter.PresenterModeAction")
@ActionRegistration(displayName = "#CTL_PresenterMode", lazy = false)
@ActionReference(path = "Menu/View/Presentation", position = 1450)
@Messages("CTL_PresenterMode=Presentation Mode")
@SuppressWarnings("serial")
public final class PresenterModeAction extends AbstractAction {
    private static PresenterModeAction running;
    private static final PropertyChangeSupport VIEW_CHANGES = new PropertyChangeSupport(PresenterModeAction.class);
    private final CommandOverlay overlay = new CommandOverlay();
    private final PresentationControls controls = new PresentationControls(this);
    private final EditorZoom zoom = new EditorZoom();
    private final Map<TopComponent, Boolean> panelStates = new LinkedHashMap<>();
    private final PropertyChangeListener editors = event -> update();
    private final PropertyChangeListener windows = event -> {
        if (TopComponent.Registry.PROP_OPENED.equals(event.getPropertyName())) { layoutRequested = true; update(); }
    };
    private PresenterPresets.Settings settings;
    private PresenterPresets.Preset preset;
    private boolean applyingLayout;
    private boolean layoutRequested;

    public PresenterModeAction() { super(Bundle.CTL_PresenterMode()); putValue(SELECTED_KEY, false); }
    static PresenterModeAction instance() {
        return (PresenterModeAction) Actions.forID("View", "org.netbeans.modules.presenter.PresenterModeAction");
    }
    static PresenterPresets.Preset currentView() { return running == null ? null : running.preset; }
    static void addViewListener(PropertyChangeListener listener) { VIEW_CHANGES.addPropertyChangeListener(listener); }
    static void selectView(PresenterPresets.Preset preset) {
        if (preset == null) { restoreBeforeShutdown(); return; }
        var prefs = NbPreferences.forModule(PresenterModeAction.class);
        prefs.put("selectedPreset", preset.key);
        instance().configure(PresenterPresets.load(prefs, preset));
    }
    PresenterPresets.Settings currentSettings() { return settings; }
    boolean isPresenting() { return running == this; }
    @Override public void actionPerformed(ActionEvent event) {
        if (!EventQueue.isDispatchThread()) { EventQueue.invokeLater(() -> actionPerformed(event)); return; }
        if (isPresenting()) stop();
        else {
            var prefs = NbPreferences.forModule(PresenterModeAction.class);
            configure(PresenterPresets.load(prefs, PresenterPresets.selected(prefs)));
        }
    }
    void configure(PresenterPresets.Settings value) {
        boolean starting = !isPresenting();
        boolean layoutChanged = starting || settings.layout() != value.layout();
        settings = value;
        preset = PresenterPresets.selected(NbPreferences.forModule(PresenterModeAction.class));
        if (starting) {
            running = this;
            EditorRegistry.addPropertyChangeListener(editors);
            TopComponent.getRegistry().addPropertyChangeListener(windows);
        }
        overlay.stop();
        overlay.corner(value.corner());
        if (value.commandOverlay()) overlay.start();
        UIManager.put("nb.presenter.outputFontSize", value.outputSize());
        layoutRequested = layoutChanged;
        update();
        controls.show(value);
        putValue(SELECTED_KEY, true);
        VIEW_CHANGES.firePropertyChange("view", null, preset);
    }
    void resize(int delta) { configure(settings.resize(delta)); }
    void toggleCommands(boolean enabled) {
        configure(new PresenterPresets.Settings(settings.editorSize(), settings.outputSize(), settings.layout(), enabled, settings.corner()));
    }
    private void update() {
        if (!EventQueue.isDispatchThread()) { EventQueue.invokeLater(this::update); return; }
        if (!isPresenting()) return;
        WindowManager wm = WindowManager.getDefault();
        EditorRegistry.componentList().forEach(editor -> {
            TopComponent tc = PresenterEditorContext.window(editor);
            if (tc != null && wm.isEditorTopComponent(tc)) {
                String mime = (String) editor.getDocument().getProperty("mimeType");
                FontColorSettings colors = MimeLookup.getLookup(mime == null ? "" : mime).lookup(FontColorSettings.class);
                var attrs = colors == null ? null : colors.getFontColors(FontColorNames.DEFAULT_COLORING);
                int base = attrs != null ? StyleConstants.getFontSize(attrs) : editor.getFont().getSize();
                zoom.target(editor, settings.editorSize(), base);
            }
        });
        if (applyingLayout || !layoutRequested) return;
        layoutRequested = false;
        applyingLayout = true;
        try {
            for (TopComponent tc : new java.util.ArrayList<>(TopComponent.getRegistry().getOpened())) {
                if (!wm.isEditorTopComponent(tc) && !wm.isTopComponentFloating(tc)) {
                    panelStates.putIfAbsent(tc, wm.isTopComponentMinimized(tc));
                }
            }
            for (TopComponent tc : new java.util.ArrayList<>(panelStates.keySet())) {
                if (tc.isOpened() && !wm.isTopComponentFloating(tc)) {
                    String id = wm.findTopComponentID(tc);
                    boolean minimize = !settings.layout().keep(id == null ? "" : id);
                    if (wm.isTopComponentMinimized(tc) != minimize) wm.setTopComponentMinimized(tc, minimize);
                }
            }
        } finally { applyingLayout = false; }
    }
    static void restoreBeforeShutdown() {
        SpotlightSelectionAction.clearCurrent();
        if (running != null) running.stop();
    }
    void stop() {
        PresenterPresets.Preset previous = preset;
        running = null;
        overlay.stop(); controls.hide(); SpotlightSelectionAction.clearCurrent();
        EditorRegistry.removePropertyChangeListener(editors);
        TopComponent.getRegistry().removePropertyChangeListener(windows);
        zoom.restore();
        UIManager.put("nb.presenter.outputFontSize", null);
        WindowManager wm = WindowManager.getDefault();
        panelStates.forEach((tc, minimized) -> {
            if (tc.isOpened() && !wm.isTopComponentFloating(tc)) wm.setTopComponentMinimized(tc, minimized);
        });
        panelStates.clear();
        putValue(SELECTED_KEY, false);
        VIEW_CHANGES.firePropertyChange("view", previous, null);
    }
}
