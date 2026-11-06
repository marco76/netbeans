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
import java.util.*;
import java.util.regex.*;
import org.openide.filesystems.FileObject;

/** Static route discovery; never executes workspace JavaScript. */
public final class AngularRouteScanner {
    public record Route(String path, String detail, FileObject file, int line,
            FileObject component, FileObject lazyRoutes, List<Route> children) {}
    private record Token(String text, int offset, boolean literal) {}
    private static final Pattern TOKENS = Pattern.compile(
            "//[^\\n]*|/\\*[\\s\\S]*?\\*/|(['\"`])(?:\\\\.|(?!\\1)[^\\\\])*?\\1|[A-Za-z_$][\\w$]*|[^\\s]");
    private AngularRouteScanner() {}

    public static List<Route> scan(FileObject root) throws IOException {
        List<FileObject> files = new ArrayList<>();
        collect(root, files);
        Map<FileObject, List<Route>> parsed = new LinkedHashMap<>();
        for (FileObject file : files) {
            String source = file.asText();
            if (source.contains("@angular/router")) parsed.put(file, parse(file, source));
        }
        Set<FileObject> referenced = new HashSet<>();
        for (List<Route> routes : parsed.values()) referenced(routes, referenced);
        List<Route> result = new ArrayList<>();
        for (var entry : parsed.entrySet()) {
            if (!referenced.contains(entry.getKey())) {
                result.addAll(expand(entry.getValue(), parsed, new HashSet<>(Set.of(entry.getKey()))));
            }
        }
        return result;
    }

    private static void referenced(List<Route> routes, Set<FileObject> files) {
        for (Route route : routes) {
            if (route.lazyRoutes != null) files.add(route.lazyRoutes);
            referenced(route.children, files);
        }
    }

    private static List<Route> expand(List<Route> routes, Map<FileObject, List<Route>> parsed, Set<FileObject> visited) throws IOException {
        List<Route> result = new ArrayList<>();
        for (Route route : routes) {
            List<Route> children = new ArrayList<>(expand(route.children, parsed, visited));
            if (route.lazyRoutes != null && !visited.contains(route.lazyRoutes)) {
                Set<FileObject> branch = new HashSet<>(visited);
                branch.add(route.lazyRoutes);
                List<Route> lazy = parsed.get(route.lazyRoutes);
                if (lazy == null) lazy = parse(route.lazyRoutes, route.lazyRoutes.asText());
                children.addAll(expand(lazy, parsed, branch));
            }
            result.add(new Route(route.path, route.detail, route.file, route.line, route.component, route.lazyRoutes, List.copyOf(children)));
        }
        return result;
    }

    private static void collect(FileObject folder, List<FileObject> files) {
        for (FileObject f : folder.getChildren()) {
            if (f.isFolder()) {
                if (!Set.of("node_modules", "dist", ".angular", ".git", "build", "target").contains(f.getNameExt())) collect(f, files);
            } else if (f.getExt().equals("ts") && !f.getNameExt().endsWith(".spec.ts") && !f.getNameExt().endsWith(".d.ts")) files.add(f);
        }
    }

    public static List<Route> parse(FileObject file, String source) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = TOKENS.matcher(source);
        while (m.find()) {
            String s = m.group();
            if (s.startsWith("//") || s.startsWith("/*")) continue;
            boolean literal = s.startsWith("'") || s.startsWith("\"") || s.startsWith("`");
            tokens.add(new Token(literal ? s.substring(1, s.length()-1) : s, m.start(), literal));
        }
        return objects(file, source, tokens, 0, tokens.size());
    }

    private static boolean is(List<Token> ts, int i, String s) {
        return i < ts.size() && !ts.get(i).literal && ts.get(i).text.equals(s);
    }

    private static int close(List<Token> ts, int start, int end) {
        String opening = ts.get(start).text;
        String closing = opening.equals("{") ? "}" : opening.equals("[") ? "]" : ")";
        int depth = 1;
        for (int i = start+1; i < end; i++) {
            if (is(ts, i, opening)) depth++;
            if (is(ts, i, closing) && --depth == 0) return i;
        }
        return end;
    }

    private static List<Route> objects(FileObject file, String source, List<Token> ts, int start, int end) {
        List<Route> result = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (!is(ts, i, "{")) continue;
            int last = close(ts, i, end);
            String path = null, component = null;
            FileObject lazyRoutes = null, lazyComponent = null;
            List<String> details = new ArrayList<>();
            List<Route> children = new ArrayList<>();
            for (int j = i+1; j+2 < last; j++) {
                if (!is(ts, j+1, ":")) continue;
                String key = ts.get(j).text;
                int value = j+2, stop = value;
                while (stop < last && !is(ts, stop, ",")) {
                    if (is(ts, stop, "{") || is(ts, stop, "[") || is(ts, stop, "(")) stop = close(ts, stop, last);
                    stop++;
                }
                String text = source.substring(ts.get(value).offset, stop < ts.size() ? ts.get(stop).offset : source.length()).trim();
                if (key.equals("path") && ts.get(value).literal) path = ts.get(value).text;
                if (key.equals("component")) { component = ts.get(value).text; details.add(component); }
                if (key.equals("children")) children.addAll(objects(file, source, ts, value, stop));
                if (Set.of("loadComponent", "loadChildren").contains(key)) {
                    details.add("lazy: " + text);
                    Matcher imported = Pattern.compile("import\\s*\\(\\s*[\"']([^\"']+)[\"']").matcher(text);
                    if (imported.find()) {
                        FileObject target = resolveModule(file, imported.group(1));
                        if (key.equals("loadChildren")) lazyRoutes = target;
                        else lazyComponent = target;
                    }
                }
                if (key.startsWith("can") || key.equals("resolve")) details.add(key + ": " + text);
                if (key.equals("redirectTo")) details.add("redirect → " + text);
                if (key.equals("matcher")) { path = "[custom matcher]"; details.add(text); }
                j = stop;
            }
            if (path != null) {
                int line = 0;
                for (int n=0; n<ts.get(i).offset; n++) if (source.charAt(n)=='\n') line++;
                result.add(new Route(path, String.join(" · ", details), file, line,
                        lazyComponent != null ? lazyComponent : resolveComponent(file, source, component), lazyRoutes, List.copyOf(children)));
                i = last;
            }
        }
        return result;
    }

    private static FileObject resolveModule(FileObject file, String path) {
        if (!path.startsWith(".")) return null;
        FileObject target = file.getParent().getFileObject(path + ".ts");
        return target != null ? target : file.getParent().getFileObject(path + "/index.ts");
    }

    private static FileObject resolveComponent(FileObject file, String source, String name) {
        if (name == null) return null;
        Matcher imports = Pattern.compile("import\\s*\\{([^}]+)\\}\\s*from\\s*['\"]([^'\"]+)['\"]").matcher(source);
        while (imports.find()) {
            for (String item : imports.group(1).split(",")) {
                String[] alias = item.trim().split("\\s+as\\s+");
                if (alias[alias.length-1].equals(name) && imports.group(2).startsWith(".")) {
                    return resolveModule(file, imports.group(2));
                }
            }
        }
        return null;
    }
}
