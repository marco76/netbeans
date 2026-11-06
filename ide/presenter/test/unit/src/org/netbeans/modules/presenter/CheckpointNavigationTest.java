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

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.PlainDocument;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileUtil;

public class CheckpointNavigationTest extends NbTestCase {
    public CheckpointNavigationTest(String name) { super(name); }
    public void testHiddenFirstSplitPaneAndDelayedFocusDoNotBlockNavigation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PlainDocument doc = new PlainDocument();
            JEditorPane hidden = new Pane(false); hidden.setDocument(doc);
            JEditorPane visible = new Pane(true); visible.setDocument(doc);
            JEditorPane different = new Pane(true);
            assertSame(visible, CheckpointNavigation.choose(new JEditorPane[]{hidden, visible}, doc, hidden));
            assertSame(visible, CheckpointNavigation.choose(new JEditorPane[]{different, visible}, doc, null));
            assertSame(visible, CheckpointNavigation.choose(new JEditorPane[]{visible, hidden}, doc, visible));
            assertNull(CheckpointNavigation.choose(new JEditorPane[]{hidden, different}, doc, null));
        });
    }
    public void testDirectSpotlightWorksWithoutWaitingForEditorRegistryFocus() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var file = FileUtil.createMemoryFileSystem().getRoot().createData("demo.ts");
                PlainDocument document = new PlainDocument();
                document.insertString(0, "before SELECTED after", null);
                document.putProperty(javax.swing.text.Document.StreamDescriptionProperty, file);
                JEditorPane pane = new Pane(true); pane.setDocument(document);
                SpotlightSelectionAction action = new SpotlightSelectionAction();
                action.activate(pane, 7, 15);
                assertEquals(Boolean.TRUE, action.getValue(javax.swing.Action.SELECTED_KEY));
                assertTrue(SpotlightLayerFactory.spotlight(pane).isActive());
                assertEquals(7, pane.getCaretPosition());
                assertSame(file, PresenterEditorContext.file(pane));
                document.insertString(0, "edit", null);
                assertFalse(SpotlightLayerFactory.spotlight(pane).isActive());
                assertEquals(Boolean.FALSE, action.getValue(javax.swing.Action.SELECTED_KEY));
            } catch (Exception ex) { throw new AssertionError(ex); }
            finally { SpotlightSelectionAction.clearCurrent(); }
        });
    }
    public void testResumeDestinationStaysOnCurrentPointWhileExploring() {
        DemoCheckpointStore store = new DemoCheckpointStore(new PresenterPresetsTest.MemoryPreferences());
        store.entries.add(new DemoCheckpoint("Controller", "file:/project/", "Controller.java", 10, 20));
        store.entries.add(new DemoCheckpoint("Client", "file:/project/", "client.ts", 30, 40));
        assertEquals(0, store.resume());
        store.current = 0;
        assertEquals(0, store.current);
        assertEquals(0, store.resume());
        assertEquals(1, store.next());
        assertEquals(-1, store.previous());
        store.save();
        assertEquals(0, store.current);
    }
    private static final class Pane extends JEditorPane {
        private static final long serialVersionUID = 1L;
        private final boolean showing;
        Pane(boolean showing) { this.showing = showing; setSize(showing ? 800 : 0, showing ? 600 : 0); }
        @Override public boolean isShowing() { return showing; }
    }
}
