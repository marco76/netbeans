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
package org.netbeans.modules.angular.rxjs;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.net.URL;
import javax.swing.Action;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.spi.editor.completion.*;
import org.netbeans.spi.editor.completion.support.*;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/** Contextual pipe operator completion and offline documentation. */
@MimeRegistrations({
    @MimeRegistration(mimeType="text/typescript", service=CompletionProvider.class, position=1200),
    @MimeRegistration(mimeType="application/x-typescript", service=CompletionProvider.class, position=1200)
})
public final class RxjsCompletionProvider implements CompletionProvider {
    @Override public int getAutoQueryTypes(JTextComponent editor, String typed) {
        try {
            return prefix(editor.getDocument().getText(0, editor.getCaretPosition())) != null
                    ? COMPLETION_QUERY_TYPE : 0;
        } catch (javax.swing.text.BadLocationException ex) { return 0; }
    }

    // Only direct pipe arguments, including unfinished and nested pipelines.
    static String prefix(String source) {
        String code = RxjsAnalysis.code(source);
        int start = code.length();
        while (start > 0 && Character.isJavaIdentifierPart(code.charAt(start - 1))) start--;
        if (!source.substring(start).equals(code.substring(start))) return null;
        // Do not complete inside a comment or string whose masked tail looks like whitespace.
        if (!RxjsAnalysis.code(source + "X").endsWith("X")) return null;
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        for (int i = 0; i < start; i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[' || c == '{') stack.push(i);
            else if ((c == ')' || c == ']' || c == '}') && !stack.isEmpty()) stack.pop();
        }
        if (stack.isEmpty()) return null;
        int open = stack.peek();
        if (code.charAt(open) != '(' || !code.substring(0, open).matches("(?s).*\\.pipe\\s*")) return null;
        String args = code.substring(open + 1, start);
        int depth = 0, argument = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) argument = i + 1;
        }
        return args.substring(argument).isBlank() ? code.substring(start) : null;
    }

    static String importStatement(String source, RxjsOperators.Operator op) {
        String pkg = op.name().equals("takeUntilDestroyed") ? "@angular/core/rxjs-interop" : "rxjs/operators";
        var imports = java.util.regex.Pattern.compile(
                "import\\s*\\{([^}]+)\\}\\s*from\\s*['\"](rxjs(?:/operators)?|@angular/core/rxjs-interop)['\"]").matcher(source);
        while (imports.find()) {
            for (String binding : imports.group(1).split(",")) {
                if (binding.trim().equals(op.name())) return "";
            }
        }
        return "import { " + op.name() + " } from '" + pkg + "';\n";
    }

    @Override public CompletionTask createTask(int type, JTextComponent editor) {
        if (type != DOCUMENTATION_QUERY_TYPE && type != COMPLETION_QUERY_TYPE && type != COMPLETION_ALL_QUERY_TYPE) return null;
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override protected void query(CompletionResultSet result, Document doc, int caret) {
                try {
                    Object source = doc.getProperty(Document.StreamDescriptionProperty);
                    FileObject file = source instanceof DataObject data ? data.getPrimaryFile()
                            : source instanceof FileObject f ? f : null;
                    if (file == null || AngularWorkspace.findWorkspaceRoot(file) == null) return;
                    String text = doc.getText(0, doc.getLength());
                    if (type != DOCUMENTATION_QUERY_TYPE) {
                        String prefix = prefix(text.substring(0, caret));
                        if (prefix == null) return;
                        result.setAnchorOffset(caret - prefix.length());
                        for (var op : RxjsOperators.ALL) {
                            if (!op.category().equals("Creation") && !op.name().equals("toSignal")
                                    && !op.name().equals("AsyncPipe") && op.name().startsWith(prefix)) {
                                result.addItem(new Item(op, caret - prefix.length()));
                            }
                        }
                        return;
                    }
                    int start = caret, end = caret;
                    while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
                    while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                    var op = RxjsOperators.find(text.substring(start, end));
                    if (op != null && !RxjsAnalysis.code(text).substring(start, end).isBlank()) {
                        result.setDocumentation(new CompletionDocumentation() {
                            @Override public String getText() { return op.html(); }
                            @Override public URL getURL() {
                                try { return new URL(op.url()); }
                                catch (java.net.MalformedURLException ex) { return null; }
                            }
                            @Override public CompletionDocumentation resolveLink(String link) { return null; }
                            @Override public Action getGotoSourceAction() { return null; }
                        });
                    }
                } catch (javax.swing.text.BadLocationException ex) { /* changed editor */ }
                finally { result.finish(); }
            }
        }, editor);
    }
    static String label(RxjsOperators.Operator op) {
        return "<b>" + RxjsOperators.escape(op.name()) + "</b>  ·  "
                + RxjsOperators.escape(op.summary());
    }

    private record Item(RxjsOperators.Operator op, int start) implements CompletionItem {
        @Override public void defaultAction(JTextComponent editor) {
            Document doc = editor.getDocument();
            Runnable edit = () -> {
                try {
                    int caret = editor.getCaretPosition();
                    String source = doc.getText(0, doc.getLength());
                    String current = prefix(source.substring(0, caret));
                    if (current == null || caret - current.length() != start) return;
                    int end = caret;
                    while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
                    boolean hasCall = source.substring(end).stripLeading().startsWith("(");
                    String replacement = op.name() + (hasCall ? "" : "()");
                    doc.remove(start, end - start);
                    doc.insertString(start, replacement, null);
                    String addedImport = importStatement(source, op);
                    int importOffset = source.startsWith("#!") ? source.indexOf('\n') + 1 : 0;
                    if (!addedImport.isEmpty()) doc.insertString(importOffset, addedImport, null);
                    editor.setCaretPosition(start + op.name().length() + (hasCall ? 0 : 1) + addedImport.length());
                } catch (javax.swing.text.BadLocationException ex) { /* stale completion */ }
            };
            var atomic = org.netbeans.api.editor.document.LineDocumentUtils.as(doc,
                    org.netbeans.api.editor.document.AtomicLockDocument.class);
            if (atomic != null) atomic.runAtomic(edit); else edit.run();
            org.netbeans.api.editor.completion.Completion.get().hideAll();
        }
        @Override public void processKeyEvent(java.awt.event.KeyEvent event) {}
        @Override public int getPreferredWidth(Graphics g, Font font) {
            return CompletionUtilities.getPreferredWidth(label(op), RxjsOperators.escape(op.types()), g, font);
        }
        @Override public void render(Graphics g, Font font, Color foreground, Color background,
                int width, int height, boolean selected) {
            CompletionUtilities.renderHtml(null, label(op), RxjsOperators.escape(op.types()),
                    g, font, foreground, width, height, selected);
        }
        @Override public CompletionTask createDocumentationTask() {
            return new AsyncCompletionTask(new AsyncCompletionQuery() {
                @Override protected void query(CompletionResultSet result, Document doc, int caret) {
                    try {
                        result.setDocumentation(new CompletionDocumentation() {
                            @Override public String getText() { return op.html(); }
                            @Override public URL getURL() {
                                try { return new URL(op.url()); }
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
        @Override public boolean instantSubstitution(JTextComponent editor) { return false; }
        @Override public int getSortPriority() { return 80; }
        @Override public CharSequence getSortText() { return op.name(); }
        @Override public CharSequence getInsertPrefix() { return op.name(); }
    }

}
