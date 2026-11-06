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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 * Models an Angular CLI workspace configured by {@code angular.json}.
 */
public final class AngularWorkspace {

    private static final Logger LOG = Logger.getLogger(AngularWorkspace.class.getName());

    public static final String ANGULAR_JSON = "angular.json"; // NOI18N
    public static final String ICON_RESOURCE = "org/netbeans/modules/angular/resources/angular_icon_16.png"; // NOI18N

    private static final String[] FRONTEND_SUBDIRS = {
        "frontend",
        "client",
        "web",
        "ui",
        "webapp",
        "src/main/webapp",
        "src/main/frontend",
        "src/main/angular"
    };

    private final FileObject workspaceDir;
    private final FileObject angularJsonFile;
    private final int version;
    private final String defaultProjectName;
    private final String packageManager;
    private final Map<String, AngularSubProject> projects;

    private AngularWorkspace(
            FileObject workspaceDir,
            FileObject angularJsonFile,
            int version,
            String defaultProjectName,
            String packageManager,
            Map<String, AngularSubProject> projects) {
        this.workspaceDir = workspaceDir;
        this.angularJsonFile = angularJsonFile;
        this.version = version;
        this.defaultProjectName = defaultProjectName;
        this.packageManager = packageManager;
        this.projects = Collections.unmodifiableMap(projects);
    }

    public @NonNull FileObject getWorkspaceDir() {
        return workspaceDir;
    }

    public @NonNull FileObject getAngularJsonFile() {
        return angularJsonFile;
    }

    public int getVersion() {
        return version;
    }

    public @CheckForNull String getDefaultProjectName() {
        return defaultProjectName;
    }

    public @NonNull String getPackageManager() {
        return packageManager != null ? packageManager : "npm"; // NOI18N
    }

    public @NonNull Map<String, AngularSubProject> getProjects() {
        return projects;
    }

    public @CheckForNull AngularSubProject getPrimaryProject() {
        if (defaultProjectName != null && projects.containsKey(defaultProjectName)) {
            return projects.get(defaultProjectName);
        }
        return projects.isEmpty() ? null : projects.values().iterator().next();
    }

    public @NonNull List<FileObject> getSourceRoots() {
        List<FileObject> roots = new ArrayList<>();
        for (AngularSubProject sub : projects.values()) {
            FileObject sourceRoot = sub.resolveSourceRoot(workspaceDir);
            if (sourceRoot != null && !roots.contains(sourceRoot)) {
                roots.add(sourceRoot);
            }
        }
        if (roots.isEmpty()) {
            FileObject defaultSrc = workspaceDir.getFileObject("src"); // NOI18N
            if (defaultSrc != null && defaultSrc.isFolder()) {
                roots.add(defaultSrc);
            } else {
                roots.add(workspaceDir);
            }
        }
        return roots;
    }

    public @NonNull List<String> getAvailableArchitectTargets() {
        Set<String> targets = new LinkedHashSet<>();
        for (AngularSubProject sub : projects.values()) {
            targets.addAll(sub.getTargets().keySet());
        }
        if (targets.isEmpty()) {
            targets.add("serve"); // NOI18N
            targets.add("build"); // NOI18N
            targets.add("test"); // NOI18N
        }
        return new ArrayList<>(targets);
    }

    public static boolean isAngularWorkspace(@NullAllowed FileObject dir) {
        return findAngularJson(dir) != null;
    }

    public static boolean isAngularProject(@NullAllowed Project project) {
        if (project == null) {
            return false;
        }
        return findWorkspaceRoot(project) != null;
    }

    public static @CheckForNull FileObject findAngularJson(@NullAllowed FileObject fileOrDir) {
        FileObject root = findWorkspaceRoot(fileOrDir);
        return root != null ? root.getFileObject(ANGULAR_JSON) : null;
    }

    public static @CheckForNull FileObject findWorkspaceRoot(@NullAllowed Project project) {
        if (project == null) {
            return null;
        }
        return findWorkspaceRoot(project.getProjectDirectory());
    }

    public static @CheckForNull FileObject findWorkspaceRoot(@NullAllowed FileObject fileOrDir) {
        if (fileOrDir == null) {
            return null;
        }
        if (!fileOrDir.isFolder() && ANGULAR_JSON.equals(fileOrDir.getNameExt())) {
            return fileOrDir.getParent();
        }
        FileObject current = fileOrDir.isFolder() ? fileOrDir : fileOrDir.getParent();
        // 1. Check current and ancestor directories
        FileObject walk = current;
        int depth = 0;
        while (walk != null && depth < 10) {
            FileObject angularJson = walk.getFileObject(ANGULAR_JSON);
            if (angularJson != null && !angularJson.isFolder()) {
                return walk;
            }
            walk = walk.getParent();
            depth++;
        }
        // 2. Check common frontend subdirectories for full-stack Java + Angular repositories
        if (current != null) {
            for (String sub : FRONTEND_SUBDIRS) {
                FileObject subDir = current.getFileObject(sub);
                if (subDir != null && subDir.isFolder()) {
                    FileObject angularJson = subDir.getFileObject(ANGULAR_JSON);
                    if (angularJson != null && !angularJson.isFolder()) {
                        return subDir;
                    }
                }
            }
            // Also check immediate child folders (1 level deep)
            for (FileObject child : current.getChildren()) {
                if (child.isFolder() && !child.getName().startsWith(".") && !"node_modules".equals(child.getName())) {
                    FileObject angularJson = child.getFileObject(ANGULAR_JSON);
                    if (angularJson != null && !angularJson.isFolder()) {
                        return child;
                    }
                }
            }
        }
        // 3. Check project owner if different
        Project owner = FileOwnerQuery.getOwner(fileOrDir);
        if (owner != null && !owner.getProjectDirectory().equals(current)) {
            FileObject prjDir = owner.getProjectDirectory();
            FileObject angularJson = prjDir.getFileObject(ANGULAR_JSON);
            if (angularJson != null && !angularJson.isFolder()) {
                return prjDir;
            }
            for (String sub : FRONTEND_SUBDIRS) {
                FileObject subDir = prjDir.getFileObject(sub);
                if (subDir != null && subDir.isFolder()) {
                    FileObject subAngular = subDir.getFileObject(ANGULAR_JSON);
                    if (subAngular != null && !subAngular.isFolder()) {
                        return subDir;
                    }
                }
            }
        }
        return null;
    }

    public static @CheckForNull AngularWorkspace load(@NullAllowed Project project) {
        FileObject root = findWorkspaceRoot(project);
        return root != null ? load(root) : null;
    }

    public static @CheckForNull AngularWorkspace load(@NullAllowed FileObject dirOrFile) {
        FileObject workspaceRoot = findWorkspaceRoot(dirOrFile);
        if (workspaceRoot == null) {
            return null;
        }
        FileObject angularJson = workspaceRoot.getFileObject(ANGULAR_JSON);
        if (angularJson == null || angularJson.isFolder()) {
            return null;
        }
        try (Reader reader = new InputStreamReader(angularJson.getInputStream(), StandardCharsets.UTF_8)) {
            return parse(workspaceRoot, angularJson, reader);
        } catch (IOException | JsonSyntaxException ex) {
            LOG.log(Level.FINE, "Failed to parse angular.json in " + workspaceRoot.getPath(), ex);
            return new AngularWorkspace(workspaceRoot, angularJson, 1, workspaceRoot.getName(), "npm", Collections.emptyMap());
        }
    }

    public static @NonNull AngularWorkspace parse(
            @NonNull FileObject workspaceRoot,
            @NonNull FileObject angularJsonFile,
            @NonNull Reader reader) {
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (rootElement == null || !rootElement.isJsonObject()) {
            return new AngularWorkspace(workspaceRoot, angularJsonFile, 1, workspaceRoot.getName(), "npm", Collections.emptyMap());
        }
        JsonObject rootObj = rootElement.getAsJsonObject();
        int version = getInt(rootObj, "version", 1);
        String defaultProject = getString(rootObj, "defaultProject", null);
        String pkgManager = "npm"; // NOI18N
        if (rootObj.has("cli") && rootObj.get("cli").isJsonObject()) {
            pkgManager = getString(rootObj.getAsJsonObject("cli"), "packageManager", "npm");
        }

        Map<String, AngularSubProject> projects = new LinkedHashMap<>();
        if (rootObj.has("projects") && rootObj.get("projects").isJsonObject()) {
            JsonObject projectsObj = rootObj.getAsJsonObject("projects");
            for (Map.Entry<String, JsonElement> entry : projectsObj.entrySet()) {
                String prjName = entry.getKey();
                if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                    JsonObject prjJson = entry.getValue().getAsJsonObject();
                    AngularSubProject subProject = parseSubProject(prjName, prjJson);
                    projects.put(prjName, subProject);
                    if (defaultProject == null) {
                        defaultProject = prjName;
                    }
                }
            }
        }
        return new AngularWorkspace(workspaceRoot, angularJsonFile, version, defaultProject, pkgManager, projects);
    }

    private static AngularSubProject parseSubProject(String name, JsonObject prjJson) {
        String projectType = getString(prjJson, "projectType", "application");
        String root = getString(prjJson, "root", "");
        String sourceRoot = getString(prjJson, "sourceRoot", root.isEmpty() ? "src" : root + "/src");
        String prefix = getString(prjJson, "prefix", "app");

        Map<String, ArchitectTarget> targets = new LinkedHashMap<>();
        JsonObject architectObj = null;
        if (prjJson.has("architect") && prjJson.get("architect").isJsonObject()) {
            architectObj = prjJson.getAsJsonObject("architect");
        } else if (prjJson.has("targets") && prjJson.get("targets").isJsonObject()) {
            architectObj = prjJson.getAsJsonObject("targets");
        }
        if (architectObj != null) {
            for (Map.Entry<String, JsonElement> targetEntry : architectObj.entrySet()) {
                String targetName = targetEntry.getKey();
                if (targetEntry.getValue() != null && targetEntry.getValue().isJsonObject()) {
                    JsonObject tObj = targetEntry.getValue().getAsJsonObject();
                    String builder = getString(tObj, "builder", "");
                    String defaultConfig = getString(tObj, "defaultConfiguration", null);
                    String outputPath = null;
                    int port = 4200;
                    if (tObj.has("options") && tObj.get("options").isJsonObject()) {
                        JsonObject opts = tObj.getAsJsonObject("options");
                        if (opts.has("outputPath") && opts.get("outputPath").isJsonPrimitive()) {
                            outputPath = opts.get("outputPath").getAsString();
                        }
                        port = getInt(opts, "port", 4200);
                    }
                    List<String> configs = new ArrayList<>();
                    if (tObj.has("configurations") && tObj.get("configurations").isJsonObject()) {
                        configs.addAll(tObj.getAsJsonObject("configurations").keySet());
                    }
                    targets.put(targetName, new ArchitectTarget(targetName, builder, defaultConfig, outputPath, port, configs));
                }
            }
        }
        return new AngularSubProject(name, projectType, root, sourceRoot, prefix, targets);
    }

    private static String getString(JsonObject obj, String key, String defaultVal) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return defaultVal;
    }

    private static int getInt(JsonObject obj, String key, int defaultVal) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsInt();
            } catch (NumberFormatException ex) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    public static final class AngularSubProject {
        private final String name;
        private final String projectType;
        private final String root;
        private final String sourceRoot;
        private final String prefix;
        private final Map<String, ArchitectTarget> targets;

        public AngularSubProject(
                String name,
                String projectType,
                String root,
                String sourceRoot,
                String prefix,
                Map<String, ArchitectTarget> targets) {
            this.name = name;
            this.projectType = projectType;
            this.root = root;
            this.sourceRoot = sourceRoot;
            this.prefix = prefix;
            this.targets = Collections.unmodifiableMap(targets);
        }

        public String getName() {
            return name;
        }

        public String getProjectType() {
            return projectType;
        }

        public boolean isApplication() {
            return "application".equalsIgnoreCase(projectType); // NOI18N
        }

        public boolean isLibrary() {
            return "library".equalsIgnoreCase(projectType); // NOI18N
        }

        public String getRoot() {
            return root;
        }

        public String getSourceRoot() {
            return sourceRoot;
        }

        public String getPrefix() {
            return prefix;
        }

        public Map<String, ArchitectTarget> getTargets() {
            return targets;
        }

        public @CheckForNull FileObject resolveRoot(FileObject workspaceDir) {
            if (root == null || root.isEmpty() || ".".equals(root)) {
                return workspaceDir;
            }
            return workspaceDir.getFileObject(root);
        }

        public @CheckForNull FileObject resolveSourceRoot(FileObject workspaceDir) {
            if (sourceRoot == null || sourceRoot.isEmpty() || ".".equals(sourceRoot)) {
                return resolveRoot(workspaceDir);
            }
            FileObject resolved = workspaceDir.getFileObject(sourceRoot);
            return resolved != null ? resolved : resolveRoot(workspaceDir);
        }
    }

    public static final class ArchitectTarget {
        private final String name;
        private final String builder;
        private final String defaultConfiguration;
        private final String outputPath;
        private final int port;
        private final List<String> configurations;

        public ArchitectTarget(
                String name,
                String builder,
                String defaultConfiguration,
                String outputPath,
                int port,
                List<String> configurations) {
            this.name = name;
            this.builder = builder;
            this.defaultConfiguration = defaultConfiguration;
            this.outputPath = outputPath;
            this.port = port;
            this.configurations = Collections.unmodifiableList(configurations);
        }

        public String getName() {
            return name;
        }

        public String getBuilder() {
            return builder;
        }

        public @CheckForNull String getDefaultConfiguration() {
            return defaultConfiguration;
        }

        public @CheckForNull String getOutputPath() {
            return outputPath;
        }

        public int getPort() {
            return port;
        }

        public List<String> getConfigurations() {
            return configurations;
        }
    }
}
