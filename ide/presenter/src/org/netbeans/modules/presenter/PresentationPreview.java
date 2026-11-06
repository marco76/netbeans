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

import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** Restores the visible workspace if a live preview is cancelled. EDT only. */
final class PresentationPreview {
    private final PresenterModeAction owner;
    private final boolean wasActive;
    private final PresenterPresets.Settings settings;
    private final Map<JTextComponent, Object> fonts = new LinkedHashMap<>();
    private final Map<TopComponent, Boolean> panels = new LinkedHashMap<>();
    PresentationPreview(PresenterModeAction owner) {
        this.owner = owner;
        wasActive = owner.isPresenting(); settings = owner.currentSettings();
        EditorRegistry.componentList().forEach(editor -> fonts.put(editor, editor.getClientProperty("text-zoom")));
        WindowManager wm = WindowManager.getDefault();
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (!wm.isEditorTopComponent(tc) && !wm.isTopComponentFloating(tc)) panels.put(tc, wm.isTopComponentMinimized(tc));
        }
    }
    void restore() {
        if (wasActive) owner.configure(settings); else owner.stop();
        fonts.forEach((editor, zoom) -> editor.putClientProperty("text-zoom", zoom));
        WindowManager wm = WindowManager.getDefault();
        panels.forEach((tc, minimized) -> {
            if (tc.isOpened() && !wm.isTopComponentFloating(tc)) wm.setTopComponentMinimized(tc, minimized);
        });
        fonts.clear(); panels.clear();
    }
}
