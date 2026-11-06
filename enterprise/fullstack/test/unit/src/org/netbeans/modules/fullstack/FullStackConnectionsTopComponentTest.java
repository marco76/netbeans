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
package org.netbeans.modules.fullstack;

import junit.framework.TestCase;

public class FullStackConnectionsTopComponentTest extends TestCase {
    public void testBackendOnlyRow() throws Exception {
        org.openide.filesystems.FileObject file = org.openide.filesystems.FileUtil.createMemoryFileSystem()
                .getRoot().createData("ProductController.java");
        Object[] row = FullStackConnectionsTopComponent.backendRow(
                new org.netbeans.modules.fullstack.spi.EndpointProvider.Endpoint("GET", "/api/products", file, () -> {}));
        assertEquals("", row[0]);
        assertEquals("GET /api/products", row[1]);
        assertEquals("ProductController.java", row[2]);
        assertEquals("No detected Angular call", row[3]);
    }

    public void testLiteralRouteAndQuery() {
        assertTrue(FullStackConnectionsTopComponent.matches("GET", "/api/products?sort=name", "GET", "/api/products"));
    }
    public void testPathVariable() {
        assertTrue(FullStackConnectionsTopComponent.matches("GET", "/api/products/42", "GET", "/api/products/{id}"));
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "/api/products/", "GET", "/api/products/{id}"));
    }
    public void testMethodMismatch() {
        assertFalse(FullStackConnectionsTopComponent.matches("POST", "/api/products", "GET", "/api/products"));
        assertTrue(FullStackConnectionsTopComponent.matches("POST", "/api/products", "GET, POST", "/api/products"));
    }
    public void testUnresolvedBaseUrl() {
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "${base}/api/products", "GET", "/api/products"));
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "https://example.com/api/products", "GET", "/api/products"));
    }
    public void testTemplatePathSegments() {
        assertTrue(FullStackConnectionsTopComponent.matches("GET", "/api/products/${id}", "GET", "/api/products/{id}"));
        assertTrue(FullStackConnectionsTopComponent.matches("GET", "/api/products/${product.id}", "GET", "/api/products/{id}"));
        assertTrue(FullStackConnectionsTopComponent.matches("GET", "/api/${category}/products/${id}", "GET", "/api/{category}/products/{id}"));
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "/api/products/${id}", "GET", "/api/products/latest"));
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "/api/products/prefix-${id}", "GET", "/api/products/{id}"));
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "/api/products/${resolveId()}", "GET", "/api/products/{id}"));
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "/${base}/products", "GET", "/api/products"));
    }
    public void testDifferentRoute() {
        assertFalse(FullStackConnectionsTopComponent.matches("GET", "/api/product", "ANY", "/api/products"));
    }
}
