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
package org.netbeans.modules.spellchecker.bindings.htmlxml;

import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.netbeans.api.html.lexer.HTMLTokenId;
import org.netbeans.api.lexer.Language;
import org.netbeans.editor.BaseDocument;

public class HtmlTokenListTest extends TestCase {
    private List<String> words(String source, int offset) throws Exception {
        BaseDocument doc = new BaseDocument(false, "text/html");
        doc.putProperty(Language.class, HTMLTokenId.language());
        doc.insertString(0, source, null);
        HtmlTokenList tokens = new HtmlTokenList(doc, null);
        tokens.setStartOffset(offset);
        List<String> words = new ArrayList<>();
        while (tokens.nextWord()) words.add(tokens.getCurrentWordText().toString());
        return words;
    }
    public void testControlHeadersAreCodeAndBodyIsProse() throws Exception {
        assertEquals(List.of("Welcome", "Hello", "world"), words(
                "Welcome @for (item of [{title: 'DevTools', link: 'https://angular.dev'}]; track item.id) { <p>Hello world</p> }", 0));
    }
    public void testNestedCallsAndQuotedParentheses() throws Exception {
        assertEquals(List.of("Visible"), words("@if (check(')') && ready()) { Visible }", 0));
    }
    public void testCommentsAndOrdinaryHtmlStillChecked() throws Exception {
        assertEquals(List.of("Check", "this", "Hello", "world"), words("<!-- Check this --> <p title=\"identifier\">Hello world</p>", 0));
    }
    public void testRestartInsideHeaderAndLetDeclaration() throws Exception {
        String source = "@let result = lookup('Something'); <p>Visible words</p>";
        assertEquals(List.of("Visible", "words"), words(source, 10));
    }
}
