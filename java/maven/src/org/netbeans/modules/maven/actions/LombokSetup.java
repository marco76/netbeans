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

package org.netbeans.modules.maven.actions;

import javax.xml.namespace.QName;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.netbeans.modules.maven.model.pom.*;

/** Maven-only setup. Uses the effective model to retain parent/BOM configuration. */
final class LombokSetup {
    static final String GROUP = "org.projectlombok";
    static final String ARTIFACT = "lombok";
    static final String VERSION = "1.18.48";
    private static final String COMPILER = "maven-compiler-plugin";
    private static final String PLUGINS = "org.apache.maven.plugins";

    private LombokSetup() {}

    static void validate(MavenProject effective) {
        var plugin = effective.getPlugin(PLUGINS + ":" + COMPILER);
        if (plugin != null && plugin.getVersion() != null && plugin.getVersion().startsWith("4.")) {
            throw new IllegalStateException("Automatic Lombok setup currently supports Maven Compiler Plugin 3.x.");
        }
    }

    static void configure(POMModel model, MavenProject effective) {
        var project = model.getProject();
        Dependency dependency = project.findDependencyById(GROUP, ARTIFACT, null);
        String managedVersion = null;
        if (effective.getDependencyManagement() != null) {
            for (var d : effective.getDependencyManagement().getDependencies()) {
                if (GROUP.equals(d.getGroupId()) && ARTIFACT.equals(d.getArtifactId())) {
                    managedVersion = d.getVersion();
                }
            }
        }
        String version = dependency == null ? null : dependency.getVersion();
        if (version == null) {
            version = managedVersion;
        }
        if (version == null) {
            for (var d : effective.getDependencies()) {
                if (GROUP.equals(d.getGroupId()) && ARTIFACT.equals(d.getArtifactId())) {
                    version = d.getVersion();
                }
            }
        }
        if (version == null) {
            version = VERSION;
        }
        if (dependency == null) {
            dependency = model.getFactory().createDependency();
            dependency.setGroupId(GROUP);
            dependency.setArtifactId(ARTIFACT);
            dependency.setScope("provided");
            project.addDependency(dependency);
        }
        if (dependency.getVersion() == null && managedVersion == null) {
            dependency.setVersion(version);
        }
        Build build = project.getBuild();
        if (build == null) {
            build = model.getFactory().createBuild();
            project.setBuild(build);
        }
        Plugin plugin = build.findPluginById(PLUGINS, COMPILER);
        if (plugin == null) {
            plugin = model.getFactory().createPlugin();
            plugin.setGroupId(PLUGINS);
            plugin.setArtifactId(COMPILER);
            build.addPlugin(plugin);
        }
        var effectivePlugin = effective.getPlugin(PLUGINS + ":" + COMPILER);
        String compilerVersion = effectivePlugin == null ? null : effectivePlugin.getVersion();
        if (compilerVersion == null || new org.apache.maven.artifact.versioning.ComparableVersion(compilerVersion)
                .compareTo(new org.apache.maven.artifact.versioning.ComparableVersion("3.5")) < 0) {
            plugin.setVersion("3.14.1");
        }
        Configuration config = plugin.getConfiguration();
        if (config == null) {
            config = model.getFactory().createConfiguration();
            plugin.setConfiguration(config);
        }
        // Materialize missing inherited parameters, especially processor paths, before extending them.
        if (effectivePlugin != null && effectivePlugin.getConfiguration() instanceof Xpp3Dom inherited) {
            for (Xpp3Dom child : inherited.getChildren()) {
                if (find(config, child.getName()) == null) {
                    config.addExtensibilityElement(copy(model, child));
                }
            }
        }
        POMExtensibilityElement paths = child(config, "annotationProcessorPaths");
        POMExtensibilityElement lombok = null;
        for (var path : paths.getExtensibilityElements()) {
            if (GROUP.equals(value(path, "groupId")) && ARTIFACT.equals(value(path, "artifactId"))) {
                lombok = path;
                break;
            }
        }
        if (lombok == null) {
            lombok = element(model, "path");
            paths.addExtensibilityElement(lombok);
            child(lombok, "groupId").setElementText(GROUP);
            child(lombok, "artifactId").setElementText(ARTIFACT);
            child(lombok, "version").setElementText(version);
        }
        if ("none".equals(config.getSimpleParameter("proc"))
                || "none".equals(effective.getProperties().getProperty("maven.compiler.proc"))) {
            config.setSimpleParameter("proc", "full");
        }
        if ("-proc:none".equals(config.getSimpleParameter("compilerArgument"))) {
            config.setSimpleParameter("compilerArgument", null);
        }
        POMExtensibilityElement processors = find(config, "annotationProcessors");
        if (processors != null && processors.getExtensibilityElements().stream().noneMatch(
                p -> "lombok.launch.AnnotationProcessorHider$AnnotationProcessor".equals(p.getElementText()))) {
            var processor = element(model, "annotationProcessor");
            processor.setElementText("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
            processors.addExtensibilityElement(processor);
        }
    }

    static boolean isConfigured(POMModel model) {
        var project = model.getProject();
        var plugin = project.getBuild() == null ? null : project.getBuild().findPluginById(PLUGINS, COMPILER);
        var config = plugin == null ? null : plugin.getConfiguration();
        var paths = config == null ? null : find(config, "annotationProcessorPaths");
        return project.findDependencyById(GROUP, ARTIFACT, null) != null && paths != null
                && paths.getExtensibilityElements().stream().anyMatch(p -> GROUP.equals(value(p, "groupId"))
                && ARTIFACT.equals(value(p, "artifactId")));
    }

    private static String value(POMComponent parent, String name) {
        var element = find(parent, name);
        return element == null ? null : element.getElementText();
    }

    private static POMExtensibilityElement find(POMComponent parent, String name) {
        return parent.getExtensibilityElements().stream()
                .filter(e -> name.equals(e.getQName().getLocalPart())).findFirst().orElse(null);
    }

    private static POMExtensibilityElement child(POMComponent parent, String name) {
        var result = find(parent, name);
        if (result == null) {
            result = element(parent.getModel(), name);
            parent.addExtensibilityElement(result);
        }
        return result;
    }

    private static POMExtensibilityElement element(POMModel model, String name) {
        return model.getFactory().createPOMExtensibilityElement(new QName(
                model.getPOMQNames().PROJECT.getQName().getNamespaceURI(), name));
    }

    private static POMExtensibilityElement copy(POMModel model, Xpp3Dom source) {
        var result = element(model, source.getName());
        for (String name : source.getAttributeNames()) {
            result.setAttribute(name, source.getAttribute(name));
        }
        if (source.getValue() != null) {
            result.setElementText(source.getValue());
        }
        for (Xpp3Dom child : source.getChildren()) {
            result.addExtensibilityElement(copy(model, child));
        }
        return result;
    }
}
