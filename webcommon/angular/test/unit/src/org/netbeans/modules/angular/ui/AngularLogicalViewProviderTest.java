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
package org.netbeans.modules.angular.ui;

import junit.framework.TestCase;

public class AngularLogicalViewProviderTest extends TestCase {
    public void testGroupsFollowApplicationFilesAndKeepFileLookups() throws Exception {
        var fs = org.openide.filesystems.FileUtil.createMemoryFileSystem();
        var root = fs.getRoot();
        var entries = new org.openide.nodes.Children.Array();
        for (String name : new String[]{".angular", ".vscode", "node_modules", "public", "src", "dist", ".gitignore", "angular.json"}) {
            var file = name.contains("json") || name.equals(".gitignore")
                    ? root.createData(name) : root.createFolder(name);
            var node = new org.openide.nodes.AbstractNode(org.openide.nodes.Children.LEAF,
                    org.openide.util.lookup.Lookups.singleton(file));
            node.setName(name);
            entries.add(new org.openide.nodes.Node[]{node});
        }
        var folder = new org.openide.nodes.AbstractNode(entries);
        var view = new org.openide.nodes.AbstractNode(new AngularLogicalViewProvider.WorkspaceChildren(folder));
        var nodes = view.getChildren().getNodes(true);
        assertEquals(6, nodes.length);
        assertEquals("public", nodes[0].getName());
        assertEquals("src", nodes[1].getName());
        assertEquals("angular.json", nodes[2].getName());
        assertEquals("Public Assets", nodes[0].getDisplayName());
        assertEquals("Sources", nodes[1].getDisplayName());
        assertEquals("Build Output", nodes[3].getDisplayName());
        assertEquals(root.getFileObject("dist"), nodes[3].getChildren().getNodes(true)[0]
                .getLookup().lookup(org.openide.filesystems.FileObject.class));
        assertEquals("Dependencies", nodes[4].getDisplayName());
        assertEquals("Hidden & Tooling", nodes[5].getDisplayName());
        var dependencies = nodes[4].getChildren().getNodes(true);
        assertEquals(1, dependencies.length);
        assertEquals(root.getFileObject("node_modules"),
                dependencies[0].getLookup().lookup(org.openide.filesystems.FileObject.class));
        assertEquals(3, nodes[5].getChildren().getNodes(true).length);
        entries.remove(new org.openide.nodes.Node[]{entries.getNodes()[2]});
        assertEquals(5, view.getChildren().getNodes(true).length);
    }

    public void testFileRolesIncludingModernAngularNames() throws Exception {
        var root = org.openide.filesystems.FileUtil.createMemoryFileSystem().getRoot();
        root.createData("app.html");
        String[][] cases = {
            {"app.ts", "component"}, {"hero.component.ts", "component"},
            {"app.config.ts", "config"}, {"app.routes.ts", "routes"},
            {"app-routing.module.ts", "routes"}, {"app.spec.ts", "test"},
            {"util.test.ts", "test"}, {"util.ts", "typescript"},
            {"index.html", "html"}, {"app.scss", "sass"}
        };
        for (String[] entry : cases) {
            assertEquals(entry[0], entry[1], AngularLogicalViewProvider.fileIcon(root.createData(entry[0])));
        }
        assertEquals("folder", AngularLogicalViewProvider.fileIcon(root.createFolder("app")));
        assertNull(AngularLogicalViewProvider.fileIcon(root.createData("unknown.bin")));
    }

    public void testCompactStableVersions() {
        assertEquals("21.2", AngularLogicalViewProvider.compactVersion("^21.2.0"));
        assertEquals("21.2", AngularLogicalViewProvider.compactVersion("~21.2.3"));
        assertEquals("21.2", AngularLogicalViewProvider.compactVersion("21.2.7"));
        assertEquals("21.2", AngularLogicalViewProvider.compactVersion("21.2"));
    }

    public void testRangesTagsAndPrereleasesStayAccurate() {
        for (String version : new String[]{">=20 <22", "^20.0.0 || ^21.0.0", "next",
                "workspace:*", "21.2.0-next.1", "*", "21.x"}) {
            assertEquals(version, AngularLogicalViewProvider.compactVersion(version));
        }
    }
}
