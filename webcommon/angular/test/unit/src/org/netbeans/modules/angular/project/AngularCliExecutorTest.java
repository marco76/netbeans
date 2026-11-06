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
public class AngularCliExecutorTest extends TestCase {
    public AngularCliExecutorTest(String name) { super(name); }
    public void testArgumentsAreNotShellExpanded() {
        File cli = new File("folder with spaces/ng.js").getAbsoluteFile();
        assertEquals(List.of("/node path/node", cli.getAbsolutePath(), "generate", "component", "a b;echo hello"),
            AngularCliExecutor.buildCommand("/node path/node", cli, List.of("generate", "component", "a b;echo hello"), false));
    }
    public void testWindowsWrapperIsRejected() {
        try {
            AngularCliExecutor.buildCommand("node.exe", new File("ng.cmd"), List.of("build"), true);
            fail("A command wrapper cannot be executed as JavaScript");
        } catch (IllegalArgumentException expected) {}
    }
    public void testNativeExecutableDoesNotUseNode() {
        File cli=new File("ng.exe");
        assertEquals(List.of(cli.getAbsolutePath(), "build"), AngularCliExecutor.buildCommand("node.exe",cli,List.of("build"),true));
    }
    public void testOnlyProjectsWithRequestedTargetAreOffered() throws Exception {
        var root = org.openide.filesystems.FileUtil.createMemoryFileSystem().getRoot();
        var json = root.createData("angular.json");
        var workspace = org.netbeans.modules.angular.model.AngularWorkspace.parse(root, json, new java.io.StringReader("""
                {"projects": {
                  "app": {"architect": {"serve": {}, "build": {}, "test": {}}},
                  "lib": {"projectType": "library", "targets": {"build": {}}}
                }}
                """));
        assertEquals(List.of("app"), AngularCliExecutor.eligibleProjects(workspace, List.of("serve")));
        assertEquals(List.of("app", "lib"), AngularCliExecutor.eligibleProjects(workspace, List.of("build")));
        assertEquals(List.of(), AngularCliExecutor.eligibleProjects(workspace, List.of("lint")));
        assertEquals(List.of("app", "lib"), AngularCliExecutor.eligibleProjects(workspace, List.of("generate", "service", "example")));
    }
}
