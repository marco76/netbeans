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

import javax.swing.text.PlainDocument;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class DemoCheckpointTest extends NbTestCase {
    public DemoCheckpointTest(String name) { super(name); }

    public void testOrderedPersistenceNavigationAndRemoval() {
        PresenterPresetsTest.MemoryPreferences prefs = new PresenterPresetsTest.MemoryPreferences();
        DemoCheckpointStore store = new DemoCheckpointStore(prefs);
        assertEquals(-1, store.next());
        store.entries.add(new DemoCheckpoint("Controller", "file:/project/", "src/Controller.java", 12, 20));
        store.entries.add(new DemoCheckpoint("Client", "file:/project/", "src/client.ts", 7, 7));
        store.entries.get(0).notes = "Explain the HTTP boundary";
        store.save();
        DemoCheckpointStore reopened = new DemoCheckpointStore(prefs);
        assertEquals(0, reopened.next());
        assertEquals(-1, reopened.previous());
        assertEquals(-1, reopened.current); // Looking at a candidate cannot advance a failed jump.
        assertEquals("Controller", reopened.entries.get(0).name);
        assertEquals("Explain the HTTP boundary", reopened.entries.get(0).notes);
        assertEquals("src/client.ts", reopened.entries.get(1).relativePath);
        assertEquals(20, reopened.entries.get(0).end());
        reopened.current = 0;
        assertEquals(1, reopened.next());
        reopened.current = 1;
        assertEquals(-1, reopened.next());
        assertEquals(0, reopened.previous());
        reopened.entries.remove(0);
        reopened.entries.get(0).name = "Angular Client";
        reopened.save();
        assertNull(prefs.get("1.name", null));
        DemoCheckpointStore finalStore = new DemoCheckpointStore(prefs);
        assertEquals(1, finalStore.entries.size());
        assertEquals("Angular Client", finalStore.entries.get(0).name);
    }

    public void testLiveSelectionTracksEditsAndDraftDoesNotRenameOriginal() throws Exception {
        PlainDocument document = new PlainDocument();
        document.insertString(0, "before SELECTED after", null);
        DemoCheckpoint entry = new DemoCheckpoint("Selection", "file:/project/", "demo.java", 7, 15);
        entry.track(document);
        DemoCheckpoint draft = entry.copy();
        draft.name = "Draft name";
        draft.notes = "Draft notes";
        assertEquals("", entry.notes);
        document.insertString(0, "prefix ", null);
        assertEquals(14, entry.start());
        assertEquals(22, entry.end());
        assertEquals(14, draft.start());
        assertEquals("Selection", entry.name);
        PresenterPresetsTest.MemoryPreferences prefs = new PresenterPresetsTest.MemoryPreferences();
        DemoCheckpointStore store = new DemoCheckpointStore(prefs);
        store.entries.add(entry);
        store.save();
        assertEquals(14, new DemoCheckpointStore(prefs).entries.get(0).start());
        PlainDocument shorter = new PlainDocument();
        shorter.insertString(0, "tiny", null);
        entry.track(shorter);
        assertEquals(4, entry.start());
        assertEquals(4, entry.end());
    }

    public void testRelativeFileReferenceDetectsMissingFiles() throws Exception {
        clearWorkDir();
        FileObject root = FileUtil.toFileObject(getWorkDir());
        FileObject file = FileUtil.createData(root, "src/demo.java");
        DemoCheckpoint entry = new DemoCheckpoint("Demo", root.toURL().toExternalForm(), "src/demo.java", 0, 0);
        assertEquals(file, entry.resolve());
        file.delete();
        assertNull(entry.resolve());
        assertNull(new DemoCheckpoint("Broken", "bad URL", "demo.java", 0, 0).resolve());
    }
}
