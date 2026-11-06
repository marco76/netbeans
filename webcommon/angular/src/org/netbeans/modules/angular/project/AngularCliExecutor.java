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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import org.netbeans.spi.project.ActionProgress;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.print.LineConvertors;
import org.netbeans.api.extexecution.base.ProcessBuilder;
import org.netbeans.modules.angular.lsp.AngularOptions;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

final class AngularCliExecutor {
    private AngularCliExecutor() {}

    static Future<Integer> executeNgCommand(AngularProject project, FileObject directory, String... arguments) {
        return execute(project, directory, Lookup.EMPTY, arguments);
    }

    static Future<Integer> execute(AngularProject project, FileObject directory, Lookup context, String... arguments) {
        AngularOptions options = AngularOptions.getInstance();
        String node = options.resolveNodeExecutable(project);
        File cli = options.resolveNgCli(directory);
        File workingDirectory = FileUtil.toFile(directory);
        if (node == null || cli == null || workingDirectory == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Install Node.js and the Angular CLI for this workspace before running Angular commands.",
                    NotifyDescriptor.ERROR_MESSAGE));
            return null;
        }
        List<String> requested = new ArrayList<>(Arrays.asList(arguments));
        List<String> projects = eligibleProjects(project.getWorkspace(), requested);
        if (projects.isEmpty() && !requested.isEmpty() && !"generate".equals(requested.get(0))) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "No Angular project defines the " + requested.get(0) + " target in angular.json.",
                    NotifyDescriptor.ERROR_MESSAGE));
            return null;
        }
        if (projects.size() == 1 && requested.stream().noneMatch(arg -> arg.equals("--project") || arg.startsWith("--project="))) {
            requested.add("--project=" + projects.get(0));
        }
        if (projects.size() > 1 && requested.stream().noneMatch(arg -> arg.equals("--project") || arg.startsWith("--project="))) {
            NotifyDescriptor choice = new NotifyDescriptor.QuickPick("Choose an Angular project", "Angular Project",
                    projects.stream().map(name -> new NotifyDescriptor.QuickPick.Item(name, "")).toList(), false);
            if (DialogDisplayer.getDefault().notify(choice) != NotifyDescriptor.OK_OPTION) {
                return null;
            }
            var selected = ((NotifyDescriptor.QuickPick) choice).getItems().stream().filter(item -> item.isSelected()).findFirst();
            if (selected.isEmpty()) return null;
            requested.add("--project=" + selected.get().getLabel());
        }
        List<String> command;
        try {
            command = buildCommand(node, cli, requested, Utilities.isWindows());
        } catch (IllegalArgumentException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            return null;
        }
        ProcessBuilder builder = ProcessBuilder.getLocal();
        builder.setExecutable(command.get(0));
        builder.setArguments(command.subList(1, command.size()));
        builder.setWorkingDirectory(workingDirectory.getAbsolutePath());
        String path = builder.getEnvironment().getVariable("PATH");
        builder.getEnvironment().setVariable("PATH", new File(node).getParent() + File.pathSeparator + (path == null ? "" : path));
        ActionProgress progress = ActionProgress.start(context);
        ExecutionDescriptor descriptor = new ExecutionDescriptor().controllable(true)
                .frontWindow(true).inputVisible(true).showProgress(true)
                .outConvertorFactory(LineConvertors::httpUrl)
                .errConvertorFactory(LineConvertors::httpUrl)
                .postExecution((Integer exitCode) -> {
                    FileUtil.refreshFor(workingDirectory);
                    progress.finished(exitCode == 0);
                });
        try {
            return ExecutionService.newService(builder, descriptor,
                    "Angular " + String.join(" ", arguments)).run();
        } catch (RuntimeException ex) {
            progress.finished(false);
            throw ex;
        }
    }

    static List<String> eligibleProjects(AngularWorkspace workspace, List<String> arguments) {
        if (arguments.isEmpty()) {
            return List.of();
        }
        String command = arguments.get(0);
        return workspace.getProjects().values().stream()
                .filter(project -> "generate".equals(command) || project.getTargets().containsKey(command))
                .map(AngularWorkspace.AngularSubProject::getName)
                .toList();
    }

    static List<String> buildCommand(String node, File cli, List<String> arguments, boolean windows) {
        List<String> command = new ArrayList<>();
        String name = cli.getName().toLowerCase(java.util.Locale.ROOT);
        if (windows && (name.endsWith(".cmd") || name.endsWith(".bat"))) {
            throw new IllegalArgumentException("Select @angular/cli/bin/ng.js instead of a Windows command wrapper in Angular settings.");
        }
        if (!name.endsWith(".exe")) {
            command.add(node);
        }
        command.add(cli.getAbsolutePath());
        command.addAll(arguments);
        return command;
    }

    static void promptAndGenerateSchematic(AngularProject project, FileObject directory, String schematic) {
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine("Name:", "Generate Angular " + schematic);
        if (DialogDisplayer.getDefault().notify(input) == NotifyDescriptor.OK_OPTION) {
            String name = input.getInputText().trim();
            if (!name.isEmpty()) {
                executeNgCommand(project, directory, "generate", schematic, name);
            }
        }
    }
}
