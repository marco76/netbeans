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

import java.util.Set;
import junit.framework.TestCase;

public class AngularMaterialCompletionProviderTest extends TestCase {
    public void testInstalledDeclarationSelectors() {
        assertEquals(Set.of("mat-card", "mat-card-title"),
                AngularMaterialCompletionProvider.selectors(
                    "static ɵcmp: i0.ɵɵComponentDeclaration<MatCard, \"mat-card\", never, {}, {}, never>;"
                    + "static ɵcmp: i0.ɵɵComponentDeclaration<MatCardTitle, \"mat-card-title, [mat-card-title]\", never>;"));
        assertTrue(AngularMaterialCompletionProvider.selectors(
                "static ɵdir: i0.ɵɵDirectiveDeclaration<MatButton, \"button[mat-button]\", never>;").isEmpty());
    }
    public void testTagPrefixInNestedMarkup() {
        String source="<div><mat";
        assertEquals("mat", AngularMaterialCompletionProvider.prefix(source, source.length()));
        assertEquals("mat-ca", AngularMaterialCompletionProvider.prefix("<mat-ca", 7));
        assertNull(AngularMaterialCompletionProvider.prefix("<div>mat", 8));
        assertNull(AngularMaterialCompletionProvider.prefix("<!-- <mat", 9));
        assertNull(AngularMaterialCompletionProvider.prefix("<div title=\"mat", 15));
    }
    public void testComponentDetails() {
        var entry=AngularMaterialCompletionProvider.entries(
                "i0.ɵɵComponentDeclaration<MatCard, \"mat-card\", never>", "@angular/material/card").get("mat-card");
        assertEquals("MatCard", entry.className());
        assertEquals("card", entry.hint());
        assertTrue(entry.documentation().contains("@angular/material/card"));
        assertTrue(entry.documentation().contains("&lt;mat-card&gt;"));
    }

    private void assertInsertion(String before, String expected) throws Exception {
        int caret=before.indexOf('|');
        javax.swing.JTextArea editor=new javax.swing.JTextArea(before.replace("|", ""));
        editor.setCaretPosition(caret);
        javax.swing.SwingUtilities.invokeAndWait(() -> AngularMaterialCompletionProvider.insert(editor, 1, "mat-card"));
        int expectedCaret=expected.indexOf('|');
        assertEquals(expected.replace("|", ""), editor.getText());
        assertEquals(expectedCaret, editor.getCaretPosition());
    }

    public void testPairedInsertionAndExistingMarkup() throws Exception {
        assertInsertion("<mat-ca|", "<mat-card>|</mat-card>");
        assertInsertion("<mat-ca|</div>", "<mat-card>|</mat-card></div>");
        assertInsertion("<mat-ca|</mat-card>", "<mat-card>|</mat-card>");
        assertInsertion("<mat-ca|rd class=\"card\">", "<mat-card| class=\"card\">");
        assertInsertion("<mat-ca|></mat-card>", "<mat-card|></mat-card>");
    }

    public void testPublicExportDeterminesPackage() throws Exception {
        var root=org.openide.filesystems.FileUtil.createMemoryFileSystem().getRoot();
        try (var out=new java.io.OutputStreamWriter(root.createData("package.json").getOutputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            out.write("{\"exports\":{\"./card\":{\"types\":\"./types/card-public.d.ts\"}}}");
        }
        var types=root.createFolder("types");
        for (String file:new String[]{"card-public.d.ts", "internal.d.ts"}) {
            try (var out=new java.io.OutputStreamWriter(types.createData(file).getOutputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                out.write("i0.ɵɵComponentDeclaration<MatCard, \"" + (file.startsWith("internal") ? "mat-internal" : "mat-card") + "\", never>");
            }
        }
        var entries=new java.util.TreeMap<String, AngularMaterialCompletionProvider.Entry>();
        AngularMaterialCompletionProvider.collectPackage(root, entries);
        assertEquals(Set.of("mat-card"), entries.keySet());
        assertEquals("@angular/material/card", entries.get("mat-card").packageName());
    }

    public void testExportingModulePreferred() {
        var entry = AngularMaterialCompletionProvider.entries(
                "i0.ɵɵComponentDeclaration<MatCard, \"mat-card\", never>; i0.ɵɵNgModuleDeclaration<MatCardModule, never, never, never>;",
                "@angular/material/card").get("mat-card");
        assertEquals("MatCardModule", entry.importName());
    }

    public void testAutomaticStandaloneImports() {
        String before = "import { Component } from '@angular/core';\n@Component({imports: [], templateUrl: './app.html'}) export class App {}";
        String after = AngularMaterialImports.addImport(before, "MatCard", "@angular/material/card");
        assertTrue(after.contains("import { MatCard } from '@angular/material/card';"));
        assertTrue(after.contains("imports: [MatCard]"));
        assertEquals(after, AngularMaterialImports.addImport(after, "MatCard", "@angular/material/card"));
        String alias = "import { MatCard as Card } from '@angular/material/card';\n@Component({imports: [CommonModule,], template: `}`}) class App {}";
        assertTrue(AngularMaterialImports.addImport(alias, "MatCard", "@angular/material/card").contains("imports: [Card, CommonModule,]"));
        assertNotNull(AngularMaterialImports.addImport("@Component({standalone: true, template: ''}) class App {}", "MatCard", "@angular/material/card"));
        assertNull(AngularMaterialImports.addImport("@Component({standalone: false}) class App {}", "MatCard", "@angular/material/card"));
        assertNull(AngularMaterialImports.addImport("@Component({imports: SHARED}) class App {}", "MatCard", "@angular/material/card"));
        assertNull(AngularMaterialImports.addImport("// @Component({imports: []})", "MatCard", "@angular/material/card"));
    }

}
