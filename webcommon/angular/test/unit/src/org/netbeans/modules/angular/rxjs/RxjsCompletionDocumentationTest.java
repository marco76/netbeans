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
package org.netbeans.modules.angular.rxjs;

import junit.framework.TestCase;

public class RxjsCompletionDocumentationTest extends TestCase {
    public void testServerAutoImportGetsReference() {
        String html = RxjsCompletionDocumentation.reference("first",
                "Auto import from 'rxjs' (alias) function first<T>(): OperatorFunction<T, T>");
        assertNotNull(html);
        assertTrue(html.contains("EmptyError"));
        assertTrue(html.contains("Example"));
        assertTrue(html.contains("first(value =&gt; value.active)"));
        assertFalse(html.contains("&apos;"));
    }

    public void testCompletionLabelEscapesHtml() {
        var op = new RxjsOperators.Operator("custom", "Utility", "Keep <T> & values",
                "", "T → T", "", "", "");
        assertEquals("<b>custom</b>  ·  Keep &lt;T&gt; &amp; values",
                RxjsCompletionProvider.label(op));
    }

    public void testMarblePreviewUsesSimultaneousCompletion() {
        String html = RxjsOperators.find("take").html();
        assertTrue(html.contains("Marble preview"));
        assertTrue(html.contains("take(2)"));
        assertTrue(html.contains("</b></font>|)"));
        assertTrue(html.contains("parentheses group simultaneous notifications"));
        assertFalse(RxjsOperators.find("switchMap").html().contains("Marble preview"));
    }

    public void testOriginMetadataEnrichesUnresolvedCompletion() {
        String description = RxjsCompletionDocumentation.description("concatMap", null, "rxjs");
        assertNotNull(description);
        assertTrue(description.contains("sequentially"));
        assertTrue(description.contains("Observable<R>"));
        assertNull(RxjsCompletionDocumentation.description("concatMap", null, "my-library"));
        assertNotNull(RxjsCompletionDocumentation.recognized("delay", null, "rxjs/operators"));
    }

    public void testActualTypeScriptMimeRegistration() {
        var providers = org.netbeans.api.editor.mimelookup.MimeLookup.getLookup("application/x-typescript")
                .lookupAll(org.netbeans.modules.lsp.client.spi.CompletionDocumentationProvider.class);
        assertTrue(providers.stream().anyMatch(p -> p instanceof RxjsCompletionDocumentation));
        var completions = org.netbeans.api.editor.mimelookup.MimeLookup.getLookup("application/x-typescript")
                .lookupAll(org.netbeans.spi.editor.completion.CompletionProvider.class);
        assertTrue(completions.stream().anyMatch(p -> p instanceof RxjsCompletionProvider));
    }

    public void testExtendedReferenceIncludesParametersAndCommonOperators() {
        assertTrue(RxjsOperators.find("mergeMap").html().contains("concurrent:"));
        for (String name : new String[]{"combineLatestWith", "concatWith", "endWith", "delay", "debounce", "distinct"}) {
            assertNotNull(RxjsOperators.find(name));
            assertTrue(RxjsOperators.find(name).html().contains("Watch for"));
        }
    }

    public void testExternalCatalogRendersEveryEntry() {
        var reference = RxjsReference.load();
        assertEquals(32, reference.operators().size());
        assertEquals("source$.pipe(map(user => user.name))", RxjsOperators.find("map").example());
        assertTrue(RxjsOperators.find("first").example().contains("\n"));
        for (var op : reference.operators()) {
            assertTrue(op.html().contains("Official documentation"));
            assertFalse(op.html().contains("{{"));
        }
        assertTrue(RxjsOperators.mappingComparison("switchMap").contains("<font color="));
    }

    public void testUnrelatedSymbolsKeepServerDocumentation() {
        assertNull(RxjsCompletionDocumentation.reference("first", "function first(): string"));
        assertNull(RxjsCompletionDocumentation.reference("first", "Auto import from 'other-rxjs'"));
        assertNull(RxjsCompletionDocumentation.reference("unknown", "Auto import from 'rxjs'"));
        assertNull(RxjsCompletionDocumentation.reference("first", null));
    }
}
