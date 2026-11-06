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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import junit.framework.TestCase;

public class AngularOptionsTest extends TestCase {
    private Path root;
    @Override protected void setUp() throws Exception { root = Files.createTempDirectory("angular-server-test"); }
    @Override protected void tearDown() throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private Path file(String path) throws Exception {
        Path result = root.resolve(path);
        Files.createDirectories(result.getParent());
        return Files.writeString(result, "// fixture");
    }
    public void testCliIsNeverSelectedAsLanguageServer() throws Exception {
        file("node_modules/@angular/cli/bin/ng.js");
        file("node_modules/.bin/ng");
        assertNull(AngularOptions.findLocalLanguageServer(root.toFile()));
    }
    public void testHoistedServerIsFoundEvenWhenWorkspaceHasCli() throws Exception {
        Path server = file("node_modules/@angular/language-server/index.js");
        file("app/node_modules/@angular/cli/bin/ng.js");
        assertEquals(server.toFile(), AngularOptions.findLocalLanguageServer(root.resolve("app").toFile()));
    }
    public void testLocalServerTakesPrecedenceOverHoistedServer() throws Exception {
        file("node_modules/@angular/language-server/index.js");
        Path local = file("app/node_modules/@angular/language-server/index.js");
        assertEquals(local.toFile(), AngularOptions.findLocalLanguageServer(root.resolve("app").toFile()));
    }
    public void testPackageBinEntryIsSupported() throws Exception {
        Path server = file("node_modules/@angular/language-server/bin/ngserver");
        assertEquals(server.toFile(), AngularOptions.findLocalLanguageServer(root.toFile()));
        assertNull(AngularOptions.findLocalLanguageServer(null));
    }
}
