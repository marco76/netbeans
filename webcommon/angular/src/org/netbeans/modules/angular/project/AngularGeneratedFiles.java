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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/** A small, read-only tour of files created by a component schematic. */
final class AngularGeneratedFiles {
    private static final Pattern CLASS = Pattern.compile("\\bexport\\s+class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern TEMPLATE = Pattern.compile("(?m)^[ \\t]*template\\s*:");
    private static final Pattern STYLES = Pattern.compile("(?m)^[ \\t]*styles\\s*:");

    record Entry(String label, FileObject file, int line, String explanation) {
        @Override public String toString() { return label; }
    }

    private AngularGeneratedFiles() { }

    static boolean supported(String name) {
        return List.of("ts", "html", "css", "scss", "sass", "less").stream()
                .anyMatch(extension -> name.endsWith("." + extension));
    }

    static List<Entry> describe(FileObject workspace, Set<FileObject> created) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (FileObject file : created.stream().sorted(java.util.Comparator.comparing(FileObject::getPath)).toList()) {
            String path = FileUtil.getRelativePath(workspace, file);
            if (path == null || !file.isData() || !supported(file.getNameExt())) continue;
            if (file.getNameExt().endsWith(".spec.ts")) {
                entries.add(entry("Explore.test", path, file, 0,
                        AngularLearningPanel.message("Learning.testRole")));
            } else if ("ts".equals(file.getExt())) {
                String source = file.asText("UTF-8");
                Matcher component = CLASS.matcher(source);
                int classOffset = component.find() ? component.start() : source.length();
                int classLine = line(source, classOffset == source.length() ? 0 : classOffset);
                entries.add(entry("Explore.class", path, file, classLine,
                        AngularLearningPanel.message("Learning.classRole")));
                // CLI-generated inline metadata precedes the class; exclude class fields and methods.
                String metadata = source.substring(0, classOffset);
                addInline(entries, metadata, TEMPLATE, "Explore.inlineTemplate", "Learning.templateRole", path, file);
                addInline(entries, metadata, STYLES, "Explore.inlineStyles", "Learning.styleRole", path, file);
            } else {
                boolean html = "html".equals(file.getExt());
                entries.add(entry(html ? "Explore.template" : "Explore.styles", path, file, 0,
                        AngularLearningPanel.message(html ? "Learning.templateRole" : "Learning.styleRole")));
            }
        }
        return List.copyOf(entries);
    }

    private static void addInline(List<Entry> entries, String source, Pattern pattern,
            String label, String explanation, String path, FileObject file) {
        Matcher match = pattern.matcher(source);
        if (match.find()) entries.add(entry(label, path, file, line(source, match.start()),
                AngularLearningPanel.message(explanation)));
    }

    private static Entry entry(String key, String path, FileObject file, int line, String explanation) {
        return new Entry(AngularLearningPanel.message(key, path), file, line, explanation);
    }

    private static int line(String source, int offset) {
        return (int) source.substring(0, offset).chars().filter(c -> c == '\n').count();
    }
}
