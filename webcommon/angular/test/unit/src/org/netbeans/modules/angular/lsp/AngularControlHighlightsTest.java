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
package org.netbeans.modules.angular.lsp;

import junit.framework.TestCase;

public class AngularControlHighlightsTest extends TestCase {
    public void testMultilineForStringsAndKeywords() {
        String source = "@for (item of [\n {title: 'Docs', link: 'https://angular.dev/a(b)'}\n]; track item.id) { <p>Hello</p> }";
        var spans = AngularControlHighlights.spans(source);
        assertEquals(7, spans.size());
        assertEquals("@for", source.substring(spans.get(0)[0], spans.get(0)[1]));
        assertEquals("of", source.substring(spans.get(1)[0], spans.get(1)[1]));
        assertEquals("'https://angular.dev/a(b)'", source.substring(spans.get(5)[0], spans.get(5)[1]));
        assertEquals("track", source.substring(spans.get(6)[0], spans.get(6)[1]));
    }
    public void testCommentsAndAttributesAreIgnored() {
        assertTrue(AngularControlHighlights.spans("<!-- @if (true) {} --> <p title=\"@for (x of y)\">Hello</p>").isEmpty());
    }
    public void testIncompleteHeaderAndLet() {
        assertEquals(2, AngularControlHighlights.spans("@if (ready && check('open'").size());
        assertEquals(4, AngularControlHighlights.spans("@let value = 42; @if (true) {}").size());
    }
    public void testBindingsAndInterpolationUseExpressionColoring() {
        String source = "<button [disabled]=\"busy || count > 2\" (click)=\"save('Done')\">{{ ready ? 'Yes' : 'No' }}</button>";
        var spans = AngularControlHighlights.spans(source);
        assertEquals(3L, spans.stream().filter(span -> span[2] == 5).count());
        assertEquals(3L, spans.stream().filter(span -> span[2] == 1).count());
        assertTrue(spans.stream().anyMatch(span -> source.substring(span[0], span[1]).equals("'Done'")));
    }
    public void testInterpolationDoesNotEndInsideQuotedText() {
        String source = "{{ value === '}}' ? 'Good' : 'Bad' }}";
        var spans = AngularControlHighlights.spans(source);
        assertEquals(4, spans.size());
        assertEquals(" value === '}}' ? 'Good' : 'Bad' ", source.substring(spans.get(0)[0], spans.get(0)[1]));
    }

}
