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

import java.lang.reflect.Proxy;
import junit.framework.TestCase;
import org.netbeans.modules.html.editor.lib.api.HtmlSource;
import org.netbeans.modules.html.editor.lib.api.elements.Named;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class AngularHtmlExtensionTest extends TestCase {
    public void testAngularIndexAndNestedTemplates() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        root.createData("angular.json");
        AngularHtmlExtension extension = new AngularHtmlExtension();
        for (String path : new String[]{"src/index.html", "projects/demo/src/app/app.html"}) {
            HtmlSource source = new HtmlSource(FileUtil.createData(root, path));
            assertTrue(extension.isCustomTag(tag("app-root"), source));
            assertTrue(extension.isCustomTag(tag("mat-card"), source));
            assertTrue(extension.isCustomTag(tag("ng-container"), source));
            assertFalse(extension.isCustomTag(tag("body"), source));
            assertFalse(extension.isCustomTag(tag("divv"), source));
        }
    }

    public void testPlainHtmlAndMissingFile() throws Exception {
        AngularHtmlExtension extension = new AngularHtmlExtension();
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        assertFalse(extension.isCustomTag(tag("app-root"),
                new HtmlSource(root.createData("index.html"))));
        assertFalse(extension.isCustomTag(tag("app-root"), new HtmlSource("<app-root>")));
    }

    private static Named tag(String name) {
        return (Named) Proxy.newProxyInstance(Named.class.getClassLoader(),
                new Class<?>[]{Named.class}, (proxy, method, args) -> {
                    if (method.getName().equals("name")) return name;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
