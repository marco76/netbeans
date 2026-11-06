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
package org.netbeans.modules.angular.lsp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import org.netbeans.api.editor.document.AtomicLockDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.RequestProcessor;
import org.openide.awt.StatusDisplayer;

/** Conservative edits for external templates owned by a standalone component. */
final class AngularMaterialImports {
    private static final RequestProcessor WORKER = new RequestProcessor(AngularMaterialImports.class);

    static void apply(FileObject template, AngularMaterialCompletionProvider.Entry entry) {
        if (template == null) return;
        WORKER.post(() -> {
            try {
                var candidates = new ArrayList<EditorCookie>();
                for (FileObject sibling : template.getParent().getChildren()) {
                    if (!sibling.getExt().equals("ts") || sibling.getNameExt().endsWith(".spec.ts")) continue;
                    EditorCookie editor = DataObject.find(sibling).getLookup().lookup(EditorCookie.class);
                    if (editor == null) continue;
                    var doc = editor.openDocument();
                    String source = doc.getText(0, doc.getLength());
                    var url = Pattern.compile("templateUrl\\s*:\\s*(['\"`])([^'\"`]+)\\1").matcher(source);
                    while (url.find()) {
                        if (template.equals(sibling.getParent().getFileObject(url.group(2)))) {
                            candidates.add(editor);
                            break;
                        }
                    }
                }
                if (candidates.size() != 1) { unavailable(); return; }
                var editor = candidates.get(0);
                var doc = editor.openDocument();
                SwingUtilities.invokeLater(() -> {
                    Runnable edit = () -> {
                        try {
                            String before = doc.getText(0, doc.getLength());
                            String after = addImport(before, entry.importName(), entry.packageName());
                            if (after == null) { unavailable(); return; }
                            if (!before.equals(after)) {
                                // Preserve the unchanged prefix and suffix in the editor document.
                                int prefix = 0;
                                while (prefix < before.length() && prefix < after.length()
                                        && before.charAt(prefix) == after.charAt(prefix)) prefix++;
                                int suffix = 0;
                                while (suffix < before.length() - prefix && suffix < after.length() - prefix
                                        && before.charAt(before.length()-1-suffix) == after.charAt(after.length()-1-suffix)) suffix++;
                                doc.remove(prefix, before.length()-prefix-suffix);
                                doc.insertString(prefix, after.substring(prefix, after.length()-suffix), null);
                            }
                        } catch (BadLocationException ex) { unavailable(); }
                    };
                    AtomicLockDocument lock = LineDocumentUtils.as(doc, AtomicLockDocument.class);
                    if (lock != null) lock.runAtomic(edit); else edit.run();
                });
            } catch (IOException | BadLocationException ex) { unavailable(); }
        });
    }

    private static void unavailable() {
        StatusDisplayer.getDefault().setStatusText("Material tag inserted. Add its import manually: the standalone owner could not be safely updated.");
    }

    /** Returns null for metadata that needs semantic resolution. */
    static String addImport(String source, String symbol, String pkg) {
        String code = mask(source);
        var component = Pattern.compile("@Component\\s*\\(\\s*\\{").matcher(code);
        if (!component.find()) return null;
        int start = component.end();
        int end = matching(code, start-1, '{', '}');
        if (end < 0 || component.find()) return null;
        String metadata = code.substring(start, end);
        // Nested metadata objects need a TypeScript syntax tree to resolve safely.
        if (metadata.indexOf('{') >= 0 || metadata.contains("...")) return null;
        if (Pattern.compile("\\bstandalone\\s*:\\s*false\\b").matcher(metadata).find()) return null;
        var imports = Pattern.compile("\\bimports\\s*:").matcher(metadata);
        String local = symbol;
        var existing = Pattern.compile("import\\s*\\{([^}]+)\\}\\s*from\\s*(['\"])") .matcher(source);
        boolean imported = false;
        while (existing.find()) {
            if (!code.startsWith("import", existing.start())) continue;
            int path = existing.end();
            if (!source.startsWith(pkg + existing.group(2), path)) continue;
            for (String binding : existing.group(1).split(",")) {
                String[] parts = binding.trim().split("\\s+as\\s+");
                if (parts[0].equals(symbol)) { local = parts.length == 2 ? parts[1].trim() : symbol; imported = true; }
            }
        }
        if (!imported && Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b").matcher(code).find()) return null;
        String updated;
        if (imports.find()) {
            int array = start + imports.end();
            while (array < end && Character.isWhitespace(code.charAt(array))) array++;
            if (array >= end || code.charAt(array) != '[') return null;
            int close = matching(code, array, '[', ']');
            if (close < 0 || close > end) return null;
            String body = code.substring(array+1, close);
            if (Pattern.compile("\\b" + Pattern.quote(local) + "\\b").matcher(body).find()) updated = source;
            else {
                // Insert before existing entries; trailing commas and comments remain intact.
                updated = source.substring(0, array+1) + local + (body.isBlank() ? "" : ", ") + source.substring(array+1);
            }
        } else {
            if (!Pattern.compile("\\bstandalone\\s*:\\s*true\\b").matcher(metadata).find()) return null;
            updated = source.substring(0, start) + "\n  imports: [" + local + "]," + source.substring(start);
        }
        return imported ? updated : "import { " + symbol + " } from '" + pkg + "';\n" + updated;
    }

    private static int matching(String code, int start, char open, char close) {
        int depth = 0;
        for (int i=start; i<code.length(); i++) {
            if (code.charAt(i) == open) depth++;
            if (code.charAt(i) == close && --depth == 0) return i;
        }
        return -1;
    }

    /** Blank strings/comments while preserving offsets for metadata edits. */
    private static String mask(String source) {
        char[] chars = source.toCharArray();
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (c == '\'' || c == '"' || c == '`') {
                chars[i] = ' ';
                for (i++; i<chars.length; i++) {
                    char next = chars[i]; chars[i] = ' ';
                    if (next == '\\' && i+1 < chars.length) { chars[++i] = ' '; continue; }
                    if (next == c) break;
                }
            } else if (c == '/' && i+1<chars.length && (chars[i+1]=='/' || chars[i+1]=='*')) {
                boolean line = chars[i+1]=='/';
                chars[i++] = ' '; chars[i] = ' ';
                for (i++; i<chars.length; i++) {
                    if (line && chars[i]=='\n') { i--; break; }
                    if (!line && chars[i]=='*' && i+1<chars.length && chars[i+1]=='/') { chars[i++]=' '; chars[i]=' '; break; }
                    chars[i]=' ';
                }
            }
        }
        return new String(chars);
    }
}
