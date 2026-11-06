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
package org.netbeans.modules.angular.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class AngularGeneratedFilesTest extends TestCase {
    public AngularGeneratedFilesTest(String name) { super(name); }

    public void testExternalFilesUseActualNamesAndExcludeExistingFiles() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject component = file(root, "src/app/card/card.ts", "@Component({templateUrl: './card.html', styleUrl: './card.scss'})\nexport class Card {}\n");
        FileObject html = file(root, "src/app/card/card.html", "<h2>Card</h2>");
        FileObject style = file(root, "src/app/card/card.scss", "h2 { color: navy; }");
        FileObject test = file(root, "src/app/card/card.spec.ts", "describe('Card', () => {});");
        file(root, "src/app/existing.ts", "export class Existing {}");
        List<AngularGeneratedFiles.Entry> entries = AngularGeneratedFiles.describe(root, Set.of(component, html, style, test));
        assertEquals(4, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.file().equals(component) && e.line() == 1));
        assertTrue(entries.stream().anyMatch(e -> e.file().equals(html) && e.explanation().contains("event bindings")));
        assertTrue(entries.stream().anyMatch(e -> e.file().equals(style) && e.explanation().contains("styleUrls")));
        assertTrue(entries.stream().anyMatch(e -> e.file().equals(test) && e.explanation().contains("TestBed")));
        assertFalse(entries.stream().anyMatch(e -> e.label().contains("existing")));
    }

    public void testInlineSectionsNavigateToTheirSourceLinesWithoutInventedCompanions() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject component = file(root, "user-card.component.ts", "import { Component } from '@angular/core';\n@Component({\n  selector: 'app-user-card',\n\n  template: `<h2>Card</h2>`,\n  styles: [`h2 { color: navy; }`]\n})\nexport class UserCard {}\n");
        List<AngularGeneratedFiles.Entry> entries = AngularGeneratedFiles.describe(root, Set.of(component));
        assertEquals(3, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.file().equals(component)));
        assertTrue(entries.stream().anyMatch(e -> e.label().startsWith("Inline HTML") && e.line() == 4));
        assertTrue(entries.stream().anyMatch(e -> e.label().startsWith("Inline styles") && e.line() == 5));
        assertFalse(entries.stream().anyMatch(e -> e.label().startsWith("Unit test")));
    }

    public void testNoStylesOrTestsAndNoInlineMetadata() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject component = file(root, "plain.ts", "@Component({templateUrl: './plain.html'})\nexport class Plain {\n  styles: string[] = [];\n}\n");
        assertEquals(1, AngularGeneratedFiles.describe(root, Set.of(component)).size());
        assertTrue(AngularGeneratedFiles.describe(root, Set.of()).isEmpty());
    }

    public void testSnapshotIncludesCompanionsButSkipsDependenciesAndBuildOutput() throws Exception {
        Path root = Files.createTempDirectory("angular-generated-files");
        try {
            Files.writeString(root.resolve("card.ts"), "");
            Set<Path> before = AngularComponentWizardIterator.sourceFiles(root);
            for (String name : List.of("card.html", "card.scss", "card.spec.ts")) Files.writeString(root.resolve(name), "");
            Files.createDirectories(root.resolve("node_modules/pkg"));
            Files.writeString(root.resolve("node_modules/pkg/dependency.ts"), "");
            Files.createDirectories(root.resolve("dist"));
            Files.writeString(root.resolve("dist/generated.html"), "");
            Files.writeString(root.resolve("notes.txt"), "");
            Set<Path> created = AngularComponentWizardIterator.sourceFiles(root);
            created.removeAll(before);
            assertEquals(Set.of(root.resolve("card.html"), root.resolve("card.scss"), root.resolve("card.spec.ts")), created);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static FileObject file(FileObject root, String path, String text) throws IOException {
        FileObject file = FileUtil.createData(root, path);
        try (var output = file.getOutputStream()) { output.write(text.getBytes(StandardCharsets.UTF_8)); }
        return file;
    }
}
