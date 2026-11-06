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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.net.URL;
import javax.swing.Action;
import org.netbeans.api.editor.document.AtomicLockDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.openide.loaders.DataObject;
import org.netbeans.spi.editor.completion.*;
import org.netbeans.spi.editor.completion.support.*;
import org.openide.filesystems.FileObject;

/** Declaration-based completion when semantic template completion is unavailable. */
@MimeRegistration(mimeType="text/html", service=CompletionProvider.class, position=1100)
public final class AngularMaterialCompletionProvider implements CompletionProvider {
    private static final Pattern DECLARATION = Pattern.compile(
            "ɵɵComponentDeclaration\\s*<([^,]+),\\s*[\"']([^\"']+)[\"']");
    private static final Pattern ELEMENT = Pattern.compile("mat-[a-z0-9-]+");

    static Set<String> selectors(String declaration) {
        Set<String> result = new TreeSet<>();
        var matcher = DECLARATION.matcher(declaration);
        while (matcher.find()) {
            for (String selector : matcher.group(2).split(",")) {
                selector = selector.trim();
                // Attribute-only and constrained selectors need semantic completion.
                if (ELEMENT.matcher(selector).matches()) result.add(selector);
            }
        }
        return result;
    }

    static String prefix(String source, int caret) {
        if (caret < 0 || caret > source.length()) return null;
        int start = caret;
        while (start > 0 && (Character.isLetterOrDigit(source.charAt(start-1))
                || source.charAt(start-1)=='-')) start--;
        if (start == 0 || source.charAt(start-1)!='<') return null;
        if (source.lastIndexOf("<!--", start) > source.lastIndexOf("-->", start)) return null;
        String prefix = source.substring(start, caret);
        return prefix.startsWith("mat") ? prefix : null;
    }

    private static FileObject file(Document doc) {
        Object source=doc.getProperty(Document.StreamDescriptionProperty);
        return source instanceof DataObject ? ((DataObject) source).getPrimaryFile()
                : source instanceof FileObject ? (FileObject) source : null;
    }

    private static FileObject material(FileObject file) {
        FileObject root = AngularWorkspace.findWorkspaceRoot(file);
        for (FileObject dir=root; dir!=null; dir=dir.getParent()) {
            FileObject pkg=dir.getFileObject("node_modules/@angular/material");
            if (pkg!=null) return pkg;
        }
        return null;
    }

    @Override public int getAutoQueryTypes(JTextComponent component, String typedText) {
        FileObject file=file(component.getDocument());
        if (file==null || AngularWorkspace.findWorkspaceRoot(file)==null) return 0;
        try {
            return prefix(component.getDocument().getText(0, component.getCaretPosition()),
                    component.getCaretPosition())!=null ? COMPLETION_QUERY_TYPE : 0;
        } catch (javax.swing.text.BadLocationException ex) { return 0; }
    }

    @Override public CompletionTask createTask(int type, JTextComponent component) {
        if (type!=COMPLETION_QUERY_TYPE && type!=COMPLETION_ALL_QUERY_TYPE) return null;
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override protected void query(CompletionResultSet result, Document doc, int caret) {
                try {
                    String prefix=prefix(doc.getText(0, caret), caret);
                    FileObject file=file(doc);
                    if (prefix==null || file==null) return;
                    FileObject pkg=material(file);
                    if (pkg==null) return;
                    Map<String, Entry> entries=new TreeMap<>();
                    collectPackage(pkg, entries);
                    for (Entry entry:entries.values()) {
                        if (entry.name.startsWith(prefix)) result.addItem(new Item(entry, caret-prefix.length()));
                    }
                } catch (IOException | javax.swing.text.BadLocationException ex) {
                    // Dependencies may be replaced by npm while completion is running.
                } finally { result.finish(); }
            }
        }, component);
    }

    record Entry(String name, String className, String packageName, String importName) {
        Entry(String name, String className, String packageName) {
            this(name, className, packageName, className);
        }
        String hint() { return packageName.substring("@angular/material/".length()); }
        String documentation() {
            return "<html><h3>" + escape(className) + "</h3><p><code>" + escape(packageName)
                    + "</code></p><p>Angular Material component</p><pre>&lt;" + escape(name)
                    + "&gt;…&lt;/" + escape(name) + "&gt;</pre>"
                    + "<p>Accepting this completion also imports the component into its owning standalone component when it can be identified.</p>"
                    + "<p><a href='https://material.angular.dev/components/" + hint()
                    + "/overview'>Component documentation</a></p></html>";
        }
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static Map<String, Entry> entries(String source, String packageName) {
        Map<String, Entry> entries=new TreeMap<>();
        // Prefer the package's exporting module: compatible with older Material releases too.
        var modules = Pattern.compile("ɵɵNgModuleDeclaration\\s*<\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*,").matcher(source);
        String module = modules.find() ? modules.group(1) : null;
        if (modules.find()) module = null;
        var matcher=DECLARATION.matcher(source);
        while (matcher.find()) {
            for (String selector:matcher.group(2).split(",")) {
                String name=selector.trim();
                if (ELEMENT.matcher(name).matches()) {
                    entries.put(name, new Entry(name, matcher.group(1).trim(), packageName,
                            module != null ? module : matcher.group(1).trim()));
                }
            }
        }
        return entries;
    }

    static void collectPackage(FileObject root, Map<String, Entry> entries) throws IOException {
        FileObject manifest=root.getFileObject("package.json");
        if (manifest!=null) {
            try (var reader=new java.io.InputStreamReader(manifest.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                var json=com.google.gson.JsonParser.parseReader(reader);
                if (json.isJsonObject() && json.getAsJsonObject().has("exports")
                        && json.getAsJsonObject().get("exports").isJsonObject()) {
                    for (var export:json.getAsJsonObject().getAsJsonObject("exports").entrySet()) {
                        String key=export.getKey();
                        if (!key.startsWith("./") || key.contains("*") || key.endsWith("package.json")) continue;
                        String types=declarationPath(export.getValue());
                        if (types==null || types.contains("..")) continue;
                        FileObject declaration=root.getFileObject(types.startsWith("./") ? types.substring(2) : types);
                        if (declaration!=null) entries.putAll(entries(declaration.asText(), "@angular/material/"+key.substring(2)));
                    }
                    return;
                }
            } catch (com.google.gson.JsonParseException ex) {
                // npm may temporarily leave an incomplete manifest; use the legacy layout.
            }
        }
        collect(root, root, entries);
    }

    private static String declarationPath(com.google.gson.JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String path=value.getAsString();
            return path.endsWith(".d.ts") ? path : null;
        }
        if (value.isJsonObject()) {
            for (var entry:value.getAsJsonObject().entrySet()) {
                String path=declarationPath(entry.getValue());
                if (path!=null) return path;
            }
        }
        return null;
    }

    private static void collect(FileObject root, FileObject folder, Map<String, Entry> entries) throws IOException {
        for (FileObject child:folder.getChildren()) {
            if (child.isFolder()) {
                if (!child.getNameExt().equals("node_modules") && !child.getNameExt().startsWith(".")) collect(root, child, entries);
            } else if (child.getNameExt().endsWith(".d.ts")) {
                String path=org.openide.filesystems.FileUtil.getRelativePath(root, child);
                String entryPoint=path.startsWith("types/")
                        ? child.getNameExt().replace(".d.ts", "") : path.split("/")[0];
                if (entryPoint.endsWith(".d.ts")) continue;
                entries.putAll(entries(child.asText(), "@angular/material/" + entryPoint));
            }
        }
    }

    static void insert(JTextComponent component, int start, String name) {
        Document doc=component.getDocument();
        Runnable edit=() -> {
            try {
                int caret=component.getCaretPosition();
                String before=doc.getText(0, caret);
                String current=prefix(before, caret);
                if (current==null || start!=caret-current.length()) return;
                int end=caret;
                while (end<doc.getLength()) {
                    char c=doc.getText(end, 1).charAt(0);
                    if (!Character.isLetterOrDigit(c) && c!='-') break;
                    end++;
                }
                // Preserve existing attributes, tag delimiters and closing tags.
                String suffix=doc.getText(end, doc.getLength()-end);
                boolean pair=suffix.isEmpty() || suffix.startsWith("<")
                        && !suffix.startsWith("</" + name + ">");
                boolean close=suffix.startsWith("</" + name + ">");
                String replacement=pair ? name + "></" + name + ">" : name + (close ? ">" : "");
                doc.remove(start, end-start);
                doc.insertString(start, replacement, null);
                component.setCaretPosition(start+name.length()+(pair || close ? 1 : 0));
            } catch (javax.swing.text.BadLocationException ex) { /* stale completion */ }
        };
        AtomicLockDocument atomic=LineDocumentUtils.as(doc, AtomicLockDocument.class);
        if (atomic!=null) atomic.runAtomic(edit); else edit.run();
    }

    private static final class Item implements CompletionItem {
        private final String name;
        private final Entry entry;
        private final int start;
        Item(Entry entry, int start) { this.entry=entry; this.name=entry.name; this.start=start; }
        @Override public void defaultAction(JTextComponent component) {
            insert(component, start, name);
            AngularMaterialImports.apply(file(component.getDocument()), entry);
            org.netbeans.api.editor.completion.Completion.get().hideAll();
        }
        @Override public void processKeyEvent(java.awt.event.KeyEvent event) {}
        @Override public int getPreferredWidth(Graphics g, Font font) {
            return CompletionUtilities.getPreferredWidth(name, entry.hint(), g, font) + 20;
        }
        @Override public void render(Graphics g, Font font, Color foreground, Color background,
                int width, int height, boolean selected) {
            CompletionUtilities.renderHtml(org.openide.util.ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, true), name, entry.hint(), g, font, foreground, width, height, selected);
        }
        @Override public CompletionTask createDocumentationTask() {
            return new AsyncCompletionTask(new AsyncCompletionQuery() {
                @Override protected void query(CompletionResultSet result, Document doc, int caret) {
                    try {
                        result.setDocumentation(new CompletionDocumentation() {
                            @Override public String getText() { return entry.documentation(); }
                            @Override public URL getURL() {
                                try { return new URL("https://material.angular.dev/components/" + entry.hint() + "/overview"); }
                                catch (java.net.MalformedURLException ex) { return null; }
                            }
                            @Override public CompletionDocumentation resolveLink(String link) { return null; }
                            @Override public Action getGotoSourceAction() { return null; }
                        });
                    } finally { result.finish(); }
                }
            });
        }
        @Override public CompletionTask createToolTipTask() { return null; }
        @Override public boolean instantSubstitution(JTextComponent component) { return false; }
        @Override public int getSortPriority() { return 90; }
        @Override public CharSequence getSortText() { return name; }
        @Override public CharSequence getInsertPrefix() { return name; }
    }
}
