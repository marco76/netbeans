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
package org.netbeans.modules.angular.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.angular.model.AngularArtifact.HttpCallSite;
import org.netbeans.modules.angular.model.AngularArtifact.Kind;
import org.netbeans.modules.angular.model.AngularArtifact.PropertyBinding;
import org.netbeans.modules.angular.model.AngularArtifact.TsDtoField;
import org.openide.filesystems.FileObject;

/**
 * Scans an Angular workspace for @Component, @Directive, @Pipe, @Injectable, @NgModule,
 * TypeScript DTO interfaces, companion files, and HttpClient REST call sites.
 */
public final class AngularComponentScanner {

    private static final Pattern DECORATOR_CLASS_PATTERN = Pattern.compile(
            "@(Component|Directive|Pipe|Injectable|NgModule)\\s*\\(([\\s\\S]*?)\\)\\s*(?:export\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z0-9_]+)");

    private static final Pattern SELECTOR_PATTERN = Pattern.compile(
            "selector\\s*:\\s*['\"`]([^'\"`]+)['\"`]");

    private static final Pattern PIPE_NAME_PATTERN = Pattern.compile(
            "name\\s*:\\s*['\"`]([^'\"`]+)['\"`]");

    private static final Pattern STANDALONE_PATTERN = Pattern.compile(
            "standalone\\s*:\\s*(true|false)");

    private static final Pattern TEMPLATE_URL_PATTERN = Pattern.compile(
            "templateUrl\\s*:\\s*['\"`]([^'\"`]+)['\"`]");

    private static final Pattern INLINE_TEMPLATE_PATTERN = Pattern.compile(
            "template\\s*:\\s*(['\"`])([\\s\\S]*?)\\1");

    private static final Pattern STYLE_URLS_ARRAY_PATTERN = Pattern.compile(
            "styleUrls\\s*:\\s*\\[([^\\]]*)\\]");

    private static final Pattern STYLE_URL_SINGLE_PATTERN = Pattern.compile(
            "styleUrl\\s*:\\s*['\"`]([^'\"`]+)['\"`]");

    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile(
            "['\"`]([^'\"`]+)['\"`]");

    private static final Pattern INPUT_DECORATOR_PATTERN = Pattern.compile(
            "@Input\\s*\\((?:\\s*['\"`]([^'\"`]*)['\"`]\\s*)?\\)\\s*(?:readonly\\s+)?([A-Za-z0-9_]+)\\s*[?!]?\\s*(?::\\s*([^;=\\n]+))?");

    private static final Pattern SIGNAL_INPUT_PATTERN = Pattern.compile(
            "(?:readonly\\s+)?([A-Za-z0-9_]+)\\s*=\\s*input(?:\\.required)?\\s*(?:<([^>]+)>)?\\s*\\(");

    private static final Pattern OUTPUT_DECORATOR_PATTERN = Pattern.compile(
            "@Output\\s*\\((?:\\s*['\"`]([^'\"`]*)['\"`]\\s*)?\\)\\s*(?:readonly\\s+)?([A-Za-z0-9_]+)\\s*(?::\\s*([^;=\\n]+))?");

    private static final Pattern SIGNAL_OUTPUT_PATTERN = Pattern.compile(
            "(?:readonly\\s+)?([A-Za-z0-9_]+)\\s*=\\s*output\\s*(?:<([^>]+)>)?\\s*\\(");

    // Matches this.http.get<UserDto[]>('/api/users/${id}') or http.post('/api/users', body) or fetch('/api/users')
    private static final Pattern HTTP_CALL_PATTERN = Pattern.compile(
            "(?:(?:this\\.)?[A-Za-z0-9_$]*(?:http|client|api)[A-Za-z0-9_$]*\\.(get|post|put|delete|patch)|\\bfetch)\\s*(?:<\\s*([^>]+?)\\s*>)?\\s*\\(\\s*(['\"`])([^'\"`]+)\\3",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INTERFACE_PATTERN = Pattern.compile(
            "(?:export\\s+)?interface\\s+([A-Za-z0-9_]+)\\s*(?:extends\\s+[^{]+)?\\{([\\s\\S]*?)\\}");

    private static final Pattern INTERFACE_FIELD_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:readonly\\s+)?([A-Za-z0-9_]+)\\s*\\??\\s*:\\s*([^;,\\n]+)");

    private static final Pattern METHOD_HEADER_PATTERN = Pattern.compile(
            "(?:public\\s+|private\\s+|protected\\s+|async\\s+)*([A-Za-z0-9_]+)\\s*(?:<[^>]*>)?\\s*\\([^)]*\\)\\s*(?::\\s*[^{]+)?\\{");

    private AngularComponentScanner() {
    }

    public static @NonNull List<AngularArtifact> scanWorkspace(@NullAllowed FileObject contextFileOrDir) {
        FileObject wsRoot = AngularWorkspace.findWorkspaceRoot(contextFileOrDir);
        if (wsRoot == null) {
            if (contextFileOrDir != null && contextFileOrDir.isFolder()) {
                wsRoot = contextFileOrDir;
            } else {
                return Collections.emptyList();
            }
        }
        AngularWorkspace ws = AngularWorkspace.load(wsRoot);
        List<FileObject> roots = ws != null ? ws.getSourceRoots() : Collections.singletonList(wsRoot);
        List<AngularArtifact> artifacts = new ArrayList<>();
        for (FileObject root : roots) {
            scanFolderRecursively(root, artifacts);
        }
        return artifacts;
    }

    private static void scanFolderRecursively(FileObject folder, List<AngularArtifact> artifacts) {
        if (folder == null || !folder.isFolder()) {
            return;
        }
        String folderName = folder.getName();
        if ("node_modules".equals(folderName) || "dist".equals(folderName)
                || ".angular".equals(folderName) || ".git".equals(folderName)
                || "target".equals(folderName) || "build".equals(folderName)) {
            return;
        }
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                scanFolderRecursively(child, artifacts);
            } else if ("ts".equalsIgnoreCase(child.getExt())
                    && !child.getNameExt().endsWith(".spec.ts")
                    && !child.getNameExt().endsWith(".d.ts")) {
                artifacts.addAll(scanTypeScriptFile(child));
            }
        }
    }

    public static @NonNull List<AngularArtifact> scanTypeScriptFile(@NonNull FileObject tsFile) {
        try {
            String content = tsFile.asText("UTF-8"); // NOI18N
            return parseTypeScriptContent(tsFile, content);
        } catch (IOException ex) {
            return Collections.emptyList();
        }
    }

    public static @NonNull List<AngularArtifact> parseTypeScriptContent(@NonNull FileObject tsFile, @NonNull String content) {
        List<AngularArtifact> results = new ArrayList<>();
        List<HttpCallSite> fileHttpCalls = parseHttpCalls(tsFile, content);

        Matcher m = DECORATOR_CLASS_PATTERN.matcher(content);
        while (m.find()) {
            String decoratorType = m.group(1);
            String metadataBody = m.group(2);
            int metadataOffset = m.start(2);
            String className = m.group(3);
            int classNameStart = m.start(3);
            int classNameEnd = m.end(3);

            Kind kind = switch (decoratorType) {
                case "Component" -> Kind.COMPONENT;
                case "Directive" -> Kind.DIRECTIVE;
                case "Pipe" -> Kind.PIPE;
                case "Injectable" -> Kind.SERVICE;
                case "NgModule" -> Kind.MODULE;
                default -> Kind.COMPONENT;
            };

            String selector = null;
            int selectorStart = -1;
            int selectorEnd = -1;
            if (kind == Kind.PIPE) {
                Matcher pm = PIPE_NAME_PATTERN.matcher(metadataBody);
                if (pm.find()) {
                    selector = pm.group(1).trim();
                    selectorStart = metadataOffset + pm.start(1);
                    selectorEnd = metadataOffset + pm.end(1);
                }
            } else {
                Matcher sm = SELECTOR_PATTERN.matcher(metadataBody);
                if (sm.find()) {
                    selector = sm.group(1).trim();
                    selectorStart = metadataOffset + sm.start(1);
                    selectorEnd = metadataOffset + sm.end(1);
                }
            }

            boolean standalone = false;
            Matcher standaloneMatcher = STANDALONE_PATTERN.matcher(metadataBody);
            if (standaloneMatcher.find()) {
                standalone = Boolean.parseBoolean(standaloneMatcher.group(1));
            }

            String templateUrl = null;
            int templateUrlStart = -1;
            int templateUrlEnd = -1;
            Matcher tm = TEMPLATE_URL_PATTERN.matcher(metadataBody);
            if (tm.find()) {
                templateUrl = tm.group(1).trim();
                templateUrlStart = metadataOffset + tm.start(1);
                templateUrlEnd = metadataOffset + tm.end(1);
            }

            String inlineTemplate = null;
            Matcher itm = INLINE_TEMPLATE_PATTERN.matcher(metadataBody);
            if (itm.find()) {
                inlineTemplate = itm.group(2);
            }

            List<String> styleUrls = new ArrayList<>();
            Matcher styleArrMatcher = STYLE_URLS_ARRAY_PATTERN.matcher(metadataBody);
            if (styleArrMatcher.find()) {
                String arrayContent = styleArrMatcher.group(1);
                Matcher litMatcher = STRING_LITERAL_PATTERN.matcher(arrayContent);
                while (litMatcher.find()) {
                    styleUrls.add(litMatcher.group(1).trim());
                }
            }
            Matcher singleStyleMatcher = STYLE_URL_SINGLE_PATTERN.matcher(metadataBody);
            if (singleStyleMatcher.find()) {
                String sUrl = singleStyleMatcher.group(1).trim();
                if (!styleUrls.contains(sUrl)) {
                    styleUrls.add(sUrl);
                }
            }

            FileObject parentDir = tsFile.getParent();
            FileObject templateFile = resolveRelativeFile(parentDir, templateUrl);
            if (templateFile == null && kind == Kind.COMPONENT) {
                templateFile = findCompanionBySuffix(tsFile, ".html"); // NOI18N
            }

            List<FileObject> styleFiles = new ArrayList<>();
            for (String sUrl : styleUrls) {
                FileObject sf = resolveRelativeFile(parentDir, sUrl);
                if (sf != null) {
                    styleFiles.add(sf);
                }
            }
            if (styleFiles.isEmpty() && kind == Kind.COMPONENT) {
                for (String ext : new String[]{".css", ".scss", ".sass", ".less"}) { // NOI18N
                    FileObject sf = findCompanionBySuffix(tsFile, ext);
                    if (sf != null) {
                        styleFiles.add(sf);
                    }
                }
            }

            FileObject specFile = findCompanionBySuffix(tsFile, ".spec.ts"); // NOI18N

            // Extract class body to parse @Input and @Output
            String classBody = extractBlockBody(content, classNameEnd);
            int classBodyOffset = classBody != null ? content.indexOf('{', classNameEnd) + 1 : classNameEnd;
            List<PropertyBinding> inputs = new ArrayList<>();
            List<PropertyBinding> outputs = new ArrayList<>();
            if (classBody != null) {
                Matcher inDec = INPUT_DECORATOR_PATTERN.matcher(classBody);
                while (inDec.find()) {
                    String alias = inDec.group(1);
                    String prop = inDec.group(2);
                    String type = inDec.group(3) != null ? inDec.group(3).trim() : "any"; // NOI18N
                    inputs.add(new PropertyBinding(prop, alias, type, true,
                            classBodyOffset + inDec.start(2), classBodyOffset + inDec.end(2)));
                }
                Matcher sigIn = SIGNAL_INPUT_PATTERN.matcher(classBody);
                while (sigIn.find()) {
                    String prop = sigIn.group(1);
                    String type = sigIn.group(2) != null ? sigIn.group(2).trim() : "any"; // NOI18N
                    inputs.add(new PropertyBinding(prop, prop, type, true,
                            classBodyOffset + sigIn.start(1), classBodyOffset + sigIn.end(1)));
                }
                Matcher outDec = OUTPUT_DECORATOR_PATTERN.matcher(classBody);
                while (outDec.find()) {
                    String alias = outDec.group(1);
                    String prop = outDec.group(2);
                    String type = outDec.group(3) != null ? outDec.group(3).trim() : "EventEmitter"; // NOI18N
                    outputs.add(new PropertyBinding(prop, alias, type, false,
                            classBodyOffset + outDec.start(2), classBodyOffset + outDec.end(2)));
                }
                Matcher sigOut = SIGNAL_OUTPUT_PATTERN.matcher(classBody);
                while (sigOut.find()) {
                    String prop = sigOut.group(1);
                    String type = sigOut.group(2) != null ? sigOut.group(2).trim() : "OutputEmitterRef"; // NOI18N
                    outputs.add(new PropertyBinding(prop, prop, type, false,
                            classBodyOffset + sigOut.start(1), classBodyOffset + sigOut.end(1)));
                }
            }

            results.add(new AngularArtifact(
                    kind,
                    className,
                    selector,
                    standalone,
                    tsFile,
                    classNameStart,
                    classNameEnd,
                    selectorStart,
                    selectorEnd,
                    templateUrl,
                    templateUrlStart,
                    templateUrlEnd,
                    inlineTemplate,
                    styleUrls,
                    templateFile,
                    styleFiles,
                    specFile,
                    inputs,
                    outputs,
                    fileHttpCalls,
                    Collections.emptyList()));
        }

        // Also scan TypeScript DTO interfaces
        Matcher im = INTERFACE_PATTERN.matcher(content);
        while (im.find()) {
            String interfaceName = im.group(1);
            int nameStart = im.start(1);
            int nameEnd = im.end(1);
            String body = im.group(2);
            int bodyOffset = im.start(2);
            List<TsDtoField> fields = new ArrayList<>();
            Matcher fm = INTERFACE_FIELD_PATTERN.matcher(body);
            while (fm.find()) {
                String fName = fm.group(1);
                String fType = fm.group(2).trim();
                fields.add(new TsDtoField(
                        fName,
                        fType,
                        bodyOffset + fm.start(1),
                        bodyOffset + fm.end(1)));
            }
            results.add(new AngularArtifact(
                    Kind.DTO_INTERFACE,
                    interfaceName,
                    null,
                    false,
                    tsFile,
                    nameStart,
                    nameEnd,
                    -1,
                    -1,
                    null,
                    -1,
                    -1,
                    null,
                    Collections.emptyList(),
                    null,
                    Collections.emptyList(),
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    fields));
        }

        return results;
    }

    public static @NonNull List<HttpCallSite> parseHttpCalls(@NonNull FileObject tsFile, @NonNull String content) {
        List<HttpCallSite> calls = new ArrayList<>();
        String enclosingClass = inferClassName(content, tsFile.getName());
        Matcher hm = HTTP_CALL_PATTERN.matcher(content);
        while (hm.find()) {
            String methodVerb = hm.group(1) != null ? hm.group(1).toUpperCase(Locale.ROOT) : "GET"; // NOI18N
            String dtoType = cleanDtoTypeName(hm.group(2));
            String rawUrl = hm.group(4);
            int urlStart = hm.start(4);
            int urlEnd = hm.end(4);
            int line = countLines(content, urlStart);
            String enclosingMethod = findEnclosingMethodName(content, hm.start());
            String normalizedPath = normalizeUrlPath(rawUrl);
            calls.add(new HttpCallSite(
                    tsFile,
                    enclosingClass,
                    enclosingMethod,
                    methodVerb,
                    rawUrl,
                    normalizedPath,
                    dtoType,
                    urlStart,
                    urlEnd,
                    line));
        }
        return calls;
    }

    public static @NonNull String normalizeUrlPath(@NullAllowed String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return "/"; // NOI18N
        }
        String u = rawUrl.trim();
        // Strip leading ${environment.apiUrl} or http://localhost:8080
        u = u.replaceAll("^\\$\\{[^}]+\\}", "");
        u = u.replaceAll("^https?://[^/]+", "");
        // Strip query parameters
        int qIdx = u.indexOf('?');
        if (qIdx >= 0) {
            u = u.substring(0, qIdx);
        }
        // Replace template interpolations ${...} with {param}
        u = u.replaceAll("\\$\\{[^}]+\\}", "{param}");
        // Replace :id path params with {param}
        u = u.replaceAll("/:([A-Za-z0-9_]+)", "/{param}");
        // Replace {id} with {param} for canonical comparison
        u = u.replaceAll("\\{[^}]+\\}", "{param}");
        if (!u.startsWith("/")) {
            u = "/" + u;
        }
        if (u.length() > 1 && u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /**
     * Resolves all companion files (.component.ts, .component.html, .component.css/.scss, .component.spec.ts)
     * for a given Angular file.
     */
    public static @NonNull Map<String, FileObject> getCompanionFiles(@NullAllowed FileObject file) {
        if (file == null || file.getParent() == null) {
            return Collections.emptyMap();
        }
        FileObject dir = file.getParent();
        String nameExt = file.getNameExt();
        String baseStem = extractComponentStem(nameExt);
        if (baseStem == null) {
            return Collections.emptyMap();
        }
        Map<String, FileObject> companions = new LinkedHashMap<>();
        String[] suffixes = {
            ".ts",
            ".html",
            ".scss",
            ".css",
            ".sass",
            ".less",
            ".spec.ts"
        };
        for (String suffix : suffixes) {
            FileObject candidate = dir.getFileObject(baseStem + suffix);
            if (candidate != null && !candidate.equals(file)) {
                companions.put(candidate.getNameExt(), candidate);
            }
        }
        return companions;
    }

    private static String extractComponentStem(String fileNameExt) {
        String[] compoundSuffixes = {
            ".component.spec.ts",
            ".service.spec.ts",
            ".directive.spec.ts",
            ".pipe.spec.ts",
            ".spec.ts",
            ".ts",
            ".html",
            ".scss",
            ".css",
            ".sass",
            ".less"
        };
        for (String suffix : compoundSuffixes) {
            if (fileNameExt.endsWith(suffix)) {
                return fileNameExt.substring(0, fileNameExt.length() - suffix.length());
            }
        }
        return null;
    }

    private static @CheckForNull FileObject resolveRelativeFile(FileObject baseDir, String relativePath) {
        if (baseDir == null || relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        String clean = relativePath.startsWith("./") ? relativePath.substring(2) : relativePath;
        return baseDir.getFileObject(clean);
    }

    private static @CheckForNull FileObject findCompanionBySuffix(FileObject tsFile, String newSuffix) {
        FileObject parent = tsFile.getParent();
        if (parent == null) {
            return null;
        }
        String nameExt = tsFile.getNameExt();
        if (nameExt.endsWith(".ts")) {
            String stem = nameExt.substring(0, nameExt.length() - 3);
            return parent.getFileObject(stem + newSuffix);
        }
        return null;
    }

    private static @CheckForNull String extractBlockBody(String content, int fromOffset) {
        int openBrace = content.indexOf('{', fromOffset);
        if (openBrace < 0) {
            return null;
        }
        int depth = 1;
        int idx = openBrace + 1;
        while (idx < content.length() && depth > 0) {
            char c = content.charAt(idx);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            idx++;
        }
        if (depth == 0) {
            return content.substring(openBrace + 1, idx - 1);
        }
        return content.substring(openBrace + 1);
    }

    private static String inferClassName(String content, String fallback) {
        Pattern p = Pattern.compile("class\\s+([A-Za-z0-9_]+)");
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return fallback;
    }

    private static String findEnclosingMethodName(String content, int callOffset) {
        String before = content.substring(0, Math.min(callOffset, content.length()));
        Matcher m = METHOD_HEADER_PATTERN.matcher(before);
        String lastMethod = "request"; // NOI18N
        while (m.find()) {
            String candidate = m.group(1);
            if (!"if".equals(candidate) && !"for".equals(candidate)
                    && !"while".equals(candidate) && !"switch".equals(candidate)
                    && !"catch".equals(candidate)) {
                lastMethod = candidate;
            }
        }
        return lastMethod;
    }

    private static @CheckForNull String cleanDtoTypeName(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String cleaned = rawType.trim().replace("[]", "").trim();
        int lt = cleaned.indexOf('<');
        if (lt >= 0 && cleaned.endsWith(">")) {
            cleaned = cleaned.substring(lt + 1, cleaned.length() - 1).replace("[]", "").trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static int countLines(String text, int offset) {
        int line = 1;
        int max = Math.min(offset, text.length());
        for (int i = 0; i < max; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    public static @NonNull List<FileObject> findAllFilesWithExtensions(FileObject root, String... extensions) {
        if (root == null || !root.isFolder()) {
            return Collections.emptyList();
        }
        List<FileObject> matches = new ArrayList<>();
        Enumeration<? extends FileObject> children = root.getChildren(true);
        while (children.hasMoreElements()) {
            FileObject fo = children.nextElement();
            if (fo.isFolder()) {
                continue;
            }
            String path = fo.getPath();
            if (path.contains("/node_modules/") || path.contains("/dist/")
                    || path.contains("/.angular/") || path.contains("/.git/")
                    || path.contains("/target/") || path.contains("/build/")) {
                continue;
            }
            for (String ext : extensions) {
                if (ext.equalsIgnoreCase(fo.getExt())) {
                    matches.add(fo);
                    break;
                }
            }
        }
        return matches;
    }
}
