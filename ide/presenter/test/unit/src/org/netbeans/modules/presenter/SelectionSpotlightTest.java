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

import java.awt.Color;
import javax.swing.JEditorPane;
import javax.swing.text.PlainDocument;
import javax.swing.text.StyleConstants;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;

public class SelectionSpotlightTest extends NbTestCase {
    public SelectionSpotlightTest(String name) { super(name); }

    public void testOnlySurroundingsAreDimmedAndSourceIsUnchanged() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "before SELECTED after", null);
        SelectionSpotlight state = new SelectionSpotlight(doc);
        state.activate(7, 15, Color.BLACK, Color.WHITE, null);
        HighlightsSequence sequence = state.highlights.getHighlights(0, doc.getLength());
        assertTrue(sequence.moveNext());
        assertEquals(0, sequence.getStartOffset());
        assertEquals(7, sequence.getEndOffset());
        assertEquals(new Color(153, 153, 153), StyleConstants.getForeground(sequence.getAttributes()));
        assertNull(sequence.getAttributes().getAttribute(StyleConstants.Background));
        assertTrue(sequence.moveNext());
        assertEquals(15, sequence.getStartOffset());
        assertEquals(doc.getLength(), sequence.getEndOffset());
        assertFalse(sequence.moveNext());
        assertEquals("before SELECTED after", doc.getText(0, doc.getLength()));
        state.clear();
        assertFalse(state.isActive());
        assertFalse(state.highlights.getHighlights(0, doc.getLength()).moveNext());
    }

    public void testEditsClearSpotlightAndNotifyOnce() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "abcdef", null);
        SelectionSpotlight state = new SelectionSpotlight(doc);
        int[] clears = {0};
        state.activate(1, 4, Color.WHITE, Color.BLACK, () -> clears[0]++);
        doc.insertString(2, "new", null);
        assertFalse(state.isActive());
        assertEquals(1, clears[0]);
        assertFalse(state.highlights.getHighlights(0, doc.getLength()).moveNext());
        state.clear();
        assertEquals(1, clears[0]);
        state.activate(1, 4, Color.WHITE, Color.BLACK, () -> clears[0]++);
        doc.remove(0, 2);
        assertFalse(state.isActive());
        assertEquals(2, clears[0]);
    }

    public void testSplitEditorsAreIndependentAndDocumentReplacementClears() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "abcdef", null);
        JEditorPane first = new JEditorPane();
        JEditorPane second = new JEditorPane();
        first.setDocument(doc);
        second.setDocument(doc);
        SelectionSpotlight firstState = SpotlightLayerFactory.spotlight(first);
        SelectionSpotlight secondState = SpotlightLayerFactory.spotlight(second);
        firstState.activate(1, 4, Color.BLACK, Color.WHITE, null);
        assertTrue(firstState.isActive());
        assertFalse(secondState.isActive());
        assertFalse(secondState.highlights.getHighlights(0, doc.getLength()).moveNext());
        first.setDocument(new PlainDocument());
        assertNotSame(firstState, SpotlightLayerFactory.spotlight(first));
        assertFalse(firstState.isActive());
        secondState.activate(2, 2, Color.WHITE, Color.BLACK, null);
        assertFalse(secondState.isActive());
    }
}
