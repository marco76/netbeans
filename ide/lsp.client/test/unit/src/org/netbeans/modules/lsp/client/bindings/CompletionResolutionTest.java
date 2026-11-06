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
package org.netbeans.modules.lsp.client.bindings;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import junit.framework.TestCase;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

/** No native editor is needed to verify deferred auto-import resolution. */
public class CompletionResolutionTest extends TestCase {
    public void testPipeArgumentContext() {
        for (String source : List.of("this.client.get('/').pipe(",
                "this.client.get('/').pipe(\n  map(x => x), ",
                "value.pipe(/* ) */ ", "value.pipe(tap(() => {}), fi")) {
            assertTrue(source, CompletionProviderImpl.isPipeArgument(source));
        }
        for (String source : List.of("value.pipe()", "value.pipe().", "other(",
                "value.pipe(map(", "value.pipe(x => {", "value.pipe('",
                "// value.pipe(", "const s = 'value.pipe('")) {
            assertFalse(source, CompletionProviderImpl.isPipeArgument(source));
        }
    }

    public void testPipeFiltersClassesAndKeepsCustomOperators() {
        for (CompletionItemKind kind : List.of(CompletionItemKind.Class,
                CompletionItemKind.Interface, CompletionItemKind.Keyword, CompletionItemKind.Module)) {
            CompletionItem item = new CompletionItem("App");
            item.setKind(kind);
            assertFalse(CompletionProviderImpl.isPipeCandidate(item, true));
            assertTrue(CompletionProviderImpl.isPipeCandidate(item, false));
        }
        for (CompletionItemKind kind : List.of(CompletionItemKind.Function,
                CompletionItemKind.Variable, CompletionItemKind.Method)) {
            CompletionItem custom = new CompletionItem("customOperator");
            custom.setKind(kind);
            assertTrue(CompletionProviderImpl.isPipeCandidate(custom, true));
        }
        CompletionItem map = new CompletionItem("map");
        map.setKind(CompletionItemKind.Function);
        CompletionItem custom = new CompletionItem("customOperator");
        custom.setKind(CompletionItemKind.Function);
        assertTrue(CompletionProviderImpl.completionPriority(map, true)
                < CompletionProviderImpl.completionPriority(custom, true));
        assertEquals(100, CompletionProviderImpl.completionPriority(map, false));
    }

    public void testRedundantKeywordDocumentationIsHidden() {
        CompletionItem item = new CompletionItem("abstract");
        item.setDetail("abstract");
        assertFalse(CompletionProviderImpl.hasUsefulDocumentation(item));
        item.setDocumentation("  ");
        assertFalse(CompletionProviderImpl.hasUsefulDocumentation(item));
        item.setDocumentation("Declares an abstract class or member.");
        assertTrue(CompletionProviderImpl.hasUsefulDocumentation(item));
    }

    public void testTypeSignatureIsUsefulDocumentation() {
        CompletionItem item = new CompletionItem("HttpClient");
        item.setDetail("class HttpClient");
        assertTrue(CompletionProviderImpl.hasUsefulDocumentation(item));
    }

    public void testCompletionFollowsQueuedDocumentChanges() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean changed = new java.util.concurrent.atomic.AtomicBoolean();
        org.netbeans.modules.lsp.client.LSPBindings.runOnBackground(() -> {
            try {
                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                changed.set(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        TextDocumentService service = new TextDocumentService() {
            @Override public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams p) {
                if (!changed.get()) throw new IllegalStateException("Completion overtook typing");
                return CompletableFuture.completedFuture(Either.forLeft(List.of(new CompletionItem("HttpClient"))));
            }
            @Override public void didOpen(DidOpenTextDocumentParams p) {}
            @Override public void didChange(DidChangeTextDocumentParams p) {}
            @Override public void didClose(DidCloseTextDocumentParams p) {}
            @Override public void didSave(DidSaveTextDocumentParams p) {}
        };
        var result = CompletionProviderImpl.requestCompletion(service, new CompletionParams());
        try {
            assertFalse(result.isDone());
        } finally {
            release.countDown();
        }
        assertEquals("HttpClient", result.get(5, java.util.concurrent.TimeUnit.SECONDS).getLeft().get(0).getLabel());
    }

    private CompletionItem item() {
        CompletionItem item = new CompletionItem("ChildComponent");
        item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(0,0), new Position(0,2)), "app-child")));
        return item;
    }
    public void testMainEditDoesNotSuppressDeferredImports() {
        CompletionItem item = item();
        TextDocumentService service = new TextDocumentService() {
            @Override public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem input) {
                input.setAdditionalTextEdits(List.of(new TextEdit(new Range(new Position(0,0), new Position(0,0)), "import { ChildComponent } from './child';")));
                return CompletableFuture.completedFuture(input);
            }
            @Override public void didOpen(DidOpenTextDocumentParams p) {}
            @Override public void didChange(DidChangeTextDocumentParams p) {}
            @Override public void didClose(DidCloseTextDocumentParams p) {}
            @Override public void didSave(DidSaveTextDocumentParams p) {}
        };
        CompletionItem resolved = CompletionProviderImpl.resolveForCommit(item, new CompletionOptions(true, List.of()), service);
        assertEquals("app-child", resolved.getTextEdit().getLeft().getNewText());
        assertEquals(1, resolved.getAdditionalTextEdits().size());
        assertTrue(resolved.getAdditionalTextEdits().get(0).getNewText().contains("import { ChildComponent }"));
    }
    public void testIncompleteImportUsesStatementFilterRange() throws Exception {
        org.netbeans.editor.BaseDocument doc = new org.netbeans.editor.BaseDocument(false, "text/plain");
        String prefix = "import { HttpC";
        doc.insertString(0, prefix, null);
        CompletionItem item = new CompletionItem("HttpClient");
        item.setFilterText("import { HttpClient } from '@angular/common/http';");
        item.setTextEdit(Either.forLeft(new TextEdit(
                new Range(new Position(0, 0), new Position(0, prefix.length())), item.getFilterText())));
        assertTrue(CompletionProviderImpl.matchesCompletion(item, doc, prefix.length(), "HttpC"));
        item.setFilterText("import { Component } from '@angular/core';");
        assertFalse(CompletionProviderImpl.matchesCompletion(item, doc, prefix.length(), "HttpC"));
    }

    public void testDisabledResolveDoesNotContactServer() {
        CompletionItem item = item();
        assertSame(item, CompletionProviderImpl.resolveForCommit(item, new CompletionOptions(false, List.of()), null));
        assertSame(item, CompletionProviderImpl.resolveForCommit(item, null, null));
    }
}
