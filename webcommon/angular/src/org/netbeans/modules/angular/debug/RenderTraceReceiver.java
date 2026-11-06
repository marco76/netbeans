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
import java.util.*;
import java.util.concurrent.*;

/** Session-only loopback collector. A random endpoint and exact origin gate each POST. */
public final class RenderTraceReceiver implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final String path="/events/"+UUID.randomUUID();
    public RenderTraceReceiver(String applicationOrigin,RenderTrace trace) throws IOException {
        this(applicationOrigin,trace,null);
    }
    public RenderTraceReceiver(String applicationOrigin,RenderTrace trace,BrowserCommands commands) throws IOException {
        URI origin;
        try { origin=URI.create(applicationOrigin.trim()); }
        catch (IllegalArgumentException ex) { throw new IOException("Enter the application's origin, for example http://localhost:4200",ex); }
        if (!Set.of("http","https").contains(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
                || origin.getQuery()!=null || origin.getFragment()!=null || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))) {
            throw new IOException("Use the application's origin without a route, query or fragment.");
        }
        String allowed=origin.getScheme()+"://"+origin.getRawAuthority();
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
        executor=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"Angular render trace"); t.setDaemon(true); return t; });
        server.setExecutor(executor);
        server.createContext(path, exchange -> receive(exchange,path,allowed,trace,commands,endpoint()));
        server.start();
    }
    static void receive(HttpExchange exchange,String path,String allowed,RenderTrace trace) throws IOException {
        receive(exchange,path,allowed,trace,null,"");
    }
    static void receive(HttpExchange exchange,String path,String allowed,RenderTrace trace,BrowserCommands commands,String endpoint) throws IOException {
        try (exchange) {
            String requestPath=exchange.getRequestURI().getPath();
            boolean extra=commands!=null && (requestPath.equals(path+"/commands") || requestPath.equals(path+"/picker.js"));
            if ((!requestPath.equals(path) && !extra) || !allowed.equals(exchange.getRequestHeaders().getFirst("Origin"))) {
                exchange.sendResponseHeaders(403,-1); return;
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin",allowed);
            exchange.getResponseHeaders().set("Vary","Origin");
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods","GET, POST");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
                exchange.sendResponseHeaders(204,-1); return;
            }
            if (extra && exchange.getRequestMethod().equals("GET")) {
                String body=requestPath.endsWith("/commands") ? commands.drain() : pickerScript(endpoint);
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",requestPath.endsWith(".js") ? "text/javascript; charset=utf-8" : "application/json");
                exchange.getResponseHeaders().set("Cache-Control","no-store");
                exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); return;
            }
            if (extra || !exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405,-1); return; }
            byte[] data=exchange.getRequestBody().readNBytes(256*1024+1);
            if (data.length>256*1024) { exchange.sendResponseHeaders(413,-1); return; }
            try { trace.add(RenderTrace.parse(new String(data,StandardCharsets.UTF_8))); exchange.sendResponseHeaders(204,-1); }
            catch (IOException ex) { exchange.sendResponseHeaders(400,-1); }
        }
    }
    public static String pickerScript(String endpoint) throws IOException {
        try (InputStream in=RenderTraceReceiver.class.getResourceAsStream("/org/netbeans/modules/angular/resources/component-picker.js")) {
            if (in==null) throw new IOException("Component picker resource is missing.");
            return new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("__ALP_ENDPOINT__",new com.google.gson.Gson().toJson(endpoint));
        }
    }
    public String endpoint() { return "http://127.0.0.1:"+server.getAddress().getPort()+path; }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
