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
package org.netbeans.modules.angular.project;

import java.net.URI;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

public class AngularDebugServerTest extends TestCase {
    public void testServeArgumentsKeepApplicationAndPortTogether() {
        var args=AngularDebugServer.arguments("my app",URI.create("http://localhost:4300/products"));
        assertTrue(args.contains("--project=my app")); assertTrue(args.contains("--port=4300"));
        assertTrue(AngularDebugServer.arguments("app",URI.create("http://localhost:4200/"),"development").contains("--configuration=development"));
        assertTrue(args.contains("--host=localhost")); assertTrue(args.contains("--open=false"));
    }
    public void testAutomaticStartupRejectsRemoteAndHttpsUrls() {
        for (String url : new String[]{"https://localhost:4200/","http://remote.example:4200/"}) {
            try { AngularDebugServer.arguments("app",URI.create(url)); fail(); } catch (IllegalArgumentException expected) {}
        }
    }
    public void testWaitRequiresLiveServerAndHandlesFailureTimeoutAndCancel() throws Exception {
        AtomicInteger checks=new AtomicInteger();
        AngularDebugServer.await(() -> { checks.incrementAndGet(); return true; },() -> true,() -> false,1000);
        assertEquals(1,checks.get());
        try { AngularDebugServer.await(() -> true,() -> false,() -> false,1000); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("exited")); }
        try { AngularDebugServer.await(() -> false,() -> true,() -> false,0); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("in time")); }
        try { AngularDebugServer.await(() -> true,() -> true,() -> true,1000); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("canceled")); }
    }
}
