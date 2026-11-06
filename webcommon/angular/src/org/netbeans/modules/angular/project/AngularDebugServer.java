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

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.netbeans.modules.angular.lsp.AngularOptions;
import org.openide.filesystems.FileUtil;
import org.openide.util.Utilities;

/** Starts only the selected serve target; ownership is managed by the debug session. */
public final class AngularDebugServer {
    private AngularDebugServer() {}
    public static List<String> projects(AngularProject project) {
        return AngularCliExecutor.eligibleProjects(project.getWorkspace(),List.of("serve"));
    }
    public static List<String> arguments(String project,URI url) {
        if (!"http".equals(url.getScheme()) || !Set.of("localhost","127.0.0.1","[::1]","::1").contains(url.getHost())) {
            throw new IllegalArgumentException("Automatic startup needs an HTTP localhost URL. Disable startup to use another server.");
        }
        if (project==null || project.isBlank()) throw new IllegalArgumentException("Select an Angular project with a serve target.");
        return List.of("serve","--project="+project,"--host="+url.getHost().replace("[", "").replace("]", ""),
                "--port="+(url.getPort()<0 ? 80 : url.getPort()),"--open=false");
    }
    public static List<String> arguments(String project,URI url,String configuration) {
        var result=new ArrayList<>(arguments(project,url));
        if (configuration!=null && !configuration.isBlank()) result.add("--configuration="+configuration);
        return result;
    }
    public static Process start(AngularProject project,String name,URI url,String configuration) throws IOException {
        var options=AngularOptions.getInstance();
        String node=options.resolveNodeExecutable(project);
        File cli=options.resolveNgCli(project.getProjectDirectory());
        if (node==null || cli==null) throw new IOException("Configure Node.js and the Angular CLI in Angular Settings.");
        var application=project.getWorkspace().getProjects().get(name);
        if (application==null || !application.getTargets().containsKey("serve")) throw new IOException("The selected Angular application no longer has a serve target. Update Firefox Debug Settings.");
        if (!configuration.isBlank() && !application.getTargets().get("serve").getConfigurations().contains(configuration)) throw new IOException("The selected serve configuration no longer exists. Update Firefox Debug Settings.");
        var command=AngularCliExecutor.buildCommand(node,cli,arguments(name,url,configuration),Utilities.isWindows());
        ProcessBuilder builder=new ProcessBuilder(command).directory(FileUtil.toFile(project.getProjectDirectory()));
        builder.redirectErrorStream(true);
        builder.environment().put("PATH",new File(node).getParent()+File.pathSeparator+builder.environment().getOrDefault("PATH", ""));
        return builder.start();
    }
    public static boolean ready(URI url) {
        try {
            HttpURLConnection connection=(HttpURLConnection)url.toURL().openConnection();
            connection.setConnectTimeout(500); connection.setReadTimeout(500); connection.setInstanceFollowRedirects(false);
            try { int status=connection.getResponseCode(); return status>=200 && status<400; }
            finally { connection.disconnect(); }
        } catch (IOException ex) { return false; }
    }
    public static void await(BooleanSupplier ready,BooleanSupplier alive,BooleanSupplier canceled,long timeoutMillis) throws IOException,InterruptedException {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!canceled.getAsBoolean()) {
            if (!alive.getAsBoolean()) throw new IOException("Angular server exited before becoming ready. Check the session Output.");
            if (ready.getAsBoolean()) return;
            if (System.nanoTime()>=end) throw new IOException("Angular server did not become ready in time. Check its URL and build errors in Output.");
            Thread.sleep(250);
        }
        throw new IOException("Debug startup canceled.");
    }
}
