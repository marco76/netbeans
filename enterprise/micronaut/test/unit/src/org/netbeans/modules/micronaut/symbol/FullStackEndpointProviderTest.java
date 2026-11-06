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
package org.netbeans.modules.micronaut.symbol;

import junit.framework.TestCase;
import org.netbeans.modules.fullstack.spi.EndpointProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class FullStackEndpointProviderTest extends TestCase {
    public void testIndexNamePreservesPathMethodsAndNavigation() throws Exception {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData("Controller.java");
        boolean[] opened = {false};
        EndpointProvider.Endpoint endpoint = FullStackEndpointProvider.toEndpoint(
                "@/api/products/{id} -- GET, POST", file, () -> opened[0] = true);
        assertNotNull(endpoint);
        assertEquals("/api/products/{id}", endpoint.path());
        assertEquals("GET, POST", endpoint.methods());
        assertSame(file, endpoint.file());
        endpoint.navigate().run();
        assertTrue(opened[0]);
    }
    public void testNonRouteSymbolsAndMissingSourcesAreIgnored() throws Exception {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData("Controller.java");
        assertNull(FullStackEndpointProvider.toEndpoint("bean", file, () -> {}));
        assertNull(FullStackEndpointProvider.toEndpoint("path -- GET", file, () -> {}));
        assertNull(FullStackEndpointProvider.toEndpoint("@/api -- GET", null, () -> {}));
    }
}
