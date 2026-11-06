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
package org.netbeans.modules.lsp.client.bindings;

import org.eclipse.lsp4j.DocumentSymbolOptions;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BreadcrumbsImplTest {

    @Test
    public void caretTraversesProviderGroups() throws Exception {
        org.netbeans.editor.BaseDocument doc = new org.netbeans.editor.BaseDocument(false, "text/plain");
        doc.insertString(0, "01234567890123456789", null);
        var file = org.openide.filesystems.FileUtil.createMemoryFileSystem().getRoot().createData("app.ts");
        var symbol = new org.eclipse.lsp4j.DocumentSymbol("Component", org.eclipse.lsp4j.SymbolKind.Class,
                new org.eclipse.lsp4j.Range(new org.eclipse.lsp4j.Position(0, 2), new org.eclipse.lsp4j.Position(0, 10)),
                new org.eclipse.lsp4j.Range(new org.eclipse.lsp4j.Position(0, 2), new org.eclipse.lsp4j.Position(0, 3)));
        var element = new BreadcrumbsImpl.BreadcrumbsElementImpl(null, file, doc, symbol);
        var providers = group(group(), group(element));
        org.junit.Assert.assertSame(element, BreadcrumbsImpl.findSymbol(providers, 5));
        org.junit.Assert.assertSame(element, BreadcrumbsImpl.findSymbol(providers, 2));
        org.junit.Assert.assertSame(element, BreadcrumbsImpl.findSymbol(providers, 10));
        org.junit.Assert.assertNull(BreadcrumbsImpl.findSymbol(providers, 15));
        org.junit.Assert.assertSame(element, BreadcrumbsImpl.findSymbol(group(element), 5));
    }

    private static org.netbeans.modules.editor.breadcrumbs.spi.BreadcrumbsElement group(
            org.netbeans.modules.editor.breadcrumbs.spi.BreadcrumbsElement... children) {
        return new org.netbeans.modules.editor.breadcrumbs.spi.BreadcrumbsElement() {
            public String getHtmlDisplayName() { return "Provider"; }
            public java.awt.Image getIcon(int type) { return null; }
            public java.awt.Image getOpenedIcon(int type) { return null; }
            public java.util.List<org.netbeans.modules.editor.breadcrumbs.spi.BreadcrumbsElement> getChildren() {
                return java.util.List.of(children);
            }
            public org.openide.util.Lookup getLookup() { return org.openide.util.Lookup.EMPTY; }
            public org.netbeans.modules.editor.breadcrumbs.spi.BreadcrumbsElement getParent() { return null; }
        };
    }

    @Test
    public void optionalServerInfo() {
        InitializeResult result = new InitializeResult(new ServerCapabilities());
        assertEquals("app.ts", BreadcrumbsImpl.providerDisplayName(result, "app.ts"));
        result.setServerInfo(new ServerInfo("Angular"));
        assertEquals("Angular", BreadcrumbsImpl.providerDisplayName(result, "app.ts"));
        result.setServerInfo(new ServerInfo(" "));
        assertEquals("app.ts", BreadcrumbsImpl.providerDisplayName(result, "app.ts"));
    }

    @Test
    public void symbolLabelTakesPrecedence() {
        ServerCapabilities capabilities = new ServerCapabilities();
        DocumentSymbolOptions options = new DocumentSymbolOptions();
        capabilities.setDocumentSymbolProvider(options);
        InitializeResult result = new InitializeResult(capabilities);
        result.setServerInfo(new ServerInfo("Angular"));
        assertEquals("Angular", BreadcrumbsImpl.providerDisplayName(result, "app.ts"));
        options.setLabel("Symbols");
        assertEquals("Symbols", BreadcrumbsImpl.providerDisplayName(result, "app.ts"));
        result.setServerInfo(null);
        assertEquals("Symbols", BreadcrumbsImpl.providerDisplayName(result, "app.ts"));
    }
}
