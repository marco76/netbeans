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

import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.maven.model.Utilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.test.TestFileUtils;

public class LombokSetupTest extends NbTestCase {
    public LombokSetupTest(String name) { super(name); }

    @Override protected void setUp() throws Exception { clearWorkDir(); }

    private FileObject pom(String body) throws Exception {
        return TestFileUtils.writeFile(FileUtil.toFileObject(getWorkDir()), "pom.xml",
                "<project xmlns='http://maven.apache.org/POM/4.0.0'><modelVersion>4.0.0</modelVersion>"
                + "<groupId>test</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>");
    }

    private void setup(FileObject pom, MavenProject effective) {
        LombokSetup.validate(effective);
        Utilities.performPOMModelOperations(pom, List.of(m -> LombokSetup.configure(m, effective)));
    }

    public void testFreshProjectAndRepeatedSetup() throws Exception {
        var pom = pom("");
        setup(pom, new MavenProject());
        String once = pom.asText();
        assertTrue(once.contains("<scope>provided</scope>"));
        assertTrue(once.contains("<annotationProcessorPaths>"));
        assertTrue(once.contains(LombokSetup.VERSION));
        setup(pom, new MavenProject());
        assertEquals(once, pom.asText());
        Utilities.performPOMModelOperations(pom, List.of(m -> assertTrue(LombokSetup.isConfigured(m))));
    }

    public void testManagedVersionStaysManaged() throws Exception {
        var effective = new MavenProject();
        var management = new DependencyManagement();
        var lombok = new Dependency();
        lombok.setGroupId(LombokSetup.GROUP);
        lombok.setArtifactId(LombokSetup.ARTIFACT);
        lombok.setVersion("1.18.40");
        management.addDependency(lombok);
        effective.getModel().setDependencyManagement(management);
        var pom = pom("");
        setup(pom, effective);
        Utilities.performPOMModelOperations(pom, List.of(m -> assertNull(m.getProject()
                .findDependencyById(LombokSetup.GROUP, LombokSetup.ARTIFACT, null).getVersion())));
        assertTrue(pom.asText().contains("<version>1.18.40</version>"));
        assertFalse(pom.asText().contains(LombokSetup.VERSION));
    }

    public void testPreservesExistingVersionAndOtherProcessors() throws Exception {
        var pom = pom("""
            <dependencies><dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId>
            <version>${lombok.version}</version><scope>provided</scope></dependency></dependencies>
            <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version>
            <configuration><release>21</release><proc>none</proc><annotationProcessorPaths>
            <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>1.6.3</version></path>
            </annotationProcessorPaths><annotationProcessors><annotationProcessor>other.Processor</annotationProcessor>
            </annotationProcessors></configuration></plugin></plugins></build>
            """);
        setup(pom, new MavenProject());
        String result = pom.asText();
        assertTrue(result.contains("mapstruct-processor"));
        assertTrue(result.contains("<release>21</release>"));
        assertTrue(result.contains("<proc>full</proc>"));
        assertTrue(result.contains("other.Processor"));
        assertTrue(result.contains("lombok.launch.AnnotationProcessorHider$AnnotationProcessor"));
        assertFalse(result.contains(LombokSetup.VERSION));
        setup(pom, new MavenProject());
        assertEquals(result, pom.asText());
    }

    public void testPreservesInheritedProcessorPaths() throws Exception {
        var effective = new MavenProject();
        var build = new Build();
        effective.getModel().setBuild(build);
        var compiler = new Plugin();
        compiler.setGroupId("org.apache.maven.plugins");
        compiler.setArtifactId("maven-compiler-plugin");
        compiler.setVersion("3.14.1");
        var config = new Xpp3Dom("configuration");
        var paths = new Xpp3Dom("annotationProcessorPaths");
        var path = new Xpp3Dom("path");
        var artifact = new Xpp3Dom("artifactId");
        artifact.setValue("existing-processor");
        path.addChild(artifact); paths.addChild(path); config.addChild(paths);
        compiler.setConfiguration(config); build.addPlugin(compiler);
        var pom = pom("");
        setup(pom, effective);
        assertTrue(pom.asText().contains("existing-processor"));
        assertTrue(pom.asText().contains("<artifactId>lombok</artifactId>"));
        String once = pom.asText();
        setup(pom, effective);
        assertEquals(once, pom.asText());
    }

    public void testCompilerFourIsRejectedBeforeEditing() throws Exception {
        var effective = new MavenProject();
        effective.getModel().setBuild(new Build());
        var compiler = new Plugin();
        compiler.setGroupId("org.apache.maven.plugins");
        compiler.setArtifactId("maven-compiler-plugin");
        compiler.setVersion("4.0.0-beta-3");
        effective.getBuild().addPlugin(compiler);
        var pom = pom("");
        String original = pom.asText();
        try {
            setup(pom, effective);
            fail("Compiler 4 must not be configured using the 3.x format");
        } catch (IllegalStateException expected) {
            assertEquals(original, pom.asText());
        }
    }
}
