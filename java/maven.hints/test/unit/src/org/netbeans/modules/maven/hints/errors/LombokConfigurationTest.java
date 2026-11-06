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

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.netbeans.junit.NbTestCase;

public class LombokConfigurationTest extends NbTestCase {
    public LombokConfigurationTest(String name) { super(name); }

    private List<Boolean> references(String source) throws Exception {
        var file = new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignored) { return source; }
        };
        try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, manager, null,
                    List.of("-proc:none"), null, List.of(file));
            CompilationUnitTree unit = task.parse().iterator().next();
            var results = new ArrayList<Boolean>();
            new TreePathScanner<Void, Void>() {
                @Override public Void visitImport(ImportTree tree, Void ignored) {
                    results.add(LombokConfiguration.isLombokReference(unit, getCurrentPath()));
                    return null;
                }
                @Override public Void visitAnnotation(AnnotationTree tree, Void ignored) {
                    results.add(LombokConfiguration.isLombokReference(unit, getCurrentPath()));
                    return null;
                }
            }.scan(unit, null);
            return results;
        }
    }

    public void testExplicitImportAndAnnotation() throws Exception {
        assertEquals(List.of(true, true), references("import lombok.Data; @Data class Example {}"));
    }

    public void testFullyQualifiedAnnotation() throws Exception {
        assertEquals(List.of(true), references("@lombok.Builder class Example {}"));
    }

    public void testOtherLibraryAndBareDataAreIgnored() throws Exception {
        assertEquals(List.of(false, false), references("import other.Data; @Data class Example {}"));
        assertEquals(List.of(false), references("@Data class Example {}"));
        assertEquals(List.of(), references("// import lombok.Data;\nclass Example { String value = \"@lombok.Data\"; }"));
    }

    public void testWildcardWithExplicitOtherImport() throws Exception {
        assertEquals(List.of(true, false, false), references("import lombok.*; import other.Data; @Data class Example {}"));
        assertEquals(List.of(true, true), references("import lombok.*; @Getter class Example {}"));
        assertEquals(List.of(true, false), references("import lombok.*; @Unrelated class Example {}"));
    }

    private MavenProject project(boolean dependency, String configuration) throws Exception {
        var project = new MavenProject();
        project.getModel().setBuild(new Build());
        if (dependency) {
            var lombok = new Dependency();
            lombok.setGroupId("org.projectlombok"); lombok.setArtifactId("lombok");
            project.getModel().addDependency(lombok);
        }
        if (configuration != null) {
            var plugin = new Plugin();
            plugin.setGroupId("org.apache.maven.plugins"); plugin.setArtifactId("maven-compiler-plugin");
            plugin.setVersion("3.14.1");
            plugin.setConfiguration(org.codehaus.plexus.util.xml.Xpp3DomBuilder.build(new java.io.StringReader(configuration)));
            project.getBuild().addPlugin(plugin);
        }
        return project;
    }

    public void testUnsupportedCompilerDoesNotOfferUnusableFix() throws Exception {
        var project = project(false, "<configuration/>");
        project.getBuild().getPlugins().get(0).setVersion("4.0.0-beta-3");
        assertFalse(LombokConfiguration.needsSetup(project));
    }

    public void testMissingDependencyAndProcessorNeedSetup() throws Exception {
        assertTrue(LombokConfiguration.needsSetup(project(false, null)));
        assertTrue(LombokConfiguration.needsSetup(project(true, null)));
        assertTrue(LombokConfiguration.needsSetup(project(true, "<configuration/>")));
    }

    public void testConfiguredProjectIsQuietAndDisabledProcessingIsFlagged() throws Exception {
        String paths = "<annotationProcessorPaths><path><groupId>org.projectlombok</groupId>"
                + "<artifactId>lombok</artifactId></path></annotationProcessorPaths>";
        assertFalse(LombokConfiguration.needsSetup(project(true, "<configuration>" + paths + "</configuration>")));
        assertTrue(LombokConfiguration.needsSetup(project(true, "<configuration><proc>none</proc>" + paths + "</configuration>")));
        var project = project(true, "<configuration>" + paths + "</configuration>");
        project.getProperties().setProperty("maven.compiler.proc", "none");
        assertTrue(LombokConfiguration.needsSetup(project));
        assertTrue(LombokConfiguration.needsSetup(project(true, "<configuration>" + paths
                + "<annotationProcessors><annotationProcessor>other.Processor</annotationProcessor></annotationProcessors></configuration>")));
    }
}
