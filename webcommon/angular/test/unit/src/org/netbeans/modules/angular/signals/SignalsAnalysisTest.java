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
import org.netbeans.editor.BaseDocument;

public class SignalsAnalysisTest extends TestCase {
    private static final String HEADER = "import {signal, computed, effect} from '@angular/core';\nclass Counter {\n count = signal(0);\n doubled = computed(() => this.count() * 2);\n";
    public void testMissingReadAndFix() throws Exception {
        String source = HEADER + " check() { if (this.count) {} while (!this.doubled) {} }\n}";
        var findings = SignalsAnalysis.inspect(source);
        assertEquals(2, findings.size());
        assertTrue(findings.get(0).readFix());
        assertEquals("this.count", source.substring(findings.get(0).start(), findings.get(0).end()));
        BaseDocument doc = new BaseDocument(false, "text/plain");
        doc.insertString(0, source, null);
        SignalsHints.readFix(doc, source, findings.get(0)).implement();
        assertTrue(doc.getText(0, doc.getLength()).contains("if (this.count())"));
        // Stale offsets must never modify an edited document.
        SignalsHints.readFix(doc, source, findings.get(1)).implement();
        assertTrue(doc.getText(0, doc.getLength()).contains("while (!this.doubled)"));
    }
    public void testReadOnlyWrites() {
        var findings = SignalsAnalysis.inspect(HEADER + " go() { this.doubled.set(3); this.doubled.update(v => v+1); this.count.set(2); }\n}");
        assertEquals(2, findings.size());
        assertFalse(findings.get(0).readFix());
        assertTrue(findings.get(0).message().contains("read only"));
    }
    public void testEffectGuidance() {
        var findings = SignalsAnalysis.inspect(HEADER + " constructor() { effect(() => { this.count.set(this.doubled()); }); }\n}");
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message().contains("computed"));
        assertFalse(findings.get(0).readFix());
    }
    public void testLegalReadsAndPassingReferences() {
        assertTrue(SignalsAnalysis.inspect(HEADER + " go() { if (this.count()) {} consume(this.count); const alias = this.count; this.count.update(v => v + 1); }\n}").isEmpty());
    }
    public void testCommentsStringsAndOtherFactories() {
        assertTrue(SignalsAnalysis.inspect(HEADER + " // if (this.count) {}\n text = \"if (this.count) {}\";\n go() { /* this.doubled.set(1) */ }\n}").isEmpty());
        assertTrue(SignalsAnalysis.inspect("import {signal} from 'other';\nclass C {\n count = signal(0);\n go() { if (this.count) {} }\n}").isEmpty());
    }
    public void testRegexLiteralIsIgnored() {
        assertTrue(SignalsAnalysis.inspect(HEADER + " pattern = /if (this.count) {}/;\n}").isEmpty());
    }
    public void testAliasesAndAmbiguousScopes() {
        String source = "import {signal as state} from '@angular/core';\nclass C {\n count = state(0);\n go() { if (this.count) {} }\n}";
        assertEquals(1, SignalsAnalysis.inspect(source).size());
        assertTrue(SignalsAnalysis.inspect(source + "\nclass Other {}").isEmpty());
        assertTrue(SignalsAnalysis.inspect(HEADER + " go() { const local = signal(0); if (this.local) {} }\n}").isEmpty());
    }
}
