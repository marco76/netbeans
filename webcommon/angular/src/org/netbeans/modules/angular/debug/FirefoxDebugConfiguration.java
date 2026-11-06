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

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Firefox adapter configuration and bounded VSIX extraction, independent of UI. */
public final class FirefoxDebugConfiguration {
    private FirefoxDebugConfiguration() {}

    public static Path adapter(Path location) {
        if (Files.isRegularFile(location) && location.getFileName().toString().equals("adapter.bundle.js")) return location;
        for (String relative : List.of("dist/adapter.bundle.js", "extension/dist/adapter.bundle.js", "adapter.bundle.js")) {
            Path candidate=location.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalArgumentException("Choose the Firefox adapter's adapter.bundle.js file or its extension directory.");
    }

    public static Map<String,Object> configuration(Path workspace, String url, String firefox, boolean attach, int port, String mappings) {
        URI uri;
        try { uri=URI.create(url.trim()); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Enter a valid application URL."); }
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Use an HTTP or HTTPS application URL without credentials.");
        if (port<1 || port>65535) throw new IllegalArgumentException("The Firefox debugger port must be between 1 and 65535.");
        if (!Files.isDirectory(workspace)) throw new IllegalArgumentException("Choose the local web root directory containing your source files.");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("type", "firefox"); result.put("request", attach ? "attach" : "launch");
        result.put("url", uri.toString()); result.put("webRoot", workspace.toAbsolutePath().normalize().toString());
        result.put("host", "localhost"); result.put("port", port);
        result.put("sourceMaps", true); result.put("reAttach", false);
        if (!attach) result.put("keepProfileChanges", false);
        result.put("skipFiles", List.of("**/node_modules/**"));
        result.put("suggestPathMappingWizard", false);
        result.put("timeout", 20);
        if (!attach && !firefox.isBlank()) result.put("firefoxExecutable", firefox.trim());
        if (!mappings.isBlank()) {
            try {
                JsonArray array=JsonParser.parseString(mappings).getAsJsonArray();
                List<Map<String,Object>> parsed=new ArrayList<>();
                for (JsonElement item : array) {
                    JsonObject obj=item.getAsJsonObject();
                    if (!obj.has("url") || !obj.has("path") || !obj.get("url").isJsonPrimitive()
                            || !obj.get("url").getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                    Map<String,Object> map=new LinkedHashMap<>();
                    map.put("url", obj.get("url").getAsString());
                    if (obj.get("path").isJsonNull()) map.put("path", null);
                    else {
                        if (!obj.get("path").isJsonPrimitive() || !obj.get("path").getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                        map.put("path", obj.get("path").getAsString().replace("${workspaceFolder}", workspace.toAbsolutePath().normalize().toString()));
                    }
                    parsed.add(map);
                }
                result.put("pathMappings", parsed);
            } catch (RuntimeException ex) { throw new IllegalArgumentException("Path mappings must be a JSON array of {\"url\":\"…\",\"path\":\"…\"} entries (path can be null)."); }
        }
        return result;
    }

    /** Keep adapter-created profiles out of inherited, possibly inaccessible OS temp locations. */
    public static void prepareProfileDirectory(Map<String,Object> config, Path directory) throws IOException {
        if (!"launch".equals(config.get("request"))) return;
        Files.createDirectories(directory);
        Path real=directory.toRealPath();
        if (!Files.isWritable(real)) throw new IOException("Firefox debug profile directory is not writable: "+real);
        // tmpDir is the adapter's supported parent directory, not a profile to copy.
        // It creates a unique profile here and removes it when the session ends.
        config.put("tmpDir", real.toString());
        // Keep a bounded set of diagnostic files so unresolved source-map paths
        // can be investigated without asking users to change adapter settings.
        String logName="adapter-"+UUID.randomUUID()+".log";
        try (var logs=Files.list(real)) {
            for (Path old : logs.filter(p -> p.getFileName().toString().matches("adapter-[0-9a-f-]+\\.log"))
                    .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed())
                    .skip(4).toList()) Files.deleteIfExists(old);
        }
        config.put("log", Map.of("fileName", real.resolve(logName).toString(),
                "fileLevel", Map.of("default", "Debug")));
    }

    /** Firefox initializes app data as well as the selected profile. Keep both out of
     * the personal Firefox directories, which macOS may deny to a child process. */
    public static void prepareLaunchEnvironment(Map<String,Object> config, Map<String,String> environment) throws IOException {
        if (!"launch".equals(config.get("request"))) return;
        Object temporaryDirectory=config.get("tmpDir");
        if (!(temporaryDirectory instanceof String directory)) {
            throw new IOException("Prepare the Firefox profile directory before launching.");
        }
        Path root=Path.of(directory);
        Path appData=Files.createDirectories(root.resolve("app-data")).toRealPath();
        Path localData=Files.createDirectories(root.resolve("local-app-data")).toRealPath();
        if (!Files.isWritable(appData) || !Files.isWritable(localData)) {
            throw new IOException("Firefox debug application data directories are not writable: "+root);
        }
        environment.put("MOZ_APP_DATA", appData.toString());
        environment.put("MOZ_LOCAL_APP_DATA", localData.toString());
    }

    /** A fixed launch port can connect the adapter to a leftover Firefox process.
     * Attach deliberately keeps the explicitly configured port. */
    public static void prepareLaunchPort(Map<String,Object> config) throws IOException {
        if (!"launch".equals(config.get("request"))) return;
        try (var socket=new java.net.ServerSocket()) {
            socket.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"),0));
            config.put("port",socket.getLocalPort());
        }
    }

    /** Extract the complete extension, retaining adapter dependencies and license files. */
    public static Path installVsix(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        Path root=destination.toAbsolutePath().normalize();
        long total=0; int count=0;
        try (ZipInputStream zip=new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer=new byte[8192];
            while ((entry=zip.getNextEntry())!=null) {
                if (++count>20000) throw new IOException("Adapter archive has too many files.");
                String name=entry.getName().replace('\\', '/');
                if (!name.startsWith("extension/")) continue;
                Path target=root.resolve(name).normalize();
                if (!target.startsWith(root)) throw new IOException("Adapter archive contains an unsafe path.");
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (OutputStream out=Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read=zip.read(buffer))!=-1) {
                        total+=read;
                        if (total>128L*1024*1024) throw new IOException("Adapter archive exceeds 128 MiB.");
                        out.write(buffer,0,read);
                    }
                }
            }
        }
        try { return adapter(root); }
        catch (IllegalArgumentException ex) { throw new IOException("This VSIX does not contain the Firefox debug adapter.", ex); }
    }
}
