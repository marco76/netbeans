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
import javax.swing.AbstractAction;
import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(category = "View", id = "org.netbeans.modules.presenter.SpotlightSelectionAction")
@ActionRegistration(displayName = "#CTL_SpotlightSelection", lazy = false)
@ActionReference(path = "Menu/View/Presentation", position = 1470)
@Messages("CTL_SpotlightSelection=Spotlight Selection")
@SuppressWarnings("serial")
public final class SpotlightSelectionAction extends AbstractAction {
    private static SpotlightSelectionAction current;
    private JTextComponent editor;
    private SelectionSpotlight active;
    private final CaretListener caretListener = event -> refresh();
    private final PropertyChangeListener documentListener = event -> {
        if ("document".equals(event.getPropertyName())) refresh();
    };

    private boolean updating;
    public SpotlightSelectionAction() {
        super(Bundle.CTL_SpotlightSelection());
        EditorRegistry.addPropertyChangeListener(event -> {
            Runnable change = () -> {
                if (EditorRegistry.FOCUS_GAINED_PROPERTY.equals(event.getPropertyName())) bind(EditorRegistry.lastFocusedComponent());
                if (editor != null && !EditorRegistry.componentList().contains(editor)) bind(null);
                refresh();
            };
            if (EventQueue.isDispatchThread()) change.run(); else EventQueue.invokeLater(change);
        });
        bind(EditorRegistry.lastFocusedComponent());
    }

    private void bind(JTextComponent target) {
        if (PresenterEditorContext.file(target) == null) target = null;
        if (editor != target) {
            clear();
            if (editor != null) { editor.removeCaretListener(caretListener); editor.removePropertyChangeListener(documentListener); }
            editor = target;
            if (editor != null) { editor.addCaretListener(caretListener); editor.addPropertyChangeListener(documentListener); }
        }
        refresh();
    }
    private void refresh() {
        if (updating) return;
        if (!EventQueue.isDispatchThread()) { EventQueue.invokeLater(this::refresh); return; }
        if (active != null && (editor == null || active.document != editor.getDocument() || !active.isActive())) clear();
        putValue(SELECTED_KEY, active != null);
        setEnabled(active != null || (editor != null && editor.getSelectionEnd() > editor.getSelectionStart()));
    }
    static void clearCurrent() {
        if (current != null) { SpotlightSelectionAction action = current; action.clear(); action.refresh(); }
    }
    private void clear() {
        if (current == this) current = null;
        SelectionSpotlight previous = active; active = null;
        if (previous != null) previous.clear();
    }
    static void showRange(JTextComponent target, int start, int end) {
        SpotlightSelectionAction action = (SpotlightSelectionAction) org.openide.awt.Actions.forID("View", "org.netbeans.modules.presenter.SpotlightSelectionAction");
        action.activate(target, start, end);
    }
    void activate(JTextComponent target, int start, int end) {
        clearCurrent(); bind(target);
        if (editor == null || end <= start) return;
        updating = true;
        try {
            current = this; active = SpotlightLayerFactory.spotlight(editor);
            active.activate(start, end, editor.getForeground(), editor.getBackground(), this::refresh);
            editor.setCaretPosition(start);
        } finally { updating = false; }
        refresh();
    }
    @Override public void actionPerformed(ActionEvent event) {
        if (!EventQueue.isDispatchThread()) { EventQueue.invokeLater(() -> actionPerformed(event)); return; }
        if (active != null) clear();
        else {
            bind(EditorRegistry.lastFocusedComponent());
            if (editor != null) activate(editor, editor.getSelectionStart(), editor.getSelectionEnd());
        }
        refresh();
    }
}
