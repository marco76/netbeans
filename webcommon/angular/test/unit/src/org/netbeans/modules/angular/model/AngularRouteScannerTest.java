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

import junit.framework.TestCase;
import org.openide.filesystems.*;

public class AngularRouteScannerTest extends TestCase {
    public void testNestedRoutesAndNavigation() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject component = root.createData("shell.ts");
        FileObject file = root.createData("app.routes.ts");
        var routes = AngularRouteScanner.parse(file, """
            import { Shell as Layout } from './shell';
            import { Routes } from '@angular/router';
            // { path: 'ignored' }
            export const routes: Routes = [
              { path: '', component: Layout, canActivate: [authGuard], children: [
                { path: 'users/:id', loadComponent: () => import('./user').then(m => m.User) },
                { path: 'old', redirectTo: 'users' }
              ]},
              { path: '**', redirectTo: '' }
            ];
            """);
        assertEquals(2, routes.size());
        assertEquals("", routes.get(0).path());
        assertEquals(component, routes.get(0).component());
        assertEquals(4, routes.get(0).line());
        assertTrue(routes.get(0).detail().contains("authGuard"));
        assertEquals(2, routes.get(0).children().size());
        assertEquals("users/:id", routes.get(0).children().get(0).path());
        assertTrue(routes.get(0).children().get(0).detail().contains("lazy:"));
        assertEquals("**", routes.get(1).path());
    }
    public void testCommentsStringsAndEmptyPaths() throws Exception {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData("routes.ts");
        var routes = AngularRouteScanner.parse(file, """
            /* { path: 'fake' } */
            const text = "{ path: 'also-fake' }";
            RouterModule.forRoot([{path: '', children: [{path: 'home', data: { title: '} , [' }}]}]);
            """);
        assertEquals(1, routes.size());
        assertEquals("home", routes.get(0).children().get(0).path());
    }
    public void testLazyRouteExpansion() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject app = root.createData("app.routes.ts");
        FileObject lazy = root.createData("admin.routes.ts");
        FileObject component = root.createData("page.ts");
        try (var writer = new java.io.OutputStreamWriter(app.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("import { Routes } from '@angular/router'; export const routes: Routes = [{path: 'admin', loadChildren: () => import('./admin.routes').then(m => m.routes)}];");
        }
        try (var writer = new java.io.OutputStreamWriter(lazy.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("import { Routes } from '@angular/router'; export const routes: Routes = [{path: ':id', loadComponent: () => import('./page').then(m => m.Page)}];");
        }
        var routes = AngularRouteScanner.scan(root);
        assertEquals(1, routes.size());
        assertEquals("admin", routes.get(0).path());
        assertEquals(1, routes.get(0).children().size());
        assertEquals(":id", routes.get(0).children().get(0).path());
        assertEquals(component, routes.get(0).children().get(0).component());
    }
}
