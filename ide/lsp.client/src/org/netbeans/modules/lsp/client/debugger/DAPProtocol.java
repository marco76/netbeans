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
package org.netbeans.modules.lsp.client.debugger;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

final class DAPProtocol {
    private DAPProtocol() {}
    static void watchConnection(java.util.concurrent.Future<Void> listening,Runnable disconnected) {
        Thread monitor=new Thread(() -> {
            try { listening.get(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException ex) {
                java.util.logging.Logger.getLogger(DAPProtocol.class.getName()).log(java.util.logging.Level.FINE,"Debug adapter connection ended",ex);
            } finally { disconnected.run(); }
        },"DAP connection monitor");
        monitor.setDaemon(true); monitor.start();
    }
    static CompletableFuture<Void> configurationDone(IDebugProtocolServer server, Capabilities capabilities) {
        return capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsConfigurationDoneRequest())
                ? server.configurationDone(new ConfigurationDoneArguments()) : CompletableFuture.completedFuture(null);
    }
}
