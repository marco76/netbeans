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

package org.netbeans.modules.javascript.nodejs.editor;

import org.netbeans.junit.NbTestCase;

public class PackageJsonCompletionProviderTest extends NbTestCase {
    public PackageJsonCompletionProviderTest(String name) {
        super(name);
    }

    public void testEmptyScopedDependencyVersion() {
        String text = "{\"private\":true,\"dependencies\":{\"@angular/cdk\":\"|\",\"@angular/core\":\"^21.2.0\"}}";
        PackageJsonCompletionProvider.Context context = context(text);
        assertNotNull(context);
        assertEquals("@angular/cdk", context.name);
        assertEquals("", context.prefix);
        assertEquals(context.start, context.end);
    }

    public void testRangesAndReplacementBounds() {
        for (String section : new String[]{"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"}) {
            for (String operator : new String[]{"^", "~", ""}) {
                String text = "{\"" + section + "\":{\"rxjs\":\"" + operator + "7.|8.0\"}}";
                PackageJsonCompletionProvider.Context context = context(text);
                assertNotNull(text, context);
                assertEquals(operator, context.operator);
                assertEquals(operator + "7.", context.prefix);
                assertEquals(operator + "7.8.0", context.original);
                assertEquals(context.original.length(), context.end - context.start);
            }
        }
    }

    public void testOnlyDirectDependencyValues() {
        for (String text : new String[]{
            "{\"scripts\":{\"build\":\"|\"}}",
            "{\"dependencies\":{\"rea|ct\":\"\"}}",
            "{\"nested\":{\"dependencies\":{\"react\":\"|\"}}}",
            "{\"dependencies\":{\"--help\":\"|\"}}",
            "{\"dependencies\":{\"react\":{\"version\":\"|\"}}}",
            "{\"description\":\"escaped \\\" dependencies\",\"name\":\"|\"}"
        }) {
            assertNull(text, context(text));
        }
    }

    public void testEarlierObjectsAndStringsDoNotBreakContext() {
        assertNotNull(context("{\"scripts\":{\"test\":\"echo {}\"},\"devDependencies\":{\"typescript\":\"|\"}}"));
    }

    private static PackageJsonCompletionProvider.Context context(String marked) {
        int caret = marked.indexOf('|');
        return PackageJsonCompletionProvider.context(marked.replace("|", ""), caret);
    }
}
