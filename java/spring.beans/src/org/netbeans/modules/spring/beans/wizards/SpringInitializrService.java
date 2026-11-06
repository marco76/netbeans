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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.SpringModule;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Service managing metadata queries and project generation via Spring Initializr
 * ({@code https://start.spring.io}) with an offline project generator fallback.
 */
public final class SpringInitializrService {

    public static final String DEFAULT_SERVICE_URL = "https://start.spring.io"; //NOI18N
    private static final int TIMEOUT_MS = 4500;

    private static final SpringInitializrService INSTANCE = new SpringInitializrService();

    private SpringInitializrService() {
    }

    public static SpringInitializrService getInstance() {
        return INSTANCE;
    }

    public InitializrMetadata getMetadata(String serviceUrl) {
        if (serviceUrl != null && !serviceUrl.trim().isEmpty()) {
            try {
                String base = normalizeServiceUrl(serviceUrl);
                String json = fetchText(base + "metadata/client"); //NOI18N
                InitializrMetadata parsed = parseMetadataJson(json);
                if (parsed != null && !parsed.getBootVersions().isEmpty()) {
                    return parsed;
                }
            } catch (Exception ex) {
                // Fall back to built-in metadata when offline or unreachable
            }
        }
        return createFallbackMetadata();
    }

    public void createProject(
            String serviceUrl,
            String buildTool,
            String bootVersion,
            String group,
            String artifact,
            String projectName,
            String description,
            String packageName,
            String packaging,
            String javaVersion,
            String language,
            String configFormat,
            Collection<DependencyItem> dependencies,
            FileObject targetDir) throws IOException {
        boolean extractedFromService = false;
        if (serviceUrl != null && !serviceUrl.trim().isEmpty() && !serviceUrl.startsWith("offline:")) { //NOI18N
            try {
                String url = buildStarterZipUrl(
                        serviceUrl, buildTool, bootVersion, group, artifact,
                        projectName, description, packageName, packaging,
                        javaVersion, language, configFormat, dependencies);
                try (InputStream in = openStream(url)) {
                    unzip(in, targetDir, artifact + "/"); //NOI18N
                    extractedFromService = true;
                }
            } catch (IOException ex) {
                // Fall back to local offline project generation
            }
        }
        if (!extractedFromService) {
            generateOfflineProject(
                    buildTool, bootVersion, group, artifact,
                    projectName, description, packageName, packaging,
                    javaVersion, language, configFormat, dependencies, targetDir);
        }
    }

    String buildStarterZipUrl(
            String serviceUrl,
            String buildTool,
            String bootVersion,
            String group,
            String artifact,
            String projectName,
            String description,
            String packageName,
            String packaging,
            String javaVersion,
            String language,
            String configFormat,
            Collection<DependencyItem> dependencies) {
        String base = normalizeServiceUrl(serviceUrl);
        String type = "GRADLE".equalsIgnoreCase(buildTool) ? "gradle-project" : "maven-project"; //NOI18N
        StringBuilder sb = new StringBuilder(base).append("starter.zip"); //NOI18N
        sb.append("?type=").append(urlEncode(type)); //NOI18N
        sb.append("&language=").append(urlEncode(language != null ? language.toLowerCase(Locale.ROOT) : "java")); //NOI18N
        sb.append("&bootVersion=").append(urlEncode(bootVersion != null ? bootVersion : "3.4.3")); //NOI18N
        sb.append("&baseDir=").append(urlEncode(artifact)); //NOI18N
        sb.append("&groupId=").append(urlEncode(group)); //NOI18N
        sb.append("&artifactId=").append(urlEncode(artifact)); //NOI18N
        sb.append("&name=").append(urlEncode(projectName)); //NOI18N
        sb.append("&description=").append(urlEncode(description != null ? description : "")); //NOI18N
        sb.append("&packageName=").append(urlEncode(packageName)); //NOI18N
        sb.append("&packaging=").append(urlEncode(packaging != null ? packaging.toLowerCase(Locale.ROOT) : "jar")); //NOI18N
        sb.append("&javaVersion=").append(urlEncode(javaVersion != null ? javaVersion : "21")); //NOI18N
        if (configFormat != null && !configFormat.isEmpty()) {
            sb.append("&configurationFileFormat=").append(urlEncode(configFormat.toLowerCase(Locale.ROOT))); //NOI18N
        }
        if (dependencies != null && !dependencies.isEmpty()) {
            List<String> ids = new ArrayList<>(dependencies.size());
            for (DependencyItem item : dependencies) {
                ids.add(item.getId());
            }
            sb.append("&dependencies=").append(urlEncode(String.join(",", ids))); //NOI18N
        }
        return sb.toString();
    }

    public static void generateOfflineProject(
            String buildTool,
            String bootVersion,
            String group,
            String artifact,
            String projectName,
            String description,
            String packageName,
            String packaging,
            String javaVersion,
            String language,
            String configFormat,
            Collection<DependencyItem> dependencies,
            FileObject targetDir) throws IOException {
        String effectiveBootVersion = (bootVersion != null && !bootVersion.trim().isEmpty()) ? bootVersion.trim() : "3.4.3"; //NOI18N
        String effectiveGroup = (group != null && !group.trim().isEmpty()) ? group.trim() : "com.example"; //NOI18N
        String effectiveArtifact = (artifact != null && !artifact.trim().isEmpty()) ? artifact.trim() : "demo"; //NOI18N
        String effectiveName = (projectName != null && !projectName.trim().isEmpty()) ? projectName.trim() : effectiveArtifact;
        String effectiveDesc = (description != null && !description.trim().isEmpty())
                ? description.trim()
                : "Spring Boot project for " + effectiveName; //NOI18N
        String effectivePkg = (packageName != null && !packageName.trim().isEmpty())
                ? packageName.trim()
                : effectiveGroup + "." + effectiveArtifact.replace('-', '.'); //NOI18N
        String effectivePackaging = (packaging != null && !packaging.trim().isEmpty())
                ? packaging.trim().toLowerCase(Locale.ROOT)
                : "jar"; //NOI18N
        String effectiveJava = (javaVersion != null && !javaVersion.trim().isEmpty()) ? javaVersion.trim() : "17"; //NOI18N

        Set<SpringModule> modules = new LinkedHashSet<>();
        if (dependencies != null) {
            for (DependencyItem dep : dependencies) {
                SpringModule catalogMod = SpringModuleCatalog.findModuleById(dep.getId());
                if (catalogMod != null) {
                    modules.add(catalogMod);
                } else {
                    modules.add(new SpringModule(
                            dep.getId(), dep.getName(), dep.getCategory(), dep.getDescription(),
                            "org.springframework.boot", "spring-boot-starter-" + dep.getId(), //NOI18N
                            "org.springframework", "spring-" + dep.getId(), //NOI18N
                            null, null, false));
                }
            }
        }
        if (modules.isEmpty()) {
            SpringModule coreMod = SpringModuleCatalog.findModuleById("context"); //NOI18N
            if (coreMod != null) {
                modules.add(coreMod);
            }
        }
        SpringModule testMod = SpringModuleCatalog.findModuleById("test"); //NOI18N
        if (testMod != null) {
            modules.add(testMod);
        }

        if ("GRADLE".equalsIgnoreCase(buildTool)) { //NOI18N
            String settingsGradle = "rootProject.name = '" + effectiveArtifact + "'\n"; //NOI18N
            writeText(targetDir, "settings.gradle", settingsGradle); //NOI18N

            String baseGradle = "plugins {\n" //NOI18N
                    + "    id 'java'\n" //NOI18N
                    + "    id 'org.springframework.boot' version '" + effectiveBootVersion + "'\n" //NOI18N
                    + "    id 'io.spring.dependency-management' version '1.1.7'\n" //NOI18N
                    + "}\n\n" //NOI18N
                    + "group = '" + effectiveGroup + "'\n" //NOI18N
                    + "version = '0.0.1-SNAPSHOT'\n\n" //NOI18N
                    + "java {\n" //NOI18N
                    + "    toolchain {\n" //NOI18N
                    + "        languageVersion = JavaLanguageVersion.of(" + effectiveJava + ")\n" //NOI18N
                    + "    }\n" //NOI18N
                    + "}\n\n" //NOI18N
                    + "repositories {\n" //NOI18N
                    + "    mavenCentral()\n" //NOI18N
                    + "}\n\n" //NOI18N
                    + "dependencies {\n" //NOI18N
                    + "}\n\n" //NOI18N
                    + "tasks.named('test') {\n" //NOI18N
                    + "    useJUnitPlatform()\n" //NOI18N
                    + "}\n"; //NOI18N
            String fullGradle = ProjectSpringDependencyModifier.updateGradleContent(
                    baseGradle, false, modules, true, effectiveBootVersion);
            writeText(targetDir, "build.gradle", fullGradle); //NOI18N
        } else {
            String basePom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" //NOI18N
                    + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" //NOI18N
                    + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" //NOI18N
                    + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" //NOI18N
                    + "    <modelVersion>4.0.0</modelVersion>\n" //NOI18N
                    + "    <parent>\n" //NOI18N
                    + "        <groupId>org.springframework.boot</groupId>\n" //NOI18N
                    + "        <artifactId>spring-boot-starter-parent</artifactId>\n" //NOI18N
                    + "        <version>" + effectiveBootVersion + "</version>\n" //NOI18N
                    + "        <relativePath/> <!-- lookup parent from repository -->\n" //NOI18N
                    + "    </parent>\n" //NOI18N
                    + "    <groupId>" + effectiveGroup + "</groupId>\n" //NOI18N
                    + "    <artifactId>" + effectiveArtifact + "</artifactId>\n" //NOI18N
                    + "    <version>0.0.1-SNAPSHOT</version>\n" //NOI18N
                    + "    <packaging>" + effectivePackaging + "</packaging>\n" //NOI18N
                    + "    <name>" + effectiveName + "</name>\n" //NOI18N
                    + "    <description>" + effectiveDesc + "</description>\n" //NOI18N
                    + "    <properties>\n" //NOI18N
                    + "        <java.version>" + effectiveJava + "</java.version>\n" //NOI18N
                    + "    </properties>\n" //NOI18N
                    + "    <dependencies>\n" //NOI18N
                    + "    </dependencies>\n" //NOI18N
                    + "    <build>\n" //NOI18N
                    + "        <plugins>\n" //NOI18N
                    + "            <plugin>\n" //NOI18N
                    + "                <groupId>org.springframework.boot</groupId>\n" //NOI18N
                    + "                <artifactId>spring-boot-maven-plugin</artifactId>\n" //NOI18N
                    + "            </plugin>\n" //NOI18N
                    + "        </plugins>\n" //NOI18N
                    + "    </build>\n" //NOI18N
                    + "</project>\n"; //NOI18N
            String fullPom = ProjectSpringDependencyModifier.updatePomXmlContent(
                    basePom, modules, true, effectiveBootVersion);
            writeText(targetDir, "pom.xml", fullPom); //NOI18N
        }

        String appClassName = toClassName(effectiveName) + "Application"; //NOI18N
        String pkgPath = effectivePkg.replace('.', '/');
        String appJava = "package " + effectivePkg + ";\n\n" //NOI18N
                + "import org.springframework.boot.SpringApplication;\n" //NOI18N
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n" //NOI18N
                + "@SpringBootApplication\n" //NOI18N
                + "public class " + appClassName + " {\n\n" //NOI18N
                + "    public static void main(String[] args) {\n" //NOI18N
                + "        SpringApplication.run(" + appClassName + ".class, args);\n" //NOI18N
                + "    }\n" //NOI18N
                + "}\n"; //NOI18N
        writeText(targetDir, "src/main/java/" + pkgPath + "/" + appClassName + ".java", appJava); //NOI18N

        if ("yaml".equalsIgnoreCase(configFormat) || "yml".equalsIgnoreCase(configFormat)) { //NOI18N
            String yml = "spring:\n  application:\n    name: " + effectiveArtifact + "\n"; //NOI18N
            writeText(targetDir, "src/main/resources/application.yml", yml); //NOI18N
        } else {
            String props = "spring.application.name=" + effectiveArtifact + "\n"; //NOI18N
            writeText(targetDir, "src/main/resources/application.properties", props); //NOI18N
        }

        String testClassName = appClassName + "Tests"; //NOI18N
        String testJava = "package " + effectivePkg + ";\n\n" //NOI18N
                + "import org.junit.jupiter.api.Test;\n" //NOI18N
                + "import org.springframework.boot.test.context.SpringBootTest;\n\n" //NOI18N
                + "@SpringBootTest\n" //NOI18N
                + "class " + testClassName + " {\n\n" //NOI18N
                + "    @Test\n" //NOI18N
                + "    void contextLoads() {\n" //NOI18N
                + "    }\n" //NOI18N
                + "}\n"; //NOI18N
        writeText(targetDir, "src/test/java/" + pkgPath + "/" + testClassName + ".java", testJava); //NOI18N
    }

    private static String toClassName(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                if (sb.length() == 0 && Character.isDigit(ch)) {
                    sb.append('_');
                }
                sb.append(capitalizeNext ? Character.toUpperCase(ch) : ch);
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }
        return sb.length() > 0 ? sb.toString() : "Demo"; //NOI18N
    }

    private static void writeText(FileObject baseDir, String relativePath, String content) throws IOException {
        FileObject fo = FileUtil.createData(baseDir, relativePath);
        try (OutputStream out = fo.getOutputStream()) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void unzip(InputStream stream, FileObject folder, String prefix) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(stream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                if (entryName.startsWith(prefix)) {
                    entryName = entryName.substring(prefix.length());
                }
                if (entryName.isEmpty()) {
                    continue;
                }
                if (zipEntry.isDirectory()) {
                    FileUtil.createFolder(folder, entryName);
                } else {
                    FileObject fo = FileUtil.createData(folder, entryName);
                    try (OutputStream out = fo.getOutputStream()) {
                        zis.transferTo(out);
                    }
                    File backingFile = FileUtil.toFile(fo);
                    if (backingFile != null && ("mvnw".equals(entryName) || "gradlew".equals(entryName))) { //NOI18N
                        backingFile.setExecutable(true);
                    }
                }
            }
        }
    }

    static InitializrMetadata createFallbackMetadata() {
        List<String> bootVersions = Arrays.asList("3.4.3", "3.3.9", "3.2.12", "2.7.18"); //NOI18N
        List<String> javaVersions = Arrays.asList("21", "17", "11"); //NOI18N
        List<DependencyItem> deps = new ArrayList<>();
        for (SpringModule m : SpringModuleCatalog.getModules()) {
            if (!m.isTestScope() && !"context".equals(m.getId())) { //NOI18N
                deps.add(new DependencyItem(m.getId(), m.getTitle(), m.getCategory(), m.getDescription()));
            }
        }
        return new InitializrMetadata("3.4.3", bootVersions, "21", javaVersions, deps, true); //NOI18N
    }

    @SuppressWarnings("unchecked")
    static InitializrMetadata parseMetadataJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Object rootObj = new MiniJsonParser(json).parse();
        if (!(rootObj instanceof Map)) {
            return null;
        }
        Map<String, Object> root = (Map<String, Object>) rootObj;

        String defaultBoot = "3.4.3"; //NOI18N
        List<String> bootVersions = new ArrayList<>();
        Object bootVerObj = root.get("bootVersion"); //NOI18N
        if (bootVerObj instanceof Map) {
            Map<String, Object> bvMap = (Map<String, Object>) bootVerObj;
            if (bvMap.get("default") instanceof String) { //NOI18N
                defaultBoot = sanitizeVersionId((String) bvMap.get("default")); //NOI18N
            }
            Object values = bvMap.get("values"); //NOI18N
            if (values instanceof List) {
                for (Object item : (List<?>) values) {
                    if (item instanceof Map) {
                        Object id = ((Map<?, ?>) item).get("id"); //NOI18N
                        if (id instanceof String) {
                            bootVersions.add(sanitizeVersionId((String) id));
                        }
                    }
                }
            }
        }

        String defaultJava = "21"; //NOI18N
        List<String> javaVersions = new ArrayList<>();
        Object javaVerObj = root.get("javaVersion"); //NOI18N
        if (javaVerObj instanceof Map) {
            Map<String, Object> jvMap = (Map<String, Object>) javaVerObj;
            if (jvMap.get("default") instanceof String) { //NOI18N
                defaultJava = (String) jvMap.get("default"); //NOI18N
            }
            Object values = jvMap.get("values"); //NOI18N
            if (values instanceof List) {
                for (Object item : (List<?>) values) {
                    if (item instanceof Map) {
                        Object id = ((Map<?, ?>) item).get("id"); //NOI18N
                        if (id instanceof String) {
                            javaVersions.add((String) id);
                        }
                    }
                }
            }
        }

        List<DependencyItem> dependencies = new ArrayList<>();
        Object depsObj = root.get("dependencies"); //NOI18N
        if (depsObj instanceof Map) {
            Object groups = ((Map<?, ?>) depsObj).get("values"); //NOI18N
            if (groups instanceof List) {
                for (Object g : (List<?>) groups) {
                    if (g instanceof Map) {
                        Map<?, ?> groupMap = (Map<?, ?>) g;
                        String category = groupMap.get("name") instanceof String //NOI18N
                                ? (String) groupMap.get("name") //NOI18N
                                : "General"; //NOI18N
                        Object groupValues = groupMap.get("values"); //NOI18N
                        if (groupValues instanceof List) {
                            for (Object dep : (List<?>) groupValues) {
                                if (dep instanceof Map) {
                                    Map<?, ?> depMap = (Map<?, ?>) dep;
                                    String id = depMap.get("id") instanceof String ? (String) depMap.get("id") : null; //NOI18N
                                    String name = depMap.get("name") instanceof String ? (String) depMap.get("name") : id; //NOI18N
                                    String desc = depMap.get("description") instanceof String ? (String) depMap.get("description") : ""; //NOI18N
                                    if (id != null && !id.isEmpty()) {
                                        dependencies.add(new DependencyItem(id, name, category, desc));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bootVersions.isEmpty()) {
            return null;
        }
        if (javaVersions.isEmpty()) {
            javaVersions.addAll(Arrays.asList("21", "17", "11")); //NOI18N
        }
        if (dependencies.isEmpty()) {
            dependencies.addAll(createFallbackMetadata().getDependencies());
        }
        return new InitializrMetadata(defaultBoot, bootVersions, defaultJava, javaVersions, dependencies, false);
    }

    private static String sanitizeVersionId(String rawVersion) {
        if (rawVersion != null && rawVersion.endsWith(".RELEASE")) { //NOI18N
            return rawVersion.substring(0, rawVersion.length() - ".RELEASE".length()); //NOI18N
        }
        if (rawVersion != null && rawVersion.endsWith(".BUILD-SNAPSHOT")) { //NOI18N
            return rawVersion.substring(0, rawVersion.length() - ".BUILD-SNAPSHOT".length()) + "-SNAPSHOT"; //NOI18N
        }
        return rawVersion;
    }

    private static String normalizeServiceUrl(String serviceUrl) {
        String trimmed = serviceUrl.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/"; //NOI18N
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8); //NOI18N
    }

    private InputStream openStream(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET"); //NOI18N
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/vnd.initializr.v2.2+json,application/vnd.initializr.v2.1+json,application/json,*/*"); //NOI18N
        conn.setRequestProperty("User-Agent", "Apache-NetBeans"); //NOI18N
        return conn.getInputStream();
    }

    private String fetchText(String urlString) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(openStream(urlString), StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public static final class InitializrMetadata {
        private final String defaultBootVersion;
        private final List<String> bootVersions;
        private final String defaultJavaVersion;
        private final List<String> javaVersions;
        private final List<DependencyItem> dependencies;
        private final boolean offlineFallback;

        public InitializrMetadata(
                String defaultBootVersion,
                List<String> bootVersions,
                String defaultJavaVersion,
                List<String> javaVersions,
                List<DependencyItem> dependencies,
                boolean offlineFallback) {
            this.defaultBootVersion = defaultBootVersion;
            this.bootVersions = Collections.unmodifiableList(new ArrayList<>(bootVersions));
            this.defaultJavaVersion = defaultJavaVersion;
            this.javaVersions = Collections.unmodifiableList(new ArrayList<>(javaVersions));
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.offlineFallback = offlineFallback;
        }

        public String getDefaultBootVersion() {
            return defaultBootVersion;
        }

        public List<String> getBootVersions() {
            return bootVersions;
        }

        public String getDefaultJavaVersion() {
            return defaultJavaVersion;
        }

        public List<String> getJavaVersions() {
            return javaVersions;
        }

        public List<DependencyItem> getDependencies() {
            return dependencies;
        }

        public boolean isOfflineFallback() {
            return offlineFallback;
        }
    }

    public static final class DependencyItem {
        private final String id;
        private final String name;
        private final String category;
        private final String description;

        public DependencyItem(String id, String name, String category, String description) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
            this.category = category != null ? category : "General"; //NOI18N
            this.description = description != null ? description : ""; //NOI18N
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DependencyItem)) {
                return false;
            }
            DependencyItem that = (DependencyItem) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Minimal, zero-dependency RFC 8259 JSON parser for Spring Initializr metadata.
     */
    private static final class MiniJsonParser {
        private final String s;
        private int pos;

        MiniJsonParser(String s) {
            this.s = s;
        }

        Object parse() {
            skipWhitespace();
            if (pos >= s.length()) {
                return null;
            }
            return readValue();
        }

        private Object readValue() {
            skipWhitespace();
            if (pos >= s.length()) {
                return null;
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return readObject();
            }
            if (c == '[') {
                return readArray();
            }
            if (c == '"') {
                return readString();
            }
            return readLiteral();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < s.length()) {
                skipWhitespace();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    break;
                }
                String key = readString();
                skipWhitespace();
                if (pos < s.length() && s.charAt(pos) == ':') {
                    pos++;
                }
                Object val = readValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else if (pos < s.length() && s.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    break;
                }
            }
            return map;
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < s.length()) {
                list.add(readValue());
                skipWhitespace();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else if (pos < s.length() && s.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    break;
                }
            }
            return list;
        }

        private String readString() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"':
                        case '\\':
                        case '/':
                            sb.append(esc);
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 <= s.length()) {
                                String hex = s.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            break;
                        default:
                            sb.append(esc);
                            break;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object readLiteral() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                pos++;
            }
            String token = s.substring(start, pos);
            if ("true".equals(token)) { //NOI18N
                return Boolean.TRUE;
            }
            if ("false".equals(token)) { //NOI18N
                return Boolean.FALSE;
            }
            if ("null".equals(token)) { //NOI18N
                return null;
            }
            return token;
        }

        private void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }
    }
}
