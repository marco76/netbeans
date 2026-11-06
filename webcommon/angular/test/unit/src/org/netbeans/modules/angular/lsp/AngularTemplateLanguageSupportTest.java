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
import org.openide.filesystems.*;

public class AngularTemplateLanguageSupportTest extends TestCase {
    private void assertSpan(String source, String identifier) {
        int caret=source.indexOf('^');
        source=source.replace("^", "");
        int[] span=AngularTemplateLanguageSupport.span(source, caret);
        assertNotNull(source, span);
        assertEquals(identifier, source.substring(span[0], span[1]));
    }
    public void testTemplateNavigation() {
        assertSpan("{{ user.na^me }}", "name");
        assertSpan("@if (us^er) { <p /> }", "user");
        assertSpan("@for (item of it^ems; track item.id) {}", "items");
        assertSpan("<button (click)=\"sa^ve()\">", "save");
        assertSpan("<input [(ngModel)]=\"na^me\">", "name");
        assertSpan("<app-ca^rd [ti^tle]=\"value\">".replace("ti^tle", "title"), "app-card");
        assertSpan("<app-card [ti^tle]=\"value\">", "title");
        assertSpan("{{ name | upperca^se }}", "uppercase");
    }
    public void testNativeHtmlAndCommentsAreNotClaimed() {
        assertNull(AngularTemplateLanguageSupport.span("<a href=\"home\">", 10));
        assertNull(AngularTemplateLanguageSupport.span("<!-- {{ name }} -->", 10));
        assertNull(AngularTemplateLanguageSupport.span("ordinary words", 5));
        assertNull(AngularTemplateLanguageSupport.span("<div title=\"hello\">", 14));
    }
    public void testAutomaticCompletionIsWorkspaceScoped() throws Exception {
        FileObject root=FileUtil.createMemoryFileSystem().getRoot();
        FileObject html=root.createData("card.html");
        var support=new AngularTemplateLanguageSupport();
        assertFalse(support.autoComplete(html, "{{ na", 5, "a"));
        root.createData("angular.json");
        assertTrue(support.autoComplete(html, "{{ na", 5, "a"));
        String binding="<button (click)=\"sav";
        assertTrue(support.autoComplete(html, binding, binding.length(), "v"));
        assertTrue(support.autoComplete(html, "<app-c", 6, "c"));
        assertFalse(support.autoComplete(html, "hello", 5, "o"));
        assertFalse(support.autoComplete(html, "<p title=\"hello", 15, "o"));
    }
}
