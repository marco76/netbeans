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
package org.netbeans.modules.spellchecker.plain;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.PlainDocument;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.spellchecker.spi.language.TokenList;

public class MarkdownTokenListTest extends NbTestCase {
    public MarkdownTokenListTest(String name) { super(name); }
    private void check(String text, int start, String... expected) throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, text, null);
        TokenList tokens = new MarkdownTokenListProvider().findTokenList(doc);
        tokens.setStartOffset(start);
        List<String> actual = new ArrayList<>();
        while (tokens.nextWord()) {
            String word = tokens.getCurrentWordText().toString();
            assertEquals(word, doc.getText(tokens.getCurrentWordStartOffset(), word.length()));
            actual.add(word);
        }
        assertEquals(List.of(expected), actual);
    }
    public void testCodeAndDestinations() throws Exception {
        check("Hello `ng serve` [guide](docs/setup.md) ![diagram](assets/view.png) https://example.com \n```bash\nng serve\n```\nWorld", 0,
                "Hello", "guide", "diagram", "World");
    }
    public void testStartInsideFence() throws Exception {
        String text = "Before\n~~~typescript\nconst value = true;\n~~~\nAfter";
        check(text, text.indexOf("value"), "After");
    }
    public void testDifferentAndShorterFencesDoNotClose() throws Exception {
        check("````java\n```\n~~~\nhidden\n````\nVisible", 0, "Visible");
    }
    public void testNestedDestinationAndReference() throws Exception {
        check("[visible](folder/file(copy).md)\n[ref]: https://example.com\n    hidden code\nProse", 0, "visible", "Prose");
    }
    public void testInlineMultipleBackticks() throws Exception {
        check("Before ``code ` nested`` After", 0, "Before", "After");
    }
}
