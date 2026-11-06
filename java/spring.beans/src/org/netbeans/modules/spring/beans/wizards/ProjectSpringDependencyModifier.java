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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.project.classpath.ProjectClassPathModifier;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.SpringModule;
import org.openide.filesystems.FileObject;

/**
 * Inspects and updates project build configurations (Maven {@code pom.xml},
 * Gradle {@code build.gradle} / {@code build.gradle.kts}, or Ant classpath libraries)
 * to import selected Spring Framework / Spring Boot modules.
 */
public final class ProjectSpringDependencyModifier {

    public static final String DEFAULT_SPRING_FRAMEWORK_VERSION = "6.1.14"; //NOI18N
    private static final String SPRING_BOOT_APPLICATION_CLASS = "org/springframework/boot/SpringApplication.class"; //NOI18N

    private static final Pattern DEPENDENCIES_BLOCK_PATTERN = Pattern.compile(
            "(?s)\\bdependencies\\s*\\{(.*?)\\}"); //NOI18N

    public enum BuildType {
        MAVEN,
        GRADLE,
        ANT
    }

    private ProjectSpringDependencyModifier() {
    }

    public static BuildType detectBuildType(Project project) {
        if (project == null || project.getProjectDirectory() == null) {
            return BuildType.ANT;
        }
        FileObject dir = project.getProjectDirectory();
        if (dir.getFileObject("pom.xml") != null) { //NOI18N
            return BuildType.MAVEN;
        }
        if (dir.getFileObject("build.gradle") != null || dir.getFileObject("build.gradle.kts") != null) { //NOI18N
            return BuildType.GRADLE;
        }
        return BuildType.ANT;
    }

    public static boolean isSpringBootProject(Project project, ClassPath compileClassPath) {
        if (compileClassPath != null && compileClassPath.findResource(SPRING_BOOT_APPLICATION_CLASS) != null) {
            return true;
        }
        if (project == null || project.getProjectDirectory() == null) {
            return false;
        }
        FileObject dir = project.getProjectDirectory();
        try {
            FileObject pom = dir.getFileObject("pom.xml"); //NOI18N
            if (pom != null) {
                String text = pom.asText("UTF-8"); //NOI18N
                if (text.contains("spring-boot")) { //NOI18N
                    return true;
                }
            }
            FileObject gradle = dir.getFileObject("build.gradle"); //NOI18N
            if (gradle == null) {
                gradle = dir.getFileObject("build.gradle.kts"); //NOI18N
            }
            if (gradle != null) {
                String text = gradle.asText("UTF-8"); //NOI18N
                if (text.contains("org.springframework.boot") || text.contains("spring-boot")) { //NOI18N
                    return true;
                }
            }
        } catch (IOException ex) {
            // ignore
        }
        return false;
    }

    public static Set<String> detectPresentModuleIds(Project project, ClassPath compileClassPath) {
        Set<String> present = new LinkedHashSet<>();
        String buildContent = readBuildFileContent(project);
        for (SpringModule module : SpringModuleCatalog.getModules()) {
            if (compileClassPath != null && module.getMarkerClass() != null) {
                String res = module.getMarkerClass().replace('.', '/') + ".class"; //NOI18N
                if (compileClassPath.findResource(res) != null) {
                    present.add(module.getId());
                    continue;
                }
            }
            if (buildContent != null) {
                if ((module.getBootArtifactId() != null && buildContent.contains(module.getBootArtifactId()))
                        || (module.getFrameworkArtifactId() != null && buildContent.contains(module.getFrameworkArtifactId()))) {
                    present.add(module.getId());
                }
            }
        }
        return Collections.unmodifiableSet(present);
    }

    private static String readBuildFileContent(Project project) {
        if (project == null || project.getProjectDirectory() == null) {
            return null;
        }
        FileObject dir = project.getProjectDirectory();
        try {
            FileObject pom = dir.getFileObject("pom.xml"); //NOI18N
            if (pom != null) {
                return pom.asText("UTF-8"); //NOI18N
            }
            FileObject gradle = dir.getFileObject("build.gradle"); //NOI18N
            if (gradle == null) {
                gradle = dir.getFileObject("build.gradle.kts"); //NOI18N
            }
            if (gradle != null) {
                return gradle.asText("UTF-8"); //NOI18N
            }
        } catch (IOException ex) {
            // ignore
        }
        return null;
    }

    public static void addSpringModules(
            Project project,
            FileObject targetSourceArtifact,
            Collection<SpringModule> modules,
            boolean springBootMode,
            String springVersion,
            Library antLibrary) throws IOException {
        if (project == null) {
            return;
        }
        BuildType buildType = detectBuildType(project);
        FileObject dir = project.getProjectDirectory();
        if (buildType == BuildType.MAVEN) {
            FileObject pom = dir.getFileObject("pom.xml"); //NOI18N
            if (pom != null && modules != null && !modules.isEmpty()) {
                String orig = pom.asText("UTF-8"); //NOI18N
                String updated = updatePomXmlContent(orig, modules, springBootMode, springVersion);
                if (!updated.equals(orig)) {
                    writeFileObject(pom, updated);
                }
            }
        } else if (buildType == BuildType.GRADLE) {
            FileObject gradle = dir.getFileObject("build.gradle"); //NOI18N
            boolean kotlinDsl = false;
            if (gradle == null) {
                gradle = dir.getFileObject("build.gradle.kts"); //NOI18N
                kotlinDsl = true;
            }
            if (gradle != null && modules != null && !modules.isEmpty()) {
                String orig = gradle.asText("UTF-8"); //NOI18N
                String updated = updateGradleContent(orig, kotlinDsl, modules, springBootMode, springVersion);
                if (!updated.equals(orig)) {
                    writeFileObject(gradle, updated);
                }
            }
        } else if (antLibrary != null && targetSourceArtifact != null) {
            ProjectClassPathModifier.addLibraries(new Library[]{antLibrary}, targetSourceArtifact, ClassPath.COMPILE);
        }
    }

    static String updatePomXmlContent(
            String pomContent,
            Collection<SpringModule> modules,
            boolean springBoot,
            String explicitVersion) {
        if (pomContent == null || modules == null || modules.isEmpty()) {
            return pomContent;
        }
        boolean hasManagedVersion = pomContent.contains("spring-boot-starter-parent") //NOI18N
                || pomContent.contains("spring-boot-dependencies") //NOI18N
                || pomContent.contains("spring-framework-bom"); //NOI18N
        String effectiveVersion = (explicitVersion != null && !explicitVersion.trim().isEmpty())
                ? explicitVersion.trim()
                : DEFAULT_SPRING_FRAMEWORK_VERSION;

        String lineSep = pomContent.contains("\r\n") ? "\r\n" : "\n"; //NOI18N
        StringBuilder newDeps = new StringBuilder();
        Set<String> addedArtifacts = new LinkedHashSet<>();

        for (SpringModule module : modules) {
            String groupId = springBoot ? module.getBootGroupId() : module.getFrameworkGroupId();
            String artifactId = springBoot ? module.getBootArtifactId() : module.getFrameworkArtifactId();
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (addedArtifacts.contains(artifactId)
                    || pomContent.contains("<artifactId>" + artifactId + "</artifactId>")) { //NOI18N
                continue;
            }
            addedArtifacts.add(artifactId);

            newDeps.append("        <dependency>").append(lineSep); //NOI18N
            newDeps.append("            <groupId>").append(groupId).append("</groupId>").append(lineSep); //NOI18N
            newDeps.append("            <artifactId>").append(artifactId).append("</artifactId>").append(lineSep); //NOI18N
            if (!springBoot && !hasManagedVersion) {
                newDeps.append("            <version>").append(effectiveVersion).append("</version>").append(lineSep); //NOI18N
            }
            if (module.isTestScope()) {
                newDeps.append("            <scope>test</scope>").append(lineSep); //NOI18N
            }
            newDeps.append("        </dependency>").append(lineSep); //NOI18N
        }

        if (newDeps.length() == 0) {
            return pomContent;
        }

        // Find last </dependencies> outside <dependencyManagement>
        int depMgmtStart = pomContent.indexOf("<dependencyManagement>"); //NOI18N
        int depMgmtEnd = pomContent.indexOf("</dependencyManagement>"); //NOI18N
        int insertCloseDeps = -1;
        int searchFrom = 0;
        while (true) {
            int idx = pomContent.indexOf("</dependencies>", searchFrom); //NOI18N
            if (idx < 0) {
                break;
            }
            if (depMgmtStart < 0 || idx < depMgmtStart || (depMgmtEnd >= 0 && idx > depMgmtEnd)) {
                insertCloseDeps = idx;
                break;
            }
            searchFrom = idx + "</dependencies>".length(); //NOI18N
        }

        if (insertCloseDeps >= 0) {
            int lineStart = insertCloseDeps;
            while (lineStart > 0 && (pomContent.charAt(lineStart - 1) == ' ' || pomContent.charAt(lineStart - 1) == '\t')) {
                lineStart--;
            }
            return pomContent.substring(0, lineStart)
                    + newDeps
                    + pomContent.substring(lineStart);
        }

        int closeProject = pomContent.lastIndexOf("</project>"); //NOI18N
        if (closeProject >= 0) {
            String block = "    <dependencies>" + lineSep //NOI18N
                    + newDeps
                    + "    </dependencies>" + lineSep; //NOI18N
            return pomContent.substring(0, closeProject)
                    + block
                    + pomContent.substring(closeProject);
        }
        return pomContent;
    }

    static String updateGradleContent(
            String gradleContent,
            boolean kotlinDsl,
            Collection<SpringModule> modules,
            boolean springBoot,
            String explicitVersion) {
        if (gradleContent == null || modules == null || modules.isEmpty()) {
            return gradleContent;
        }
        boolean hasDependencyManagement = gradleContent.contains("io.spring.dependency-management") //NOI18N
                || gradleContent.contains("org.springframework.boot") //NOI18N
                || gradleContent.contains("platform("); //NOI18N
        String effectiveVersion = (explicitVersion != null && !explicitVersion.trim().isEmpty())
                ? explicitVersion.trim()
                : DEFAULT_SPRING_FRAMEWORK_VERSION;

        String lineSep = gradleContent.contains("\r\n") ? "\r\n" : "\n"; //NOI18N
        StringBuilder newLines = new StringBuilder();
        Set<String> addedArtifacts = new LinkedHashSet<>();

        for (SpringModule module : modules) {
            String groupId = springBoot ? module.getBootGroupId() : module.getFrameworkGroupId();
            String artifactId = springBoot ? module.getBootArtifactId() : module.getFrameworkArtifactId();
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (addedArtifacts.contains(artifactId) || gradleContent.contains(artifactId)) {
                continue;
            }
            addedArtifacts.add(artifactId);

            String scope = module.isTestScope() ? "testImplementation" : "implementation"; //NOI18N
            String coord = groupId + ":" + artifactId; //NOI18N
            if (!springBoot && !hasDependencyManagement) {
                coord = coord + ":" + effectiveVersion; //NOI18N
            }
            if (kotlinDsl) {
                newLines.append("    ").append(scope).append("(\"").append(coord).append("\")").append(lineSep); //NOI18N
            } else {
                newLines.append("    ").append(scope).append(" '").append(coord).append("'").append(lineSep); //NOI18N
            }
        }

        if (newLines.length() == 0) {
            return gradleContent;
        }

        Matcher matcher = DEPENDENCIES_BLOCK_PATTERN.matcher(gradleContent);
        if (matcher.find()) {
            int closeBrace = matcher.end() - 1;
            int lineStart = closeBrace;
            while (lineStart > 0 && (gradleContent.charAt(lineStart - 1) == ' ' || gradleContent.charAt(lineStart - 1) == '\t')) {
                lineStart--;
            }
            return gradleContent.substring(0, lineStart)
                    + newLines
                    + gradleContent.substring(lineStart);
        }

        return gradleContent
                + (gradleContent.endsWith("\n") ? "" : lineSep) //NOI18N
                + lineSep
                + "dependencies {" + lineSep //NOI18N
                + newLines
                + "}" + lineSep; //NOI18N
    }

    private static void writeFileObject(FileObject fo, String content) throws IOException {
        try (OutputStream out = fo.getOutputStream()) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
