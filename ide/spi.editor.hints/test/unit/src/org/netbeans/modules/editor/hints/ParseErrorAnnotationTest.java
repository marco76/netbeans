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
package org.netbeans.modules.editor.hints;

import java.util.List;
import junit.framework.TestCase;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.Severity;

public class ParseErrorAnnotationTest extends TestCase {
    public void testDiagnosticEscapesCodeAndDoesNotAdvertiseMissingFixes() {
        var fixes = new FixData(new StaticFixList(), new StaticFixList());
        var annotation = new ParseErrorAnnotation(Severity.ERROR, fixes,
                "Expected <app-card> & value\nSecond line", null, null);
        String tooltip = annotation.getShortDescription();
        assertTrue(tooltip.contains("<b>Error</b>"));
        assertTrue(tooltip.contains("&lt;app-card&gt; &amp; value<br>Second line"));
        assertFalse(tooltip.contains("Alt+Enter"));
    }
    public void testAvailableFixAdvertisesShortcut() {
        Fix fix = new Fix() {
            @Override public String getText() { return "Fix it"; }
            @Override public ChangeInfo implement() { return null; }
        };
        var fixes = new FixData(new StaticFixList(List.of(fix)), new StaticFixList());
        var annotation = new ParseErrorAnnotation(Severity.WARNING, fixes, "Check this", null, null);
        assertTrue(annotation.getShortDescription().contains("<b>Warning</b>"));
        assertTrue(annotation.getShortDescription().contains("Alt+Enter"));
    }
}
