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
package org.netbeans.modules.spring.beans.wizards;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.spring.beans.wizards.SpringInitializrService.DependencyItem;
import org.netbeans.modules.spring.beans.wizards.SpringInitializrService.InitializrMetadata;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.EnableAnnotation;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.SpringModule;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Unit tests for Spring configuration and project wizards, module catalog,
 * and build dependency modification.
 */
public class SpringWizardsTest extends NbTestCase {

    public SpringWizardsTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
    }

    public void testSpringModuleCatalog() {
        List<SpringModule> modules = SpringModuleCatalog.getModules();
        assertFalse(modules.isEmpty());

        SpringModule web = SpringModuleCatalog.findModuleById("web");
        assertNotNull(web);
        assertEquals("org.springframework.boot:spring-boot-starter-web", web.getCoordinates(true));
        assertEquals("org.springframework:spring-webmvc", web.getCoordinates(false));

        Map<String, List<SpringModule>> byCat = SpringModuleCatalog.getModulesByCategory();
        assertTrue(byCat.containsKey(SpringModuleCatalog.CAT_CORE));
        assertTrue(byCat.containsKey(SpringModuleCatalog.CAT_WEB));
        assertTrue(byCat.containsKey(SpringModuleCatalog.CAT_DATA));

        List<EnableAnnotation> enables = SpringModuleCatalog.getEnableAnnotations();
        assertFalse(enables.isEmpty());
    }

    public void testUpdatePomXmlSpringBootStarters() {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <parent>\n"
                + "        <groupId>org.springframework.boot</groupId>\n"
                + "        <artifactId>spring-boot-starter-parent</artifactId>\n"
                + "        <version>3.4.3</version>\n"
                + "    </parent>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>org.springframework.boot</groupId>\n"
                + "            <artifactId>spring-boot-starter-web</artifactId>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        List<SpringModule> selected = Arrays.asList(
                SpringModuleCatalog.findModuleById("web"),
                SpringModuleCatalog.findModuleById("data-jpa"),
                SpringModuleCatalog.findModuleById("test")
        );

        String updated = ProjectSpringDependencyModifier.updatePomXmlContent(pom, selected, true, null);
        // web was already present, should only appear once
        assertEquals(1, countOccurrences(updated, "<artifactId>spring-boot-starter-web</artifactId>"));
        // data-jpa and test should be added without explicit version (managed by parent)
        assertTrue(updated.contains("<artifactId>spring-boot-starter-data-jpa</artifactId>"));
        assertTrue(updated.contains("<artifactId>spring-boot-starter-test</artifactId>"));
        assertTrue(updated.contains("<scope>test</scope>"));
    }

    public void testUpdatePomXmlSpringFrameworkModulesWithoutDependenciesTag() {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>app</artifactId>\n"
                + "</project>\n";

        List<SpringModule> selected = Arrays.asList(
                SpringModuleCatalog.findModuleById("context"),
                SpringModuleCatalog.findModuleById("tx")
        );

        String updated = ProjectSpringDependencyModifier.updatePomXmlContent(pom, selected, false, "6.1.14");
        assertTrue(updated.contains("<dependencies>"));
        assertTrue(updated.contains("<artifactId>spring-context</artifactId>"));
        assertTrue(updated.contains("<artifactId>spring-tx</artifactId>"));
        assertTrue(updated.contains("<version>6.1.14</version>"));
    }

    public void testUpdateGradleGroovyAndKotlinDsl() {
        String groovyGradle = "plugins {\n"
                + "    id 'java'\n"
                + "    id 'org.springframework.boot' version '3.4.3'\n"
                + "    id 'io.spring.dependency-management' version '1.1.7'\n"
                + "}\n\n"
                + "dependencies {\n"
                + "}\n";

        List<SpringModule> selected = Arrays.asList(
                SpringModuleCatalog.findModuleById("web"),
                SpringModuleCatalog.findModuleById("security"),
                SpringModuleCatalog.findModuleById("test")
        );

        String updatedGroovy = ProjectSpringDependencyModifier.updateGradleContent(groovyGradle, false, selected, true, null);
        assertTrue(updatedGroovy.contains("implementation 'org.springframework.boot:spring-boot-starter-web'"));
        assertTrue(updatedGroovy.contains("implementation 'org.springframework.boot:spring-boot-starter-security'"));
        assertTrue(updatedGroovy.contains("testImplementation 'org.springframework.boot:spring-boot-starter-test'"));

        String kotlinGradle = "dependencies {\n}\n";
        String updatedKotlin = ProjectSpringDependencyModifier.updateGradleContent(kotlinGradle, true, selected, false, "6.1.14");
        assertTrue(updatedKotlin.contains("implementation(\"org.springframework:spring-webmvc:6.1.14\")"));
        assertTrue(updatedKotlin.contains("testImplementation(\"org.springframework:spring-test:6.1.14\")"));
    }

    @SuppressWarnings("unchecked")
    public void testBuildJavaConfigTemplateParameters() {
        EnableAnnotation enableWebMvc = SpringModuleCatalog.getEnableAnnotations().get(0);
        EnableAnnotation enableTx = SpringModuleCatalog.getEnableAnnotations().get(1);

        Map<String, Object> params = NewSpringJavaConfigWizardIterator.buildTemplateParameters(
                "com.example.config",
                true,
                "com.example.service, com.example.web",
                "classpath:application.properties",
                "com.example.db.DataConfig, SecurityConfig.class",
                Arrays.asList(enableWebMvc, enableTx),
                true);

        assertEquals("com.example.config", params.get("package"));
        assertEquals(Boolean.TRUE, params.get("componentScan"));
        assertEquals("basePackages = {\"com.example.service\", \"com.example.web\"}", params.get("componentScanValue"));
        assertEquals("\"classpath:application.properties\"", params.get("propertySourceValue"));
        assertEquals("{DataConfig.class, SecurityConfig.class}", params.get("importClassesValue"));

        List<String> enableNames = (List<String>) params.get("enableAnnotations");
        assertEquals(Arrays.asList("EnableWebMvc", "EnableTransactionManagement"), enableNames);

        List<String> imports = (List<String>) params.get("imports");
        assertTrue(imports.contains("org.springframework.context.annotation.Configuration"));
        assertTrue(imports.contains("org.springframework.context.annotation.ComponentScan"));
        assertTrue(imports.contains("org.springframework.context.annotation.PropertySource"));
        assertTrue(imports.contains("org.springframework.context.annotation.Import"));
        assertTrue(imports.contains("com.example.db.DataConfig"));
        assertTrue(imports.contains("org.springframework.web.servlet.config.annotation.EnableWebMvc"));
        assertTrue(imports.contains("org.springframework.transaction.annotation.EnableTransactionManagement"));
    }

    public void testSpringInitializrMetadataParsingAndOfflineGeneration() throws IOException {
        String sampleJson = "{\n"
                + "  \"bootVersion\": {\n"
                + "    \"default\": \"3.4.3.RELEASE\",\n"
                + "    \"values\": [\n"
                + "      {\"id\": \"3.4.3.RELEASE\", \"name\": \"3.4.3\"},\n"
                + "      {\"id\": \"3.3.9.RELEASE\", \"name\": \"3.3.9\"}\n"
                + "    ]\n"
                + "  },\n"
                + "  \"javaVersion\": {\n"
                + "    \"default\": \"21\",\n"
                + "    \"values\": [\n"
                + "      {\"id\": \"21\", \"name\": \"21\"},\n"
                + "      {\"id\": \"17\", \"name\": \"17\"}\n"
                + "    ]\n"
                + "  },\n"
                + "  \"dependencies\": {\n"
                + "    \"values\": [\n"
                + "      {\n"
                + "        \"name\": \"Web\",\n"
                + "        \"values\": [\n"
                + "          {\"id\": \"web\", \"name\": \"Spring Web\", \"description\": \"Build web applications\"}\n"
                + "        ]\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}";

        InitializrMetadata metadata = SpringInitializrService.parseMetadataJson(sampleJson);
        assertNotNull(metadata);
        assertEquals("3.4.3", metadata.getDefaultBootVersion());
        assertEquals(Arrays.asList("3.4.3", "3.3.9"), metadata.getBootVersions());
        assertEquals("21", metadata.getDefaultJavaVersion());
        assertEquals(1, metadata.getDependencies().size());
        assertEquals("web", metadata.getDependencies().get(0).getId());

        File workDir = getWorkDir();
        FileObject mavenDir = FileUtil.createFolder(FileUtil.toFileObject(workDir), "maven-boot-app");
        SpringInitializrService.generateOfflineProject(
                "MAVEN",
                "3.4.3",
                "com.example",
                "maven-boot-app",
                "MavenBootApp",
                "Demo Boot App",
                "com.example.boot",
                "jar",
                "21",
                "java",
                "properties",
                Collections.singletonList(new DependencyItem("web", "Spring Web", "Web", "Web starter")),
                mavenDir);

        FileObject pomFo = mavenDir.getFileObject("pom.xml");
        assertNotNull(pomFo);
        assertTrue(pomFo.asText("UTF-8").contains("spring-boot-starter-web"));
        assertNotNull(mavenDir.getFileObject("src/main/java/com/example/boot/MavenBootAppApplication.java"));
        assertNotNull(mavenDir.getFileObject("src/main/resources/application.properties"));
        assertNotNull(mavenDir.getFileObject("src/test/java/com/example/boot/MavenBootAppApplicationTests.java"));
    }

    @SuppressWarnings("unchecked")
    public void testBuildSpringDataRepositoryTemplateParameters() {
        Map<String, Object> jpaParams = NewSpringDataRepositoryWizardIterator.buildTemplateParameters(
                "com.example.repository",
                "com.example.domain.Customer",
                "long",
                NewSpringDataRepositoryWizardIterator.RepositoryKind.JPA_REPOSITORY,
                true,
                false,
                true,
                "email",
                "java.lang.String");

        assertEquals("com.example.repository", jpaParams.get("package"));
        assertEquals("Customer", jpaParams.get("entitySimpleName"));
        assertEquals("Long", jpaParams.get("idSimpleName"));
        assertEquals(Boolean.FALSE, jpaParams.get("useRepositoryDefinition"));
        assertEquals("JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer>", jpaParams.get("extendsClause"));

        List<String> imports = (List<String>) jpaParams.get("imports");
        assertTrue(imports.contains("com.example.domain.Customer"));
        assertTrue(imports.contains("org.springframework.data.jpa.repository.JpaRepository"));
        assertTrue(imports.contains("org.springframework.data.jpa.repository.JpaSpecificationExecutor"));
        assertTrue(imports.contains("org.springframework.data.jpa.repository.Query"));

        List<String> sampleMethods = (List<String>) jpaParams.get("sampleMethods");
        assertNotNull(sampleMethods);
        assertTrue(sampleMethods.toString().contains("findByEmail(String email)"));
        assertTrue(sampleMethods.toString().contains("@Query(\"SELECT e FROM Customer e WHERE e.email = :email\")"));

        Map<String, Object> repoDefParams = NewSpringDataRepositoryWizardIterator.buildTemplateParameters(
                "com.example.domain",
                "com.example.domain.Order",
                "java.util.UUID",
                NewSpringDataRepositoryWizardIterator.RepositoryKind.REPOSITORY_DEFINITION,
                false,
                true,
                false,
                null,
                null);

        assertEquals("Order", repoDefParams.get("entitySimpleName"));
        assertEquals("UUID", repoDefParams.get("idSimpleName"));
        assertEquals(Boolean.TRUE, repoDefParams.get("useRepositoryDefinition"));
        assertEquals(Boolean.TRUE, repoDefParams.get("addRepositoryAnnotation"));
        assertEquals("", repoDefParams.get("extendsClause"));
        List<String> repoDefImports = (List<String>) repoDefParams.get("imports");
        assertFalse(repoDefImports.contains("com.example.domain.Order"));
        assertTrue(repoDefImports.contains("java.util.UUID"));
        assertTrue(repoDefImports.contains("org.springframework.data.repository.RepositoryDefinition"));
        assertTrue(repoDefImports.contains("org.springframework.stereotype.Repository"));
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
