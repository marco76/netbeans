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
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

public class DAPProtocolTest extends TestCase {
    public void testFirefoxDoesNotRequireConfigurationDone() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        IDebugProtocolServer server=new IDebugProtocolServer() {
            @Override public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(null);
            }
        };
        Capabilities capabilities=new Capabilities();
        capabilities.setSupportsConfigurationDoneRequest(false);
        DAPProtocol.configurationDone(server,capabilities).get();
        DAPProtocol.configurationDone(server,new Capabilities()).get();
        assertEquals(0,calls.get());
        capabilities.setSupportsConfigurationDoneRequest(true);
        DAPProtocol.configurationDone(server,capabilities).get();
        assertEquals(1,calls.get());
    }
    public void testAdapterExitTriggersDisconnectCleanup() throws Exception {
        var listening=new CompletableFuture<Void>();
        var disconnected=new java.util.concurrent.CountDownLatch(1);
        DAPProtocol.watchConnection(listening,disconnected::countDown);
        listening.completeExceptionally(new java.io.IOException("adapter exited"));
        assertTrue(disconnected.await(2,java.util.concurrent.TimeUnit.SECONDS));
    }
}
