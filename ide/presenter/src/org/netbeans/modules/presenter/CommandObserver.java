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

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.KeyStroke;

/** Reads command metadata from the IDE's existing action notifications only. */
final class CommandObserver extends Handler {
    record Command(String name, KeyStroke shortcut) { }
    private final Consumer<Command> consumer;
    private final Logger actions = Logger.getLogger("org.netbeans.ui.actions");
    private final Logger editors = Logger.getLogger("org.netbeans.ui.actions.editor");
    private Level oldActions;
    private Level oldEditors;
    private boolean childHandler;
    private boolean changedActions;
    private boolean changedEditors;
    private volatile boolean active;

    CommandObserver(Consumer<Command> consumer) { this.consumer = consumer; }

    void start() {
        if (active) return;
        oldActions = actions.getLevel();
        oldEditors = editors.getLevel();
        active = true;
        actions.addHandler(this);
        childHandler = !editors.getUseParentHandlers();
        if (childHandler) editors.addHandler(this);
        changedActions = !actions.isLoggable(Level.FINER);
        if (changedActions) actions.setLevel(Level.FINER);
        changedEditors = !editors.isLoggable(Level.FINE);
        if (changedEditors) editors.setLevel(Level.FINE);
    }

    @Override public void close() {
        if (!active) return;
        active = false;
        actions.removeHandler(this);
        if (childHandler) editors.removeHandler(this);
        // Do not overwrite a logging setting changed elsewhere during the demo.
        if (changedActions && actions.getLevel() == Level.FINER) actions.setLevel(oldActions);
        if (changedEditors && editors.getLevel() == Level.FINE) editors.setLevel(oldEditors);
    }

    @Override public void publish(LogRecord record) {
        if (!active) return;
        Command command = command(record);
        if (command != null) consumer.accept(command);
    }

    @Override public void flush() { }

    static Command command(LogRecord record) {
        String kind = record.getMessage();
        if (!"UI_ACTION_EDITOR".equals(kind) && !"UI_ACTION_KEY_PRESS".equals(kind)
                && !"UI_ACTION_BUTTON_PRESS".equals(kind)) return null;
        Object[] params = record.getParameters();
        if (params == null || params.length < 5 || !(params[4] instanceof String name)) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.isBlank() || lower.contains("typed") || lower.startsWith("insert-")
                || lower.startsWith("delete-") || lower.contains("caret")
                || lower.startsWith("selection-") || lower.startsWith("select-")
                || (lower.contains("build-tool-tip") || lower.contains("build-popup-menu")) || lower.contains("kit-install")
                || lower.equals("page-up") || lower.equals("page-down")) return null;
        KeyStroke shortcut = params[0] instanceof KeyStroke ? (KeyStroke) params[0] : null;
        Action action = params[2] instanceof Action ? (Action) params[2] : null;
        AWTEvent event = EventQueue.getCurrentEvent();
        if (shortcut == null && "UI_ACTION_EDITOR".equals(kind)
                && event instanceof KeyEvent key && key.getID() == KeyEvent.KEY_PRESSED
                && (key.isControlDown() || key.isAltDown() || key.isMetaDown()
                    || (key.getKeyCode() >= KeyEvent.VK_F1 && key.getKeyCode() <= KeyEvent.VK_F12))) {
            shortcut = KeyStroke.getKeyStrokeForEvent(key);
        }
        if (shortcut == null && action != null && action.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke key) shortcut = key;
        // Typed keystrokes never become overlay content, even for unknown actions.
        if (shortcut != null && shortcut.getKeyCode() == KeyEvent.VK_UNDEFINED) return null;
        if (action != null && action.getValue("menuText") instanceof String menu) name = menu;
        name = name.replace("&", "").replace("...", "").replace("…", "").trim();
        if (name.matches("[a-z]+(?:-[a-z]+)+")) {
            StringBuilder title = new StringBuilder();
            for (String word : name.split("-")) {
                if (!title.isEmpty()) title.append(' ');
                title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            name = title.toString();
        }
        if (name.length() > 100) name = name.substring(0, 97) + "…";
        return name.isBlank() ? null : new Command(name, shortcut);
    }
}
