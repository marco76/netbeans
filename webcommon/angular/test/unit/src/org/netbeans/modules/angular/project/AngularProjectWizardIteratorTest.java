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

import java.io.File;
import java.util.List;
import junit.framework.TestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class AngularProjectWizardIteratorTest extends TestCase {
    public AngularProjectWizardIteratorTest(String name) { super(name); }

    private File workDir;

    @Override protected void setUp() throws Exception {
        workDir = Files.createTempDirectory("angular-wizard-test").toFile();
    }

    @Override protected void tearDown() throws Exception {
        try (var paths = Files.walk(workDir.toPath())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private File getWorkDir() { return workDir; }

    public void testEmptyDestinationCanBeRetriedButExistingFilesAreProtected() throws Exception {
        File parent = getWorkDir();
        assertNull(AngularProjectWizardIterator.destinationError("my-app", parent));
        assertTrue(new File(parent, "my-app").mkdir());
        assertNull(AngularProjectWizardIterator.destinationError("my-app", parent));
        Files.writeString(new File(parent, "my-app/keep.txt").toPath(), "existing content");
        assertNotNull(AngularProjectWizardIterator.destinationError("my-app", parent));
        assertNotNull(AngularProjectWizardIterator.destinationError("other-app", new File(parent, "missing")));
    }

    public void testNameCannotEscapeDestinationOrBecomeAnOption() throws Exception {
        for (String name : List.of("../app", "/tmp/app", "--help", "app/name", "app;echo", "My App", "", "test")) {
            assertNotNull(name, AngularProjectWizardIterator.destinationError(name, getWorkDir()));
        }
        assertTrue(AngularProjectWizardIterator.validName("my-app2"));
    }

    public void testCommandIsNonInteractiveAndPreservesSpaces() throws Exception {
        File directory = new File(getWorkDir(), "parent with spaces/my-app");
        List<String> args = AngularProjectWizardIterator.newProjectArguments("my-app", directory, "scss", true, false, true);
        assertEquals("new", args.get(0));
        assertEquals("my-app", args.get(1));
        assertTrue(args.contains("--directory=" + directory.getName()));
        assertFalse(args.contains("--directory=" + directory.getAbsolutePath()));
        assertTrue(args.contains("--style=scss"));
        assertTrue(args.contains("--routing=true"));
        assertTrue(args.contains("--ssr=false"));
        assertTrue(args.contains("--skip-install=false"));
        assertTrue(args.contains("--interactive=false"));
        assertTrue(args.contains("--skip-git=true"));
    }

    public void testOptionalFeaturesAndOfflineGeneration() throws Exception {
        List<String> args = AngularProjectWizardIterator.newProjectArguments("app", getWorkDir(), "css", false, true, false);
        assertTrue(args.contains("--routing=false"));
        assertTrue(args.contains("--ssr=true"));
        assertTrue(args.contains("--skip-install=true"));
    }

    public void testCliVersionResolvesSymlinkAndChecksPackageIdentity() throws Exception {
        Path cli = workDir.toPath().resolve("cli/bin/ng.js");
        Files.createDirectories(cli.getParent());
        Files.writeString(cli, "// test entry");
        Path manifest = cli.getParent().getParent().resolve("package.json");
        Files.writeString(manifest, "{\"name\":\"@angular/cli\",\"version\":\"21.2.6\"}");
        Path link = workDir.toPath().resolve("ng");
        Files.createSymbolicLink(link, cli);
        assertEquals("21.2.6", AngularProjectWizardIterator.cliVersion(link.toFile()));
        Files.writeString(manifest, "{\"name\":\"unrelated\",\"version\":\"1.0.0\"}");
        assertNull(AngularProjectWizardIterator.cliVersion(link.toFile()));
        assertNull(AngularProjectWizardIterator.cliVersion(null));
    }

    public void testMaterialMatchesFrameworkMajorAndUsesSelectedTheme() throws Exception {
        Files.writeString(workDir.toPath().resolve("package.json"),
                "{\"dependencies\":{\"@angular/core\":\"^21.2.0\"}}");
        List<String> args = AngularProjectWizardIterator.materialArguments(workDir, "rose-red");
        assertEquals(List.of("add", "@angular/material@^21", "--skip-confirmation",
                "--interactive=false", "--theme=rose-red"), args);
        assertFalse(AngularProjectWizardIterator.materialArguments(workDir, "CLI default")
                .stream().anyMatch(arg -> arg.startsWith("--theme=")));
    }

    public void testMaterialDoesNotGuessAnUnknownFrameworkVersion() throws Exception {
        Files.writeString(workDir.toPath().resolve("package.json"),
                "{\"dependencies\":{\"@angular/core\":\"latest\"}}");
        try {
            AngularProjectWizardIterator.materialArguments(workDir, "CLI default");
            fail("Do not silently install an incompatible Material version");
        } catch (java.io.IOException expected) { }
    }

    public void testModernMaterialThemeIsRejectedForOlderAngular() throws Exception {
        Files.writeString(workDir.toPath().resolve("package.json"),
                "{\"dependencies\":{\"@angular/core\":\"~17.3.0\"}}");
        assertTrue(AngularProjectWizardIterator.materialArguments(workDir, "CLI default").contains("@angular/material@^17"));
        try {
            AngularProjectWizardIterator.materialArguments(workDir, "azure-blue");
            fail("Modern theme cannot be used with Angular 17");
        } catch (java.io.IOException expected) { }
    }
}
