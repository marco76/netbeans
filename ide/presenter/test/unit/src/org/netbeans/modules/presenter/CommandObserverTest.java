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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.netbeans.junit.NbTestCase;

public class CommandObserverTest extends NbTestCase {
    public CommandObserverTest(String name) { super(name); }

    private static LogRecord record(String kind, Object action, String name) {
        LogRecord record = new LogRecord(Level.FINER, kind);
        Object privatePayload = new Object() {
            @Override public String toString() { throw new AssertionError("Event payload must never be read"); }
        };
        record.setParameters(new Object[]{privatePayload, privatePayload, action, privatePayload, name});
        return record;
    }

    public void testUsesOnlyCommandNameAndShortcut() {
        Action action = new AbstractAction("ignored") {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { }
        };
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK);
        action.putValue(Action.ACCELERATOR_KEY, key);
        action.putValue("menuText", "&Go to Declaration");
        CommandObserver.Command command = CommandObserver.command(record("UI_ACTION_EDITOR", action, "goto-declaration"));
        assertEquals("Go to Declaration", command.name());
        assertEquals(key, command.shortcut());
        LogRecord global = record("UI_ACTION_KEY_PRESS", null, "&Build Project…");
        global.getParameters()[0] = key;
        assertEquals(new CommandObserver.Command("Build Project", key), CommandObserver.command(global));
        assertNotNull(CommandObserver.command(record("UI_ACTION_EDITOR", null, "build-project")));
    }

    public void testTypingAndUnrelatedEventsAreExcluded() {
        for (String name : new String[]{"default-typed", "insert-content", "insert-break", "insert-tab",
                "delete-previous", "caret-forward", "selection-forward", "build-tool-tip"}) {
            assertNull(name, CommandObserver.command(record("UI_ACTION_EDITOR", null, name)));
        }
        assertNull(CommandObserver.command(record("SOME_OTHER_LOG", null, "private text")));
        LogRecord typed = record("UI_ACTION_KEY_PRESS", null, "unknown-command");
        typed.getParameters()[0] = KeyStroke.getKeyStroke('x');
        assertNull(CommandObserver.command(typed));
    }

    public void testLifecycleRestoresLoggerStateAndStopsNotifications() {
        Logger actions = Logger.getLogger("org.netbeans.ui.actions");
        Logger editors = Logger.getLogger("org.netbeans.ui.actions.editor");
        Level oldActions = actions.getLevel();
        Level oldEditors = editors.getLevel();
        int handlers = actions.getHandlers().length;
        List<CommandObserver.Command> commands = new ArrayList<>();
        CommandObserver observer = new CommandObserver(commands::add);
        try {
            actions.setLevel(Level.INFO);
            editors.setLevel(null);
            observer.start();
            observer.start();
            assertEquals(handlers + 1, actions.getHandlers().length);
            observer.publish(record("UI_ACTION_BUTTON_PRESS", null, "Save All"));
            assertEquals(1, commands.size());
            observer.close();
            assertEquals(Level.INFO, actions.getLevel());
            assertNull(editors.getLevel());
            assertEquals(handlers, actions.getHandlers().length);
            observer.publish(record("UI_ACTION_BUTTON_PRESS", null, "Build Project"));
            assertEquals(1, commands.size());
            actions.setLevel(Level.FINEST);
            observer.start();
            assertEquals(Level.FINEST, actions.getLevel());
            observer.close();
            assertEquals(Level.FINEST, actions.getLevel());
        } finally {
            observer.close();
            actions.setLevel(oldActions);
            editors.setLevel(oldEditors);
        }
    }
}
