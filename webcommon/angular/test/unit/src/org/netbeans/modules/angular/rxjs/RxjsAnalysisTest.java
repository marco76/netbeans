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

public class RxjsAnalysisTest extends TestCase {
    public void testPipeCompletionContext() {
        assertEquals("", RxjsCompletionProvider.prefix("this.httpClient.get(\"/hello\").pipe("));
        assertEquals("sw", RxjsCompletionProvider.prefix("source$.pipe(map(x => x),\n sw"));
        assertEquals("ca", RxjsCompletionProvider.prefix("source$.pipe(switchMap(x => request(x).pipe(ca"));
        assertNull(RxjsCompletionProvider.prefix("source$.pipe(map(x => "));
        assertNull(RxjsCompletionProvider.prefix("source$.pipe(map(x), value."));
        assertNull(RxjsCompletionProvider.prefix("source$.pipe( // comment "));
        assertNull(RxjsCompletionProvider.prefix("source$.pipe('text "));
        assertNull(RxjsCompletionProvider.prefix("// source$.pipe("));
        assertNull(RxjsCompletionProvider.prefix("source$.pipe(map(x)).subscribe("));
    }
    public void testCompletionImports() {
        var map = RxjsOperators.find("map");
        assertEquals("import { map } from 'rxjs/operators';\n", RxjsCompletionProvider.importStatement("", map));
        assertEquals("", RxjsCompletionProvider.importStatement("import { map, tap } from 'rxjs';", map));
        assertEquals("", RxjsCompletionProvider.importStatement("import { map } from 'rxjs/operators';", map));
        assertFalse(RxjsCompletionProvider.importStatement("import { map as project } from 'rxjs';", map).isEmpty());
        assertTrue(RxjsCompletionProvider.importStatement("", RxjsOperators.find("takeUntilDestroyed")).contains("@angular/core/rxjs-interop"));
    }
    public void testOperatorsAndNestedPipes() {
        var stages = RxjsAnalysis.stages("query$.pipe(debounceTime(300), switchMap(q => search(q).pipe(catchError(() => of([])))))");
        assertEquals(3, stages.size());
        assertEquals("debounceTime", stages.get(0).name());
        assertEquals("switchMap", stages.get(1).name());
        assertEquals("catchError", stages.get(2).name());
    }
    public void testIgnoreCommentsStringsAndCallbackCalls() {
        assertTrue(RxjsAnalysis.stages("// x.pipe(map(fn))\nconst s = 'x.pipe(filter(fn))';").isEmpty());
        var stages = RxjsAnalysis.stages("x.pipe(tap(() => map(fn)), filter(ok))");
        assertEquals(2, stages.size());
        assertEquals("filter", stages.get(1).name());
    }
    public void testNestedSubscriptionOnlyInsideCallback() {
        assertTrue(RxjsAnalysis.inspect("a.subscribe(fn); b.subscribe(fn);").isEmpty());
        assertEquals(1, RxjsAnalysis.inspect("a.subscribe(v => { b.subscribe(fn); });").size());
        assertTrue(RxjsAnalysis.inspect("// a.subscribe(v => b.subscribe(fn))").isEmpty());
    }
    public void testLifecycleIsReviewSuggestion() {
        assertTrue(RxjsAnalysis.inspect("@Component({}) class C { init() { events.subscribe(fn); } }").get(0).message().contains("Review subscription ownership"));
        assertTrue(RxjsAnalysis.inspect("@Component({}) class C { init() { events.pipe(takeUntilDestroyed()).subscribe(fn); } }").isEmpty());
    }
    public void testFlatteningTimeline() {
        var latest = RxjsTimeline.tasks("switchMap");
        assertTrue(latest.get(0).cancelled());
        assertEquals(2, latest.get(0).end());
        assertFalse(latest.get(1).cancelled());
        var queued = RxjsTimeline.tasks("concatMap");
        assertEquals(4, queued.get(1).start());
        assertEquals(8, queued.get(2).start());
        assertEquals(12, queued.get(2).end());
        assertEquals(2, RxjsTimeline.tasks("mergeMap").get(1).start());
        assertTrue(RxjsTimeline.tasks("exhaustMap").get(1).ignored());
        assertEquals(7, RxjsTimeline.tasks("exhaustMap").get(2).start());
    }
    public void testCatalogAndEscaping() {
        assertTrue(RxjsOperators.ALL.size() >= 20);
        assertEquals(RxjsOperators.ALL.size(), RxjsOperators.ALL.stream().map(RxjsOperators.Operator::name).distinct().count());
        assertTrue(RxjsOperators.find("map").html().contains("T → R"));
        assertEquals("&lt;script&gt;&amp;", RxjsOperators.escape("<script>&"));
        assertTrue(RxjsAnalysis.explain("x.pipe(map(fn))").contains("not inferred TypeScript types"));
    }
}
