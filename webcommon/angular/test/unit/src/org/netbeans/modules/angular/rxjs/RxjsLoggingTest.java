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

public class RxjsLoggingTest extends TestCase {
    public void testRoundTripAndPreserveUserTap() {
        String source = "const result = source$.pipe(map(x => x + 1), tap(console.log));";
        String logged = RxjsLogging.insert(source, source.indexOf("map"));
        assertTrue(logged.startsWith(RxjsLogging.IMPORT));
        assertTrue(logged.contains("AlpLibre RxJS log"));
        assertEquals(source, RxjsLogging.remove(logged));
    }
    public void testTrailingCommentRemainsOutsideGeneratedTap() {
        String source = "source$.pipe(map(f) // keep this comment\n)";
        String logged = RxjsLogging.insert(source, source.indexOf("map"));
        assertTrue(logged.contains("map(f), /* AlpLibre"));
        assertTrue(logged.contains("value)) // keep this comment"));
        assertEquals(source, RxjsLogging.remove(logged));
    }
    public void testNestedStageLogging() {
        String source = "source$.pipe(switchMap(x => inner$.pipe(map(f))), filter(p))";
        String logged = RxjsLogging.insert(source, source.indexOf("map(f)"));
        assertTrue(logged.contains("map(f), /* AlpLibre"));
        assertEquals(source, RxjsLogging.remove(logged));
    }
    public void testGeneratedTextInsideStringIsPreserved() {
        String source = "const example = `, /* AlpLibre RxJS log */ __alpRxTap(value => console.debug('[RxJS stage @ 2]', value))`;";
        assertEquals(source, RxjsLogging.remove(source));
    }
    public void testIdentifierConflictIsRejected() {
        String source = "const __alpRxTap = 1; source$.pipe(map(f))";
        try { RxjsLogging.insert(source, source.indexOf("map")); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    public void testAllMappingOperatorsShowComparison() {
        for (String name : new String[]{"switchMap", "concatMap", "mergeMap", "exhaustMap"}) {
            String html = RxjsOperators.find(name).html();
            assertTrue(html.contains("Which mapping operator?"));
            assertTrue(html.contains("Ignore while busy"));
            assertTrue(html.contains("3 frames"));
        }
        assertEquals("", RxjsOperators.mappingComparison("map"));
    }
}
