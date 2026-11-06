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
package org.netbeans.modules.angular.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.openide.filesystems.FileObject;

/**
 * Parses {@code package.json} in an Angular workspace to expose npm scripts and package dependencies.
 */
public final class PackageJsonConfig {

    private static final Logger LOG = Logger.getLogger(PackageJsonConfig.class.getName());
    public static final String PACKAGE_JSON = "package.json"; // NOI18N

    private final FileObject packageJsonFile;
    private final String name;
    private final String version;
    private final Map<String, String> scripts;
    private final Map<String, String> dependencies;
    private final Map<String, String> devDependencies;

    public PackageJsonConfig(
            FileObject packageJsonFile,
            String name,
            String version,
            Map<String, String> scripts,
            Map<String, String> dependencies,
            Map<String, String> devDependencies) {
        this.packageJsonFile = packageJsonFile;
        this.name = name;
        this.version = version;
        this.scripts = Collections.unmodifiableMap(scripts);
        this.dependencies = Collections.unmodifiableMap(dependencies);
        this.devDependencies = Collections.unmodifiableMap(devDependencies);
    }

    public @NonNull FileObject getPackageJsonFile() {
        return packageJsonFile;
    }

    public @NonNull String getName() {
        return name != null ? name : packageJsonFile.getParent().getName();
    }

    public @CheckForNull String getVersion() {
        return version;
    }

    public @NonNull Map<String, String> getScripts() {
        return scripts;
    }

    public @NonNull Map<String, String> getDependencies() {
        return dependencies;
    }

    public @NonNull Map<String, String> getDevDependencies() {
        return devDependencies;
    }

    public boolean hasAngularCore() {
        return dependencies.containsKey("@angular/core") || devDependencies.containsKey("@angular/core"); // NOI18N
    }

    public @CheckForNull String getAngularVersion() {
        String v = dependencies.get("@angular/core"); // NOI18N
        if (v == null) {
            v = devDependencies.get("@angular/core"); // NOI18N
        }
        return v;
    }

    public static @CheckForNull PackageJsonConfig load(@NullAllowed FileObject workspaceOrFile) {
        if (workspaceOrFile == null) {
            return null;
        }
        FileObject pkgFile = workspaceOrFile.isFolder()
                ? workspaceOrFile.getFileObject(PACKAGE_JSON)
                : (PACKAGE_JSON.equals(workspaceOrFile.getNameExt()) ? workspaceOrFile : null);
        if (pkgFile == null || pkgFile.isFolder()) {
            FileObject wsRoot = AngularWorkspace.findWorkspaceRoot(workspaceOrFile);
            if (wsRoot != null) {
                pkgFile = wsRoot.getFileObject(PACKAGE_JSON);
            }
        }
        if (pkgFile == null || pkgFile.isFolder()) {
            return null;
        }
        try (Reader reader = new InputStreamReader(pkgFile.getInputStream(), StandardCharsets.UTF_8)) {
            return parse(pkgFile, reader);
        } catch (IOException | JsonSyntaxException ex) {
            LOG.log(Level.FINE, "Could not parse package.json: " + pkgFile.getPath(), ex);
            return new PackageJsonConfig(pkgFile, pkgFile.getParent().getName(), null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    public static @NonNull PackageJsonConfig parse(@NonNull FileObject pkgFile, @NonNull Reader reader) {
        JsonElement element = JsonParser.parseReader(reader);
        if (element == null || !element.isJsonObject()) {
            return new PackageJsonConfig(pkgFile, pkgFile.getParent().getName(), null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
        JsonObject obj = element.getAsJsonObject();
        String name = obj.has("name") && obj.get("name").isJsonPrimitive() ? obj.get("name").getAsString() : pkgFile.getParent().getName();
        String version = obj.has("version") && obj.get("version").isJsonPrimitive() ? obj.get("version").getAsString() : null;
        Map<String, String> scripts = readStringMap(obj, "scripts"); // NOI18N
        Map<String, String> deps = readStringMap(obj, "dependencies"); // NOI18N
        Map<String, String> devDeps = readStringMap(obj, "devDependencies"); // NOI18N
        return new PackageJsonConfig(pkgFile, name, version, scripts, deps, devDeps);
    }

    private static Map<String, String> readStringMap(JsonObject parent, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            JsonObject child = parent.getAsJsonObject(key);
            for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        return result;
    }
}
