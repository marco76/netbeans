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

import java.util.List;
import junit.framework.TestCase;

public class AngularComponentWizardIteratorTest extends TestCase {
    private static final String DEFAULT = "Workspace default";

    public void testDefaultsAreDelegatedToWorkspaceSchematics() {
        List<String> args = AngularComponentWizardIterator.componentArguments("user-card", "src/app", "web",
                DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, false);
        assertEquals(List.of("generate", "component", "user-card", "--project=web", "--path=src/app", "--interactive=false"), args);
    }

    public void testExternalTemplateStylesAndTestsAreGeneratedTogether() {
        List<String> args = AngularComponentWizardIterator.componentArguments("features/user-card", "projects/web/src/app", "web",
                "Separate file", "Separate file", "scss", "Generate", "Standalone", "OnPush", "Create subfolder", false);
        assertTrue(args.contains("--inline-template=false"));
        assertTrue(args.contains("--inline-style=false"));
        assertTrue(args.contains("--style=scss"));
        assertTrue(args.contains("--skip-tests=false"));
        assertTrue(args.contains("--standalone=true"));
        assertTrue(args.contains("--change-detection=OnPush"));
        assertTrue(args.contains("--flat=false"));
        assertFalse(args.contains("--force"));
    }

    public void testPreviewUsesTheSameOptionsWithoutWritingFiles() {
        List<String> args = AngularComponentWizardIterator.componentArguments("card", "src/app/folder with spaces", "web",
                "Inline", "Inline", "css", "Skip", "NgModule", DEFAULT, "Same folder", true);
        assertTrue(args.contains("--dry-run"));
        assertTrue(args.contains("--path=src/app/folder with spaces"));
        assertTrue(args.contains("--inline-template=true"));
        assertTrue(args.contains("--inline-style=true"));
        assertTrue(args.contains("--skip-tests=true"));
        assertTrue(args.contains("--standalone=false"));
        assertTrue(args.contains("--flat=true"));
    }

    public void testInvalidNamesAndEscapingPathsAreRejected() {
        for (String name : List.of("../card", "--help", "card;echo", "Card Name", "/card", "")) {
            assertNotNull(name, AngularComponentWizardIterator.validationError(name, "src/app"));
        }
        for (String path : List.of("../app", "src/../../app", "/tmp/app", "C:/app", "src\\app", "")) {
            assertNotNull(path, AngularComponentWizardIterator.validationError("card", path));
        }
        assertNull(AngularComponentWizardIterator.validationError("shared/user-card", "projects/web/src/app"));
    }
    public void testWizardInitializesWithWorkspaceDefaults() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("angular-component-wizard");
        try {
            java.nio.file.Files.writeString(directory.resolve("angular.json"),
                    "{\"version\":1,\"projects\":{\"web\":{\"projectType\":\"application\",\"root\":\"\",\"sourceRoot\":\"src\"}}}");
            org.openide.filesystems.FileUtil.refreshFor(directory.toFile());
            var root = org.openide.filesystems.FileUtil.toFileObject(directory.toFile());
            assertNotNull(root);
            AngularProject project = new AngularProject(root,
                    org.netbeans.modules.angular.model.AngularWorkspace.load(root),
                    new org.netbeans.spi.project.ProjectState() {
                        @Override public void markModified() { }
                        @Override public void notifyDeleted() { }
                    });
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                AngularComponentWizardIterator iterator = new AngularComponentWizardIterator(project);
                org.openide.WizardDescriptor wizard = new org.openide.WizardDescriptor(iterator);
                assertTrue(iterator.current().isValid());
                assertNotNull(iterator.current().getComponent());
                iterator.current().storeSettings(wizard);
                iterator.uninitialize(wizard);
            });
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }

}
