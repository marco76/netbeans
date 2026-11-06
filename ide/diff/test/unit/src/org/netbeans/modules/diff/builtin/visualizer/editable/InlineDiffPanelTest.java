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

package org.netbeans.modules.diff.builtin.visualizer.editable;

import java.util.Map;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import org.netbeans.api.diff.Difference;
import org.netbeans.junit.NbTestCase;

public class InlineDiffPanelTest extends NbTestCase {
    public InlineDiffPanelTest(String name) { super(name); }

    private static final String BLOCK = "first meaningful line\nsecond meaningful line\nthird meaningful line\n";

    private Difference removed(String text) {
        return new Difference(Difference.DELETE, 1, 3, 0, 0, text, null);
    }

    private Difference added(String text) {
        return new Difference(Difference.ADD, 0, 0, 5, 7, null, text);
    }

    public void testExactUniqueMove() {
        Map<Integer, Integer> moves = InlineDiffPanel.findMoves(new Difference[] {removed(BLOCK), added(BLOCK)});
        assertEquals(Integer.valueOf(1), moves.get(0));
        assertEquals(Integer.valueOf(0), moves.get(1));
    }

    public void testRepeatedAndEditedBlocksAreNotMoves() {
        assertTrue(InlineDiffPanel.findMoves(new Difference[] {removed(BLOCK), added(BLOCK), added(BLOCK)}).isEmpty());
        assertTrue(InlineDiffPanel.findMoves(new Difference[] {removed(BLOCK), removed(BLOCK), added(BLOCK)}).isEmpty());
        assertTrue(InlineDiffPanel.findMoves(new Difference[] {removed(BLOCK), added(BLOCK + "extra\n")}).isEmpty());
        assertTrue(InlineDiffPanel.findMoves(new Difference[] {removed("}\n}\n}\n"), added("}\n}\n}\n")}).isEmpty());
    }

    public void testWhitespaceFilteringPreservesSourceCoordinatesAndText() throws Exception {
        org.netbeans.modules.diff.builtin.provider.BuiltInDiffProvider provider =
                new org.netbeans.modules.diff.builtin.provider.BuiltInDiffProvider();
        org.netbeans.modules.diff.builtin.provider.BuiltInDiffProvider.Options options =
                new org.netbeans.modules.diff.builtin.provider.BuiltInDiffProvider.Options();
        options.ignoreLeadingAndtrailingWhitespace = true;
        options.ignoreInnerWhitespace = true;
        provider.setOptions(options);
        String first = "  same ( value );\nold value\n";
        String second = "same(value);\nnew value\n";
        Difference[] diffs = provider.computeDiff(new java.io.StringReader(first), new java.io.StringReader(second));
        assertEquals(1, diffs.length);
        assertEquals(2, diffs[0].getFirstStart());
        assertEquals(2, diffs[0].getSecondStart());
        assertEquals("old value\n", diffs[0].getFirstText());
        assertEquals("new value\n", diffs[0].getSecondText());
    }

    public void testContextExpansionAndShowAll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            InlineDiffPanel panel = new InlineDiffPanel(null);
            String content = String.join("\n", java.util.stream.IntStream.rangeClosed(1, 20)
                    .mapToObj(i -> "source line " + i).toArray(String[]::new));
            panel.setContent(content, content, new Difference[0]);
            JTextPane editor = (JTextPane) ((JScrollPane) panel.getComponent(0)).getViewport().getView();
            assertTrue(editor.getText().contains("source line 1\n"));
            assertTrue(editor.getText().contains("source line 20"));
            assertFalse(editor.getText().contains("source line 10"));
            JButton expand = null;
            for (int i = 0; i < editor.getStyledDocument().getLength(); i++) {
                Object component = StyleConstants.getComponent(editor.getStyledDocument().getCharacterElement(i).getAttributes());
                if (component instanceof JButton) { expand = (JButton) component; break; }
            }
            assertNotNull(expand);
            expand.doClick();
            assertTrue(editor.getText().contains("source line 10"));
            panel.setContent(content, content, new Difference[0]);
            panel.setCollapsed(false);
            assertTrue(editor.getText().contains("source line 10"));
            assertTrue(editor.getText().contains("source line 20"));
        });
    }
}
