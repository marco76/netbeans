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
package org.netbeans.modules.angular.signals;

import junit.framework.TestCase;

public class SignalsCompletionDocumentationTest extends TestCase {
    public void testWritableValue() {
        String detail = "(property) Counter.count: WritableSignal<number>";
        assertTrue(SignalsCompletionDocumentation.description("count", detail).contains("Writable"));
        String html = SignalsCompletionDocumentation.reference("count", detail);
        assertTrue(html.contains("count.set(nextValue)"));
        assertTrue(html.contains("WritableSignal&lt;number&gt;"));
        assertTrue(html.contains("{{ count() }}"));
    }

    public void testReadOnlyValues() {
        for (String type : new String[]{"Signal", "InputSignal", "InputSignalWithTransform"}) {
            String html = SignalsCompletionDocumentation.reference("value", "(property) C.value: " + type + "<number>");
            assertNotNull(html);
            assertTrue(html.contains("read only"));
            assertFalse(html.contains("value.set("));
        }
    }

    public void testOrdinaryValuesAndFunctionsRemainUnchanged() {
        assertNull(SignalsCompletionDocumentation.reference("count", "(property) C.count: number"));
        assertNull(SignalsCompletionDocumentation.reference("make", "(function) make(): Signal<number>"));
        assertNull(SignalsCompletionDocumentation.reference("signal", "Auto import from 'other-package'"));
        assertNull(SignalsCompletionDocumentation.description("value", null));
    }

    public void testAngularFactories() {
        assertTrue(SignalsCompletionDocumentation.reference("computed", "Auto import from '@angular/core'").contains("computed(() =&gt; count() * 2)"));
        assertTrue(SignalsCompletionDocumentation.reference("signal", "Auto import from '@angular/core'").contains("const count = signal(0)"));
    }
}
