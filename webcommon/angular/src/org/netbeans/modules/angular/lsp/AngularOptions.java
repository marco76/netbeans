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
package org.netbeans.modules.angular.lsp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.project.Project;
import org.netbeans.modules.javascript.nodejs.api.NodeJsSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbPreferences;
import org.openide.util.Utilities;

/**
 * Manages and auto-discovers executable and probe paths for Node.js, Angular CLI (ng),
 * Angular Language Server (ngserver / @angular/language-server), TypeScript (--tsProbeLocations),
 * and Angular Language Service (--ngProbeLocations).
 */
public final class AngularOptions {

    public static final String KEY_NODE_PATH = "angular.node.path"; // NOI18N
    public static final String KEY_NG_CLI_PATH = "angular.ng.cli.path"; // NOI18N
    public static final String KEY_NGSERVER_PATH = "angular.ngserver.path"; // NOI18N
    public static final String KEY_TS_PROBE_LOCATIONS = "angular.tsProbeLocations"; // NOI18N
    public static final String KEY_NG_PROBE_LOCATIONS = "angular.ngProbeLocations"; // NOI18N

    private static final AngularOptions INSTANCE = new AngularOptions();

    private AngularOptions() {
    }

    public static @NonNull AngularOptions getInstance() {
        return INSTANCE;
    }

    private Preferences getPreferences() {
        return NbPreferences.forModule(AngularOptions.class);
    }

    public void addPreferenceChangeListener(PreferenceChangeListener listener) {
        getPreferences().addPreferenceChangeListener(listener);
    }

    public void removePreferenceChangeListener(PreferenceChangeListener listener) {
        getPreferences().removePreferenceChangeListener(listener);
    }

    public void setNodePath(@NullAllowed String path) {
        putOrRemove(KEY_NODE_PATH, path);
    }

    public void setNgCliPath(@NullAllowed String path) {
        putOrRemove(KEY_NG_CLI_PATH, path);
    }

    public void setNgServerPath(@NullAllowed String path) {
        putOrRemove(KEY_NGSERVER_PATH, path);
    }

    public void setCustomTsProbeLocations(@NullAllowed String locations) {
        putOrRemove(KEY_TS_PROBE_LOCATIONS, locations);
    }

    public void setCustomNgProbeLocations(@NullAllowed String locations) {
        putOrRemove(KEY_NG_PROBE_LOCATIONS, locations);
    }

    public String getConfiguredPath(String key) {
        return getPreferences().get(key, "");
    }

    private void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            getPreferences().remove(key);
        } else {
            getPreferences().put(key, value.trim());
        }
    }

    /**
     * Resolves the Node.js executable for the given project or global environment.
     */
    public @CheckForNull String resolveNodeExecutable(@NullAllowed Project project) {
        String custom = System.getProperty(KEY_NODE_PATH, getPreferences().get(KEY_NODE_PATH, null));
        if (isValidExecutable(custom)) {
            return custom;
        }
        try {
            String fromSupport = NodeJsSupport.getInstance().getNode(project);
            if (isValidExecutable(fromSupport)) {
                return fromSupport;
            }
        } catch (Throwable t) {
            // Fall through to auto-detection
        }
        String exeName = Utilities.isWindows() ? "node.exe" : "node"; // NOI18N
        File fromNvm = findLatestNvmBinary(exeName);
        if (fromNvm != null) {
            return fromNvm.getAbsolutePath();
        }
        for (String dir : getCommonBinaryDirs()) {
            File candidate = new File(dir, exeName);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Resolves the npm executable for the given project or global environment.
     */
    public @CheckForNull String resolveNpmExecutable(@NullAllowed Project project) {
        String node = resolveNodeExecutable(project);
        String npmName = Utilities.isWindows() ? "npm.cmd" : "npm"; // NOI18N
        if (node != null) {
            File nodeParent = new File(node).getParentFile();
            if (nodeParent != null) {
                File sibling = new File(nodeParent, npmName);
                if (sibling.isFile() && sibling.canExecute()) {
                    return sibling.getAbsolutePath();
                }
            }
        }
        File fromNvm = findLatestNvmBinary(npmName);
        if (fromNvm != null) {
            return fromNvm.getAbsolutePath();
        }
        for (String dir : getCommonBinaryDirs()) {
            File candidate = new File(dir, npmName);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Resolves the Angular CLI {@code ng} executable or JS entry point for a workspace.
     */
    public @CheckForNull File resolveNgCli(@NullAllowed FileObject workspaceDir) {
        String custom = System.getProperty(KEY_NG_CLI_PATH, getPreferences().get(KEY_NG_CLI_PATH, null));
        if (custom != null && !custom.isBlank()) {
            File f = new File(custom.trim());
            if (f.exists()) {
                return f;
            }
        }
        if (workspaceDir != null) {
            File wsFile = FileUtil.toFile(workspaceDir);
            if (wsFile != null) {
                File entry = new File(wsFile, "node_modules/@angular/cli/bin/ng.js");
                if (entry.isFile()) {
                    return entry;
                }
                File localBin = new File(wsFile, Utilities.isWindows()
                        ? "node_modules/.bin/ng.cmd" : "node_modules/.bin/ng"); // NOI18N
                if (localBin.isFile()) {
                    return localBin;
                }
                File localJs = new File(wsFile, "node_modules/@angular/cli/bin/ng.js"); // NOI18N
                if (localJs.isFile()) {
                    return localJs;
                }
            }
        }
        String ngName = Utilities.isWindows() ? "ng.cmd" : "ng"; // NOI18N
        File fromNvm = findLatestNvmBinary(ngName);
        if (fromNvm != null) {
            return fromNvm;
        }
        for (String dir : getCommonBinaryDirs()) {
            File candidate = new File(dir, ngName);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Resolves the {@code @angular/language-server} entry point (either {@code index.js} or {@code ngserver} binary).
     */
    public @CheckForNull File resolveNgServer(@NullAllowed FileObject workspaceDir) {
        String custom = System.getProperty(KEY_NGSERVER_PATH, getPreferences().get(KEY_NGSERVER_PATH, null));
        if (custom != null && !custom.isBlank()) {
            File customFile = new File(custom.trim());
            if (customFile.isDirectory()) {
                File idx = new File(customFile, "index.js"); // NOI18N
                if (idx.isFile()) {
                    return idx;
                }
                File bin = new File(customFile, "bin/ngserver"); // NOI18N
                if (bin.isFile()) {
                    return bin;
                }
            }
            if (customFile.isFile()) {
                return customFile;
            }
        }

        // Only ngserver implements LSP; the Angular CLI (ng.js) does not.
        File localServer = findLocalLanguageServer(workspaceDir == null ? null : FileUtil.toFile(workspaceDir));
        if (localServer != null) return localServer;

        // 2. Check global node_modules roots (NVM, Homebrew, /usr/local, etc.)
        for (File globalNodeModules : getGlobalNodeModulesDirs()) {
            File indexJs = new File(globalNodeModules, "@angular/language-server/index.js"); // NOI18N
            if (indexJs.isFile()) {
                return indexJs;
            }
            File binScript = new File(globalNodeModules, "@angular/language-server/bin/ngserver"); // NOI18N
            if (binScript.isFile()) {
                return binScript;
            }
        }

        // 3. Check binary directories on PATH
        String binName = Utilities.isWindows() ? "ngserver.cmd" : "ngserver"; // NOI18N
        File nvmBin = findLatestNvmBinary(binName);
        if (nvmBin != null) {
            return nvmBin;
        }
        for (String dir : getCommonBinaryDirs()) {
            File candidate = new File(dir, binName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    static @CheckForNull File findLocalLanguageServer(@NullAllowed File workspace) {
        for (File dir = workspace; dir != null; dir = dir.getParentFile()) {
            for (String entry : new String[]{"index.js", "bin/ngserver"}) {
                File server = new File(dir, "node_modules/@angular/language-server/" + entry);
                if (server.isFile()) return server;
            }
        }
        return null;
    }

    /**
     * Computes comma-separated {@code --tsProbeLocations} for {@code ngserver}.
     * Order of precedence:
     * 1. Explicit user/system override
     * 2. Workspace {@code node_modules} (and ancestor monorepo {@code node_modules})
     * 3. {@code @angular/language-server/node_modules}
     * 4. Global {@code node_modules}
     * 5. NetBeans bundled {@code typescript-lsp/node_modules}
     */
    public @NonNull List<String> resolveTsProbeLocations(@NullAllowed FileObject workspaceDir, @NullAllowed File ngServerFile) {
        Set<String> locations = new LinkedHashSet<>();

        String custom = System.getProperty(KEY_TS_PROBE_LOCATIONS, getPreferences().get(KEY_TS_PROBE_LOCATIONS, null));
        if (custom != null && !custom.isBlank()) {
            for (String part : custom.split(",")) { // NOI18N
                if (!part.isBlank()) {
                    locations.add(part.trim());
                }
            }
        }

        addWorkspaceNodeModules(workspaceDir, locations);
        addLanguageServerNodeModules(ngServerFile, locations);

        for (File globalNm : getGlobalNodeModulesDirs()) {
            if (new File(globalNm, "typescript/lib/tsserverlibrary.js").isFile()
                    || new File(globalNm, "typescript/package.json").isFile()) {
                locations.add(globalNm.getAbsolutePath());
            }
            File nestedNm = new File(globalNm, "@angular/language-server/node_modules"); // NOI18N
            if (nestedNm.isDirectory()) {
                locations.add(nestedNm.getAbsolutePath());
            }
        }

        try {
            File nbBundledTs = InstalledFileLocator.getDefault().locate(
                    "typescript-lsp/node_modules", "org.netbeans.modules.typescript.editor", false); // NOI18N
            if (nbBundledTs != null && nbBundledTs.isDirectory()) {
                locations.add(nbBundledTs.getAbsolutePath());
            }
        } catch (Throwable ignored) {
        }

        if (locations.isEmpty() && workspaceDir != null) {
            File wsFile = FileUtil.toFile(workspaceDir);
            if (wsFile != null) {
                locations.add(new File(wsFile, "node_modules").getAbsolutePath()); // NOI18N
            }
        }
        return new ArrayList<>(locations);
    }

    /**
     * Computes comma-separated {@code --ngProbeLocations} for {@code ngserver}.
     * Order of precedence:
     * 1. Explicit user/system override
     * 2. Workspace {@code node_modules} (and ancestor monorepo {@code node_modules})
     * 3. {@code @angular/language-server/node_modules} (where {@code @angular/language-service} is bundled)
     * 4. Global {@code node_modules}
     */
    public @NonNull List<String> resolveNgProbeLocations(@NullAllowed FileObject workspaceDir, @NullAllowed File ngServerFile) {
        Set<String> locations = new LinkedHashSet<>();

        String custom = System.getProperty(KEY_NG_PROBE_LOCATIONS, getPreferences().get(KEY_NG_PROBE_LOCATIONS, null));
        if (custom != null && !custom.isBlank()) {
            for (String part : custom.split(",")) { // NOI18N
                if (!part.isBlank()) {
                    locations.add(part.trim());
                }
            }
        }

        addWorkspaceNodeModules(workspaceDir, locations);
        addLanguageServerNodeModules(ngServerFile, locations);

        for (File globalNm : getGlobalNodeModulesDirs()) {
            File nestedNm = new File(globalNm, "@angular/language-server/node_modules"); // NOI18N
            if (nestedNm.isDirectory()) {
                locations.add(nestedNm.getAbsolutePath());
            }
            if (new File(globalNm, "@angular/language-service/package.json").isFile()
                    || new File(globalNm, "@angular/core/package.json").isFile()) {
                locations.add(globalNm.getAbsolutePath());
            }
        }

        if (locations.isEmpty() && workspaceDir != null) {
            File wsFile = FileUtil.toFile(workspaceDir);
            if (wsFile != null) {
                locations.add(new File(wsFile, "node_modules").getAbsolutePath()); // NOI18N
            }
        }
        return new ArrayList<>(locations);
    }

    private static void addWorkspaceNodeModules(@NullAllowed FileObject workspaceDir, Set<String> locations) {
        if (workspaceDir == null) {
            return;
        }
        File wsFile = FileUtil.toFile(workspaceDir);
        int depth = 0;
        for (File dir = wsFile; dir != null && depth < 5; dir = dir.getParentFile(), depth++) {
            File nm = new File(dir, "node_modules"); // NOI18N
            if (nm.isDirectory()) {
                locations.add(nm.getAbsolutePath());
            }
        }
    }

    private static void addLanguageServerNodeModules(@NullAllowed File ngServerFile, Set<String> locations) {
        if (ngServerFile == null) {
            return;
        }
        File resolved = ngServerFile;
        try {
            resolved = ngServerFile.getCanonicalFile();
        } catch (Exception ignored) {
        }
        File parent = resolved.isDirectory() ? resolved : resolved.getParentFile();
        if (parent != null) {
            if ("bin".equals(parent.getName()) && parent.getParentFile() != null) { // NOI18N
                parent = parent.getParentFile();
            }
            File bundledNm = new File(parent, "node_modules"); // NOI18N
            if (bundledNm.isDirectory()) {
                locations.add(bundledNm.getAbsolutePath());
            }
            // If parent is inside <global>/node_modules/@angular/language-server, also add <global>/node_modules
            File maybeAngularScope = parent.getParentFile();
            if (maybeAngularScope != null && "@angular".equals(maybeAngularScope.getName())) { // NOI18N
                File outerNm = maybeAngularScope.getParentFile();
                if (outerNm != null && outerNm.isDirectory()) {
                    locations.add(outerNm.getAbsolutePath());
                }
            }
        }
    }

    public static @NonNull List<File> getGlobalNodeModulesDirs() {
        List<File> dirs = new ArrayList<>();
        String userHome = System.getProperty("user.home"); // NOI18N
        if (userHome != null) {
            File nvmVersions = new File(userHome, ".nvm/versions/node"); // NOI18N
            if (nvmVersions.isDirectory()) {
                File[] children = nvmVersions.listFiles(File::isDirectory);
                if (children != null) {
                    Arrays.sort(children, Comparator.comparing(File::getName).reversed());
                    for (File ver : children) {
                        File libNm = new File(ver, "lib/node_modules"); // NOI18N
                        if (libNm.isDirectory()) {
                            dirs.add(libNm);
                        }
                    }
                }
            }
            File npmGlobal = new File(userHome, ".npm-global/lib/node_modules"); // NOI18N
            if (npmGlobal.isDirectory()) {
                dirs.add(npmGlobal);
            }
        }
        for (String path : new String[]{
            "/opt/homebrew/lib/node_modules", // NOI18N
            "/usr/local/lib/node_modules", // NOI18N
            "/usr/lib/node_modules" // NOI18N
        }) {
            File f = new File(path);
            if (f.isDirectory()) {
                dirs.add(f);
            }
        }
        return dirs;
    }

    private static @CheckForNull File findLatestNvmBinary(String binaryName) {
        String userHome = System.getProperty("user.home"); // NOI18N
        if (userHome == null) {
            return null;
        }
        File nvmVersions = new File(userHome, ".nvm/versions/node"); // NOI18N
        if (!nvmVersions.isDirectory()) {
            return null;
        }
        File[] children = nvmVersions.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return null;
        }
        Arrays.sort(children, Comparator.comparing(File::getName).reversed());
        for (File ver : children) {
            File bin = new File(ver, "bin/" + binaryName); // NOI18N
            if (bin.isFile() && bin.canExecute()) {
                return bin;
            }
        }
        return null;
    }

    private static List<String> getCommonBinaryDirs() {
        List<String> dirs = new ArrayList<>();
        String pathEnv = System.getenv("PATH"); // NOI18N
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                if (!p.isBlank()) {
                    dirs.add(p.trim());
                }
            }
        }
        for (String std : new String[]{"/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"}) { // NOI18N
            if (!dirs.contains(std)) {
                dirs.add(std);
            }
        }
        return dirs;
    }

    private static boolean isValidExecutable(@NullAllowed String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        File f = new File(path.trim());
        return f.isFile() && f.canExecute();
    }
}
