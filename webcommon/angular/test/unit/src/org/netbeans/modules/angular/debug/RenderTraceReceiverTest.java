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

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import junit.framework.TestCase;

public class RenderTraceReceiverTest extends TestCase {
    private static final class Exchange extends HttpExchange {
        final Headers request=new Headers(), response=new Headers();
        final String method, path; final byte[] body; int status;
        Exchange(String origin,String method,String path,String body) {
            request.set("Origin",origin); this.method=method; this.path=path;
            this.body=body.getBytes(StandardCharsets.UTF_8);
        }
        @Override public Headers getRequestHeaders() { return request; }
        @Override public Headers getResponseHeaders() { return response; }
        @Override public URI getRequestURI() { return URI.create(path); }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() {}
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(body); }
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        @Override public OutputStream getResponseBody() { return output; }
        @Override public void sendResponseHeaders(int code,long length) { status=code; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name,Object value) {}
        @Override public void setStreams(InputStream in,OutputStream out) {}
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
    private Exchange send(RenderTrace trace,String origin,String method,String path,String body) throws Exception {
        Exchange exchange=new Exchange(origin,method,path,body);
        RenderTraceReceiver.receive(exchange,"/events/token","http://localhost:4200",trace);
        return exchange;
    }
    public void testOriginEndpointAndPayloadValidation() throws Exception {
        RenderTrace trace=new RenderTrace();
        assertEquals(204,send(trace,"http://localhost:4200","OPTIONS","/events/token","").status);
        assertEquals(403,send(trace,"http://untrusted.invalid","POST","/events/token",RenderTraceTest.batch()).status);
        assertEquals(403,send(trace,"http://localhost:4200","POST","/events/wrong",RenderTraceTest.batch()).status);
        assertEquals(400,send(trace,"http://localhost:4200","POST","/events/token","{}").status);
        Exchange accepted=send(trace,"http://localhost:4200","POST","/events/token",RenderTraceTest.batch());
        assertEquals(204,accepted.status);
        assertEquals("http://localhost:4200",accepted.response.getFirst("Access-Control-Allow-Origin"));
        assertEquals(1,trace.events().size());
    }
    public void testOversizedBodiesAndOtherMethods() throws Exception {
        RenderTrace trace=new RenderTrace();
        assertEquals(413,send(trace,"http://localhost:4200","POST","/events/token","x".repeat(256*1024+1)).status);
        assertEquals(405,send(trace,"http://localhost:4200","GET","/events/token","").status);
        assertTrue(trace.events().isEmpty());
    }
    public void testPickerEndpointsUseOriginAndSessionToken() throws Exception {
        RenderTrace trace=new RenderTrace(); BrowserCommands commands=new BrowserCommands(); commands.pick();
        Exchange poll=new Exchange("http://localhost:4200","GET","/events/token/commands","");
        RenderTraceReceiver.receive(poll,"/events/token","http://localhost:4200",trace,commands,"http://127.0.0.1:1234/events/token");
        assertEquals(200,poll.status); assertTrue(poll.output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("pick"));
        assertEquals("[]",commands.drain());
        Exchange wrong=new Exchange("http://other.invalid","GET","/events/token/commands","");
        RenderTraceReceiver.receive(wrong,"/events/token","http://localhost:4200",trace,commands,"unused"); assertEquals(403,wrong.status);
        Exchange script=new Exchange("http://localhost:4200","GET","/events/token/picker.js","");
        RenderTraceReceiver.receive(script,"/events/token","http://localhost:4200",trace,commands,"http://127.0.0.1:1234/events/token");
        assertEquals(200,script.status);
        String code=script.output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(code.contains("__ALP_ENDPOINT__")); assertTrue(code.contains("http://127.0.0.1:1234/events/token"));
    }
}
