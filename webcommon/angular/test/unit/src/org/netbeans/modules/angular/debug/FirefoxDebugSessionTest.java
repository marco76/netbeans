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
package org.netbeans.modules.angular.debug;

import java.io.*;
import java.util.stream.Stream;
import junit.framework.TestCase;

public class FirefoxDebugSessionTest extends TestCase {
    private static final class Child extends Process {
        int stops;
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { stops++; }
        @Override public boolean isAlive() { return false; }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
    }
    public void testStopsOnlyOwnedProcessesAndIsIdempotent() {
        var session=new FirefoxDebugSession(); Child server=new Child(), adapter=new Child(), external=new Child();
        session.own(server); session.own(adapter); session.close(); session.close();
        assertEquals(1,server.stops); assertEquals(1,adapter.stops); assertEquals(0,external.stops); assertTrue(session.isClosed());
    }
    public void testCancelDuringStartupImmediatelyStopsNewProcess() {
        var session=new FirefoxDebugSession(); session.close(); Child late=new Child(); session.own(late); assertEquals(1,late.stops);
    }
}
