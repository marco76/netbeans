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
package org.netbeans.modules.angular.model;
import java.io.StringReader;
import junit.framework.TestCase;
import org.openide.filesystems.*;
import org.netbeans.modules.angular.project.AngularProjectFactory;
public class AngularWorkspaceTest extends TestCase {
    public AngularWorkspaceTest(String name) { super(name); }
    public void testMultiProjectWorkspaceAndTargets() throws Exception {
        FileObject root=FileUtil.createMemoryFileSystem().getRoot();
        FileObject json=root.createData("angular.json");
        FileUtil.createFolder(root,"projects/app/src");
        AngularWorkspace ws=AngularWorkspace.parse(root,json,new StringReader("""
            {"version":1,"defaultProject":"app","cli":{"packageManager":"pnpm"},"projects":{
              "app":{"root":"projects/app","sourceRoot":"projects/app/src","targets":{"build":{"builder":"example:build"},"test":{"builder":"example:test"}}},
              "lib":{"root":"projects/lib","projectType":"library"}}}
            """));
        assertEquals(2,ws.getProjects().size());
        assertEquals("app",ws.getDefaultProjectName());
        assertEquals("pnpm",ws.getPackageManager());
        assertTrue(ws.getAvailableArchitectTargets().contains("test"));
        assertTrue(ws.getSourceRoots().contains(root.getFileObject("projects/app/src")));
    }
    public void testProjectFactoryDoesNotClaimNestedFolder() throws Exception {
        FileObject root=FileUtil.createMemoryFileSystem().getRoot();
        root.createData("angular.json");
        FileObject nested=FileUtil.createFolder(root,"src/app");
        AngularProjectFactory factory=new AngularProjectFactory();
        assertTrue(factory.isProject(root));
        assertFalse(factory.isProject(nested));
        assertEquals(root,AngularWorkspace.findWorkspaceRoot(nested));
    }
    public void testSelectorOffsets() throws Exception {
        FileObject file=FileUtil.createMemoryFileSystem().getRoot().createData("hello.component.ts");
        String source="@Component({selector: 'app-hello', template: ''}) export class HelloComponent {}";
        var artifacts=AngularComponentScanner.parseTypeScriptContent(file,source);
        assertEquals(1,artifacts.size());
        var artifact=artifacts.get(0);
        assertEquals("app-hello",source.substring(artifact.getSelectorStartOffset(),artifact.getSelectorEndOffset()));
    }
}
