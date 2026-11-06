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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.modules.angular.model.AngularComponentScanner;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Implements NetBeans {@link ActionProvider} for Angular CLI workspaces,
 * wiring Run, Build, Clean, Test, Test Single, Debug, and Angular CLI commands.
 */
public final class AngularActionProvider implements ActionProvider {

    private static final Logger LOG = Logger.getLogger(AngularActionProvider.class.getName());

    public static final String COMMAND_NG_SERVE = "ng.serve"; // NOI18N
    public static final String COMMAND_NG_BUILD_PROD = "ng.build.production"; // NOI18N
    public static final String COMMAND_NG_LINT = "ng.lint"; // NOI18N
    public static final String COMMAND_NG_E2E = "ng.e2e"; // NOI18N
    public static final String COMMAND_NG_GENERATE_COMPONENT = "ng.generate.component"; // NOI18N
    public static final String COMMAND_NG_GENERATE_SERVICE = "ng.generate.service"; // NOI18N
    public static final String COMMAND_NG_GENERATE_DIRECTIVE = "ng.generate.directive"; // NOI18N
    public static final String COMMAND_NG_GENERATE_PIPE = "ng.generate.pipe"; // NOI18N
    public static final String COMMAND_NG_GENERATE_GUARD = "ng.generate.guard"; // NOI18N
    public static final String COMMAND_NG_GENERATE_INTERFACE = "ng.generate.interface"; // NOI18N

    private static final String[] SUPPORTED_ACTIONS = {
        COMMAND_RUN,
        COMMAND_DEBUG,
        COMMAND_BUILD,
        COMMAND_REBUILD,
        COMMAND_CLEAN,
        COMMAND_TEST,
        COMMAND_TEST_SINGLE,
        COMMAND_NG_SERVE,
        COMMAND_NG_BUILD_PROD,
        COMMAND_NG_LINT,
        COMMAND_NG_E2E,
        COMMAND_NG_GENERATE_COMPONENT,
        COMMAND_NG_GENERATE_SERVICE,
        COMMAND_NG_GENERATE_DIRECTIVE,
        COMMAND_NG_GENERATE_PIPE,
        COMMAND_NG_GENERATE_GUARD,
        COMMAND_NG_GENERATE_INTERFACE
    };

    private static final Set<String> SUPPORTED_SET = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(SUPPORTED_ACTIONS)));

    private final AngularProject project;

    public AngularActionProvider(@NonNull AngularProject project) {
        this.project = project;
    }

    @Override
    public String[] getSupportedActions() {
        return SUPPORTED_ACTIONS.clone();
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        if (!SUPPORTED_SET.contains(command)) {
            return false;
        }
        String target = switch (command) {
            case COMMAND_RUN, COMMAND_NG_SERVE -> "serve";
            case COMMAND_BUILD, COMMAND_REBUILD, COMMAND_NG_BUILD_PROD -> "build";
            case COMMAND_TEST, COMMAND_TEST_SINGLE -> "test";
            case COMMAND_NG_LINT -> "lint";
            case COMMAND_NG_E2E -> "e2e";
            default -> null;
        };
        if (target != null && AngularCliExecutor.eligibleProjects(project.getWorkspace(), List.of(target)).isEmpty()) {
            return false;
        }
        if (COMMAND_TEST_SINGLE.equals(command)) {
            FileObject spec = resolveSpecFile(context);
            return spec != null && FileUtil.isParentOf(project.getProjectDirectory(), spec);
        }
        return true;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        if (!isActionEnabled(command, context)) {
            throw new IllegalArgumentException("Unsupported or unavailable Angular action: " + command);
        }
        org.openide.LifecycleManager.getDefault().saveAll();
        FileObject wsDir = project.getProjectDirectory();
        switch (command) {
            case COMMAND_DEBUG -> org.netbeans.modules.angular.debug.FirefoxDebugger.debug(project);
            case COMMAND_RUN, COMMAND_NG_SERVE ->
                AngularCliExecutor.execute(project, wsDir, context, "serve", "--open"); // NOI18N
            case COMMAND_BUILD ->
                AngularCliExecutor.execute(project, wsDir, context, "build"); // NOI18N
            case COMMAND_REBUILD -> {
                cleanWorkspaceOutput(wsDir);
                AngularCliExecutor.execute(project, wsDir, context, "build"); // NOI18N
            }
            case COMMAND_CLEAN ->
                cleanWorkspaceOutput(wsDir);
            case COMMAND_NG_BUILD_PROD ->
                AngularCliExecutor.execute(project, wsDir, context, "build", "--configuration", "production"); // NOI18N
            case COMMAND_TEST ->
                AngularCliExecutor.execute(project, wsDir, context, "test", "--watch=false");
            case COMMAND_TEST_SINGLE -> {
                FileObject spec = resolveSpecFile(context);
                AngularCliExecutor.execute(project, wsDir, context, "test", "--watch=false", "--include", FileUtil.getRelativePath(wsDir, spec));
            }
            case COMMAND_DEBUG_TEST_SINGLE -> {
                FileObject spec = resolveSpecFile(context);
                throw new UnsupportedOperationException("Angular test debugging is not implemented");
            }
            case COMMAND_NG_LINT ->
                AngularCliExecutor.execute(project, wsDir, context, "lint"); // NOI18N
            case COMMAND_NG_E2E ->
                AngularCliExecutor.execute(project, wsDir, context, "e2e"); // NOI18N
            case COMMAND_NG_GENERATE_COMPONENT ->
                AngularComponentWizardIterator.open(project); // NOI18N
            case COMMAND_NG_GENERATE_SERVICE ->
                AngularCliExecutor.promptAndGenerateSchematic(project, wsDir, "service"); // NOI18N
            case COMMAND_NG_GENERATE_DIRECTIVE ->
                AngularCliExecutor.promptAndGenerateSchematic(project, wsDir, "directive"); // NOI18N
            case COMMAND_NG_GENERATE_PIPE ->
                AngularCliExecutor.promptAndGenerateSchematic(project, wsDir, "pipe"); // NOI18N
            case COMMAND_NG_GENERATE_GUARD ->
                AngularCliExecutor.promptAndGenerateSchematic(project, wsDir, "guard"); // NOI18N
            case COMMAND_NG_GENERATE_INTERFACE ->
                AngularCliExecutor.promptAndGenerateSchematic(project, wsDir, "interface"); // NOI18N
            default -> throw new IllegalArgumentException(command);
        }
    }

    public FileObject resolveSpecFile(Lookup context) {
        if (context == null) {
            return null;
        }
        FileObject fo = context.lookup(FileObject.class);
        if (fo == null) {
            return null;
        }
        if (fo.getNameExt().endsWith(".spec.ts")) { // NOI18N
            return FileUtil.isParentOf(project.getProjectDirectory(), fo) ? fo : null;
        }
        Map<String, FileObject> companions = AngularComponentScanner.getCompanionFiles(fo);
        for (Map.Entry<String, FileObject> entry : companions.entrySet()) {
            if (entry.getKey().endsWith(".spec.ts")) { // NOI18N
                return entry.getValue();
            }
        }
        return null;
    }

    public static String getRelativeSpecPath(FileObject workspaceDir, FileObject specFile) {
        if (workspaceDir == null || specFile == null) {
            return null;
        }
        String rel = FileUtil.getRelativePath(workspaceDir, specFile);
        return rel != null ? rel : specFile.getPath();
    }

    private static void cleanWorkspaceOutput(FileObject wsDir) {
        for (String folder : new String[]{"dist", ".angular/cache"}) { // NOI18N
            FileObject target = wsDir.getFileObject(folder);
            if (target != null && target.isFolder()) {
                try {
                    target.delete();
                } catch (IOException ex) {
                    LOG.log(Level.FINE, "Could not delete " + target.getPath(), ex);
                }
            }
        }
    }
}
