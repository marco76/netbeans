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
package org.netbeans.modules.maven.hints.errors;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Set;
import javax.swing.Action;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.awt.Actions;
import org.openide.util.ContextAwareAction;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;

final class LombokConfiguration {
    private static final Set<String> WILDCARD_ANNOTATIONS = Set.of(
            "Data", "Value", "Getter", "Setter", "Builder", "SuperBuilder", "With", "WithBy",
            "NoArgsConstructor", "RequiredArgsConstructor", "AllArgsConstructor", "ToString",
            "EqualsAndHashCode", "NonNull", "Cleanup", "SneakyThrows", "Synchronized", "Locked",
            "UtilityClass", "Accessors", "FieldDefaults", "Delegate", "StandardException",
            "Slf4j", "XSlf4j", "Log", "Log4j", "Log4j2", "CommonsLog", "Flogger", "JBossLog", "CustomLog");

    private LombokConfiguration() {}

    static boolean isLombokReference(CompilationUnitTree unit, TreePath path) {
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            Tree leaf = current.getLeaf();
            if (leaf instanceof ImportTree imported) {
                return imported.getQualifiedIdentifier().toString().startsWith("lombok.");
            }
            if (leaf instanceof AnnotationTree annotation) {
                return isLombokAnnotation(annotation.getAnnotationType().toString(),
                        unit.getImports().stream()
                                .filter(i -> !i.isStatic()).map(i -> i.getQualifiedIdentifier().toString()).toList());
            }
        }
        return false;
    }

    static boolean isLombokAnnotation(String name, List<String> imports) {
        if (name.startsWith("lombok.")) {
            return true;
        }
        if (name.contains(".")) {
            return false;
        }
        // An explicit import from another library takes precedence over a wildcard.
        for (String imported : imports) {
            if (imported.endsWith("." + name)) {
                return imported.startsWith("lombok.");
            }
        }
        return WILDCARD_ANNOTATIONS.contains(name)
                && imports.stream().anyMatch(i -> i.startsWith("lombok.") && i.endsWith(".*"));
    }

    static boolean hasDependency(MavenProject model) {
        return model.getDependencies().stream().anyMatch(d -> "org.projectlombok".equals(d.getGroupId())
                && "lombok".equals(d.getArtifactId()));
    }

    static boolean needsSetup(MavenProject model) {
        var compiler = model.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (compiler != null && compiler.getVersion() != null && compiler.getVersion().startsWith("4.")) {
            return false; // The setup action currently supports the 3.x configuration format.
        }
        if (!hasDependency(model) || compiler == null) {
            return true;
        }
        if ("none".equals(model.getProperties().getProperty("maven.compiler.proc"))) {
            return true;
        }
        if (!(compiler.getConfiguration() instanceof Xpp3Dom config)) {
            return true;
        }
        if ("none".equals(value(config, "proc")) || "-proc:none".equals(value(config, "compilerArgument"))) {
            return true;
        }
        var paths = config.getChild("annotationProcessorPaths");
        if (paths == null) {
            return true;
        }
        for (var path : paths.getChildren()) {
            if ("org.projectlombok".equals(value(path, "groupId")) && "lombok".equals(value(path, "artifactId"))) {
                var processors = config.getChild("annotationProcessors");
                if (processors == null) {
                    return false;
                }
                for (var processor : processors.getChildren()) {
                    if ("lombok.launch.AnnotationProcessorHider$AnnotationProcessor".equals(processor.getValue())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static String value(Xpp3Dom parent, String name) {
        var child = parent.getChild(name);
        return child == null ? null : child.getValue();
    }

    @Messages({"FIX_SetupLombok=Set up Lombok", "FIX_RepairLombok=Repair Lombok configuration"})
    static Fix fix(Project project) {
        boolean repair = hasDependency(project.getLookup().lookup(NbMavenProject.class).getMavenProject());
        return new Fix() {
            @Override public String getText() {
                return repair ? Bundle.FIX_RepairLombok() : Bundle.FIX_SetupLombok();
            }

            @Override public ChangeInfo implement() {
                EventQueue.invokeLater(() -> {
                    Action action = Actions.forID("Project", "org.netbeans.modules.maven.actions.SetupLombokAction");
                    if (action instanceof ContextAwareAction contextual) {
                        Action bound = contextual.createContextAwareInstance(Lookups.singleton(project));
                        if (bound.isEnabled()) {
                            bound.actionPerformed(new ActionEvent(project, ActionEvent.ACTION_PERFORMED, "setup-lombok"));
                        }
                    }
                });
                return null;
            }
        };
    }
}
