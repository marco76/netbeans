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

import java.util.ArrayList;
import java.util.Collection;
import org.netbeans.api.project.Project;
import org.netbeans.modules.fullstack.spi.EndpointProvider;
import org.openide.util.lookup.ServiceProvider;

/** Adapts the existing Spring/Micronaut endpoint index to the connection view. */
@ServiceProvider(service = EndpointProvider.class)
public final class FullStackEndpointProvider implements EndpointProvider {
    @Override public Collection<Endpoint> findEndpoints(Project project) {
        Collection<Endpoint> endpoints = new ArrayList<>();
        for (MicronautSymbolSearcher.SymbolDescriptor symbol : MicronautSymbolSearcher.getSymbols(project, "@/")) {
            Endpoint endpoint = toEndpoint(symbol.getSimpleName(), symbol.getFileObject(), symbol::open);
            if (endpoint != null) endpoints.add(endpoint);
        }
        return endpoints;
    }
    static Endpoint toEndpoint(String name, org.openide.filesystems.FileObject file, Runnable navigate) {
        String[] route = name.split(" -- ", 2);
        if (route.length != 2 || !route[0].startsWith("@") || file == null) return null;
        return new Endpoint(route[1], route[0].substring(1), file, navigate);
    }
}
