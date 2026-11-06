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
package org.netbeans.modules.markdown;

import java.awt.Color;
import javax.swing.text.PlainDocument;
import javax.swing.text.StyleConstants;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;

public class MarkdownCodeHighlightsTest extends NbTestCase {
    public MarkdownCodeHighlightsTest(String name) { super(name); }

    public void testBashBlockIncludesFenceBodyAndBlankLines() throws Exception {
        String text = "Before\n\n```bash\nng serve\n\n```\n\nAfter";
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, text, null);
        HighlightsSequence highlights = MarkdownCodeHighlights.createBag(doc, Color.LIGHT_GRAY).getHighlights(0, doc.getLength());
        assertTrue(highlights.moveNext());
        assertEquals(text.indexOf("```"), highlights.getStartOffset());
        assertTrue(highlights.getEndOffset() >= text.lastIndexOf("```") + 3);
        assertEquals(Color.LIGHT_GRAY, highlights.getAttributes().getAttribute(StyleConstants.Background));
        assertEquals(Boolean.TRUE, highlights.getAttributes().getAttribute(HighlightsContainer.ATTR_EXTENDS_EOL));
        assertNull(highlights.getAttributes().getAttribute(StyleConstants.Foreground));
        assertFalse(highlights.moveNext());
    }

    public void testInlineCodeDoesNotExtendToEndOfLine() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "Use `ng serve` here", null);
        HighlightsSequence highlights = MarkdownCodeHighlights.createBag(doc, Color.GRAY).getHighlights(0, doc.getLength());
        assertTrue(highlights.moveNext());
        assertEquals(4, highlights.getStartOffset());
        assertEquals(14, highlights.getEndOffset());
        assertNull(highlights.getAttributes().getAttribute(HighlightsContainer.ATTR_EXTENDS_EOL));
        assertFalse(highlights.moveNext());
    }

    public void testUnclosedAndUnknownLanguageBlocks() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "~~~unknown\ncontent", null);
        HighlightsSequence highlights = MarkdownCodeHighlights.createBag(doc, Color.GRAY).getHighlights(0, doc.getLength());
        assertTrue(highlights.moveNext());
        assertEquals(0, highlights.getStartOffset());
        assertEquals(doc.getLength(), highlights.getEndOffset());
    }
}
