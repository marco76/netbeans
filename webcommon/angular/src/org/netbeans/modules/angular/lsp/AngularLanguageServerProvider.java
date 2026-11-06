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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.api.project.Project;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.angular.model.PackageJsonConfig;
import org.netbeans.modules.lsp.client.spi.LanguageIdResolver;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider;
import org.netbeans.modules.lsp.client.spi.MultiMimeLanguageServerProvider;
import org.netbeans.modules.lsp.client.spi.ServerRestarter;
import org.openide.awt.NotificationDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;

/**
 * Starts the official Angular Language Server ({@code @angular/language-server} / {@code ngserver})
 * over stdio and configures {@code --tsProbeLocations} and {@code --ngProbeLocations} for both
 * TypeScript ({@code application/x-typescript}) and Angular HTML templates ({@code text/html}).
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = AngularLanguageServerProvider.TYPESCRIPT_MIME_TYPE, service = LanguageServerProvider.class, position = 100),
    @MimeRegistration(mimeType = AngularLanguageServerProvider.HTML_MIME_TYPE, service = LanguageServerProvider.class, position = 100)
})
public class AngularLanguageServerProvider implements MultiMimeLanguageServerProvider {

    private static final Logger LOG = Logger.getLogger(AngularLanguageServerProvider.class.getName());

    public static final String TYPESCRIPT_MIME_TYPE = "application/x-typescript"; // NOI18N
    public static final String HTML_MIME_TYPE = "text/html"; // NOI18N

    private static final Set<String> SUPPORTED_MIME_TYPES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(TYPESCRIPT_MIME_TYPE, HTML_MIME_TYPE)));

    public static final LanguageIdResolver LANGUAGE_ID_RESOLVER = (FileObject fo) -> {
        String ext = fo.getExt();
        if ("html".equalsIgnoreCase(ext) || "htm".equalsIgnoreCase(ext)) { // NOI18N
            return "html"; // NOI18N
        }
        if ("tsx".equalsIgnoreCase(ext)) { // NOI18N
            return "typescriptreact"; // NOI18N
        }
        return "typescript"; // NOI18N
    };

    private final AtomicBoolean missingServerWarningIssued = new AtomicBoolean(false);
    private PreferenceChangeListener optionsListener;

    @Override
    public Set<String> getMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    @Messages({
        "WARN_NoNodeForAngular=Node.js not found; Angular Language Server disabled.",
        "DESC_NoNodeForAngular=Please specify Node.js location in Tools > Options > HTML/JS > Node.js.",
        "WARN_NoNgServer=@angular/language-server (ngserver) not found.",
        "DESC_NoNgServer=Install @angular/language-server for your Angular version, then configure its location in Tools > Options > HTML/JS > Angular.",
        "WARN_AngularStartup=Angular code intelligence could not start.",
        "DESC_AngularStartup=Check the IDE log for details, verify project dependencies, and review Tools > Options > HTML/JS > Angular."
    })
    public @CheckForNull LanguageServerDescription startServer(@NonNull Lookup lookup) {
        Project prj = lookup.lookup(Project.class);
        if (!isAngularApplicable(prj)) {
            return null;
        }

        FileObject workspaceRoot = prj != null ? AngularWorkspace.findWorkspaceRoot(prj) : null;
        if (workspaceRoot == null && prj != null) {
            workspaceRoot = prj.getProjectDirectory();
        }

        AngularOptions options = AngularOptions.getInstance();
        // Keep recovery available even when initial setup is incomplete.
        ServerRestarter restarter = lookup.lookup(ServerRestarter.class);
        if (restarter != null) {
            optionsListener = evt -> {
                missingServerWarningIssued.set(false);
                restarter.restart();
            };
            options.addPreferenceChangeListener(WeakListeners.create(PreferenceChangeListener.class, optionsListener, options));
        }
        String node = options.resolveNodeExecutable(prj);
        if (node == null || node.isEmpty()) {
            if (!missingServerWarningIssued.getAndSet(true)) {
                NotificationDisplayer.getDefault().notify(
                        Bundle.WARN_NoNodeForAngular(),
                        ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, true),
                        Bundle.DESC_NoNodeForAngular(),
                        evt -> OptionsDisplayer.getDefault().open("Html5/NodeJs")); // NOI18N
            }
            return null;
        }

        File ngServer = options.resolveNgServer(workspaceRoot);
        if (ngServer == null || !ngServer.exists()) {
            if (!missingServerWarningIssued.getAndSet(true)) {
                NotificationDisplayer.getDefault().notify(
                        Bundle.WARN_NoNgServer(),
                        ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, true),
                        Bundle.DESC_NoNgServer(),
                        evt -> OptionsDisplayer.getDefault().open("Html5/Angular"));
            }
            return null;
        }

        List<String> tsProbeLocations = options.resolveTsProbeLocations(workspaceRoot, ngServer);
        List<String> ngProbeLocations = options.resolveNgProbeLocations(workspaceRoot, ngServer);
        List<String> command = buildServerCommand(node, ngServer, tsProbeLocations, ngProbeLocations);

        File workingDir = workspaceRoot != null ? FileUtil.toFile(workspaceRoot) : ngServer.getParentFile();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null && workingDir.isDirectory()) {
                pb.directory(workingDir);
            }
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            try {
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    LOG.log(Level.WARNING, "Angular Language Server exited during startup (code {0}): {1}",
                            new Object[]{process.exitValue(), String.join(" ", command)});
                    notifyStartupFailure();
                    return null;
                }
            } catch (InterruptedException ex) {
                process.destroy();
                Thread.currentThread().interrupt();
                return null;
            }
            missingServerWarningIssued.set(false);
            LOG.log(Level.INFO, "Started Angular Language Server: {0}", String.join(" ", command)); // NOI18N
            return LanguageServerDescription.create(
                    process.getInputStream(),
                    process.getOutputStream(),
                    process,
                    Lookups.fixed(LANGUAGE_ID_RESOLVER));
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to start Angular Language Server (ngserver)", ex); // NOI18N
            notifyStartupFailure();
            return null;
        }
    }

    private void notifyStartupFailure() {
        if (!missingServerWarningIssued.getAndSet(true)) {
            NotificationDisplayer.getDefault().notify(
                    Bundle.WARN_AngularStartup(),
                    ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, true),
                    Bundle.DESC_AngularStartup(),
                    evt -> OptionsDisplayer.getDefault().open("Html5/Angular"));
        }
    }

    /**
     * Checks whether the given project is an Angular workspace (contains {@code angular.json}
     * or declares {@code @angular/core} in {@code package.json}).
     */
    public static boolean isAngularApplicable(@NullAllowed Project project) {
        if (project == null) {
            return false;
        }
        if (AngularWorkspace.isAngularProject(project)) {
            return true;
        }
        PackageJsonConfig pkg = PackageJsonConfig.load(project.getProjectDirectory());
        return pkg != null && pkg.hasAngularCore();
    }

    /**
     * Constructs the command line arguments used to launch {@code ngserver} via LSP over stdio.
     */
    public static @NonNull List<String> buildServerCommand(
            @NonNull String nodeExecutable,
            @NonNull File ngServerFile,
            @NonNull List<String> tsProbeLocations,
            @NonNull List<String> ngProbeLocations) {
        List<String> cmd = new ArrayList<>();
        String name = ngServerFile.getName().toLowerCase();
        if (name.endsWith(".cmd") || name.endsWith(".bat") || name.endsWith(".exe")) { // NOI18N
            cmd.add(ngServerFile.getAbsolutePath());
        } else {
            cmd.add(nodeExecutable);
            cmd.add(ngServerFile.getAbsolutePath());
        }
        cmd.add("--stdio"); // NOI18N
        if (!tsProbeLocations.isEmpty()) {
            cmd.add("--tsProbeLocations"); // NOI18N
            cmd.add(String.join(",", tsProbeLocations)); // NOI18N
        }
        if (!ngProbeLocations.isEmpty()) {
            cmd.add("--ngProbeLocations"); // NOI18N
            cmd.add(String.join(",", ngProbeLocations)); // NOI18N
        }
        cmd.add("--includeAutomaticOptionalChainCompletions"); // NOI18N
        cmd.add("--includeCompletionsWithSnippetText"); // NOI18N
        return cmd;
    }
}
