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
package org.netbeans.modules.markdown;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.text.*;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.completion.*;
import org.netbeans.spi.editor.completion.support.*;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/** Language names and relative file paths, without scanning outside the document folder. */
@MimeRegistration(mimeType = MarkdownDataObject.MIME_TYPE, service = CompletionProvider.class)
public final class MarkdownCompletionProvider implements CompletionProvider {
    @Override public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return typedText.endsWith("`") || typedText.endsWith("(") || typedText.endsWith("/") ? COMPLETION_QUERY_TYPE : 0;
    }
    @Override public CompletionTask createTask(int type, JTextComponent component) {
        if (type != COMPLETION_QUERY_TYPE && type != COMPLETION_ALL_QUERY_TYPE) return null;
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override protected void query(CompletionResultSet result, Document doc, int caret) {
                try {
                    String before = doc.getText(0, caret);
                    String line = before.substring(before.lastIndexOf('\n') + 1);
                    java.util.regex.Matcher fence = java.util.regex.Pattern.compile("^ {0,3}(?:`{3,}|~{3,})([A-Za-z0-9_-]*)$").matcher(line);
                    if (fence.matches()) {
                        String prefix = fence.group(1);
                        int start = caret - prefix.length();
                        result.setAnchorOffset(start);
                        for (String language : List.of("bash", "sh", "java", "javascript", "typescript", "html", "css", "scss", "json", "yaml", "xml", "sql", "python", "text")) {
                            if (language.startsWith(prefix)) result.addItem(new Item(language, start, caret, prefix));
                        }
                    } else {
                        int opening = line.lastIndexOf("](");
                        if (opening < 0) return;
                        String path = line.substring(opening + 2);
                        if (path.contains(")") || path.contains(":") || path.contains(" ") || path.startsWith("/")) return;
                        Object owner = doc.getProperty(Document.StreamDescriptionProperty);
                        if (!(owner instanceof DataObject data)) return;
                        FileObject parent = data.getPrimaryFile().getParent();
                        int slash = path.lastIndexOf('/');
                        String directory = slash < 0 ? "" : path.substring(0, slash + 1);
                        FileObject folder = directory.isEmpty() ? parent : parent.getFileObject(directory);
                        if (folder == null || !folder.isFolder()) return;
                        String prefix = path.substring(slash + 1);
                        int start = caret - prefix.length();
                        result.setAnchorOffset(start);
                        for (FileObject child : folder.getChildren()) {
                            if (isTaskCancelled()) return;
                            if (child.getNameExt().startsWith(".")) continue;
                            String name = child.getNameExt() + (child.isFolder() ? "/" : "");
                            if (name.startsWith(prefix)) result.addItem(new Item(name, start, caret, prefix));
                        }
                    }
                } catch (BadLocationException ex) {
                    // The document changed while computing completion.
                } finally { result.finish(); }
            }
        }, component);
    }

    private static final class Item implements CompletionItem {
        private final String value, prefix;
        private final int start, end;
        Item(String value, int start, int end, String prefix) {
            this.value = value; this.start = start; this.end = end; this.prefix = prefix;
        }
        @Override public void defaultAction(JTextComponent component) {
            Completion.get().hideAll();
            try {
                if (component.getCaretPosition() != end || !component.getDocument().getText(start, end - start).equals(prefix)) return;
                component.select(start, end);
                component.replaceSelection(value);
            } catch (BadLocationException ex) { /* stale result */ }
        }
        @Override public int getPreferredWidth(Graphics g, Font font) { return CompletionUtilities.getPreferredWidth(value, null, g, font); }
        @Override public void render(Graphics g, Font font, Color fg, Color bg, int width, int height, boolean selected) {
            String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            CompletionUtilities.renderHtml(null, escaped, null, g, font, fg, width, height, selected);
        }
        @Override public void processKeyEvent(KeyEvent event) { }
        @Override public CompletionTask createDocumentationTask() { return null; }
        @Override public CompletionTask createToolTipTask() { return null; }
        @Override public boolean instantSubstitution(JTextComponent component) { return false; }
        @Override public int getSortPriority() { return 100; }
        @Override public CharSequence getSortText() { return value; }
        @Override public CharSequence getInsertPrefix() { return value; }
    }
}
