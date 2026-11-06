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

package org.netbeans.modules.javascript.nodejs.editor;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.javascript.nodejs.ui.libraries.Library;
import org.netbeans.modules.javascript.nodejs.ui.libraries.LibraryProvider;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/** Dependency version completion, using npm's configured registry and credentials. */
@MimeRegistration(mimeType = "text/x-json", service = CompletionProvider.class)
public final class PackageJsonCompletionProvider implements CompletionProvider {
    private static final Logger LOG = Logger.getLogger(PackageJsonCompletionProvider.class.getName());
    private static final Set<String> SECTIONS = Set.of("dependencies", "devDependencies",
            "peerDependencies", "optionalDependencies");

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE && queryType != COMPLETION_ALL_QUERY_TYPE) {
            return null;
        }
        Object owner = component.getDocument().getProperty(Document.StreamDescriptionProperty);
        if (!(owner instanceof DataObject)) {
            return null;
        }
        FileObject file = ((DataObject) owner).getPrimaryFile();
        if (!"package.json".equals(file.getNameExt())) {
            return null;
        }
        Project project = FileOwnerQuery.getOwner(file);
        if (project == null) {
            return null;
        }
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override
            protected void query(CompletionResultSet result, Document document, int caret) {
                try {
                    String text = document.getText(0, document.getLength());
                    Context context = context(text, caret);
                    if (context == null || isTaskCancelled()) {
                        return;
                    }
                    result.setAnchorOffset(context.start);
                    Library library = LibraryProvider.forProject(project).libraryDetails(context.name, false);
                    if (library == null || isTaskCancelled()) {
                        return;
                    }
                    Set<String> versions = new LinkedHashSet<>();
                    if (library.getLatestVersion() != null) {
                        versions.add(library.getLatestVersion().getName());
                    }
                    Library.Version[] available = library.getVersions();
                    if (available != null) {
                        for (int i = available.length - 1; i >= 0; i--) {
                            versions.add(available[i].getName());
                        }
                    }
                    int order = 0;
                    for (String version : versions) {
                        String value = context.operator + version;
                        if (value.equals(context.original)) {
                            result.addItem(new Item(value, context, order++));
                        }
                    }
                } catch (BadLocationException | RuntimeException ex) {
                    LOG.log(Level.FINE, "Cannot complete npm dependency version", ex);
                } finally {
                    result.finish();
                }
            }
        }, component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        // Registry queries are explicit (Ctrl+Space), avoiding a process per keystroke.
        return 0;
    }

    static Context context(String text, int caret) {
        if (caret < 0 || caret > text.length()) {
            return null;
        }
        Deque<String> objects = new ArrayDeque<>();
        String key = "";
        boolean value = false;
        for (int i = 0; i < caret; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                int start = ++i;
                while (i < text.length()) {
                    if (text.charAt(i) == '\\') {
                        i += 2;
                    } else if (text.charAt(i) == '"') {
                        break;
                    } else {
                        i++;
                    }
                }
                if (caret >= start && caret <= i) {
                    if (value && objects.size() == 2 && SECTIONS.contains(objects.peek())
                            && key.matches("(?:@[a-z0-9._-]+/)?[a-z0-9][a-z0-9._-]*")) {
                        String prefix = text.substring(start, caret);
                        String operator = prefix.startsWith("^") || prefix.startsWith("~")
                                ? prefix.substring(0, 1) : "";
                        return new Context(key, prefix, operator, start, Math.min(i, text.length()),
                                text.substring(start, Math.min(i, text.length())));
                    }
                    return null;
                }
                if (!value) {
                    key = text.substring(start, Math.min(i, text.length()));
                }
            } else if (c == '{' || c == '[') {
                objects.push(c == '{' ? key : "[]");
                key = "";
                value = false;
            } else if (c == '}' || c == ']') {
                if (objects.isEmpty()) {
                    return null;
                }
                objects.pop();
                value = true;
            } else if (c == ':') {
                value = true;
            } else if (c == ',') {
                key = "";
                value = false;
            }
        }
        return null;
    }

    static final class Context {
        final String name;
        final String prefix;
        final String operator;
        final int start;
        final int end;
        final String original;

        Context(String name, String prefix, String operator, int start, int end, String original) {
            this.name = name;
            this.prefix = prefix;
            this.operator = operator;
            this.start = start;
            this.end = end;
            this.original = original;
        }
    }

    private static final class Item implements CompletionItem {
        private final String version;
        private final Context context;
        private final int order;

        Item(String version, Context context, int order) {
            this.version = version;
            this.context = context;
            this.order = order;
        }

        @Override public void defaultAction(JTextComponent component) {
            Completion.get().hideAll();
            try {
                Document document = component.getDocument();
                // A stale result must never replace text edited during the npm query.
                if (component.getCaretPosition() != context.start + context.prefix.length()
                        || !document.getText(context.start, context.end - context.start)
                                .equals(context.original)) {
                    return;
                }
                component.select(context.start, context.end);
                component.replaceSelection(version);
            } catch (BadLocationException ex) {
                LOG.log(Level.FINE, "Cannot insert npm version", ex);
            }
        }
        @Override public int getPreferredWidth(Graphics g, Font font) {
            return CompletionUtilities.getPreferredWidth(version, null, g, font);
        }
        @Override public void render(Graphics g, Font font, Color foreground, Color background,
                int width, int height, boolean selected) {
            CompletionUtilities.renderHtml(null, version, null, g, font, foreground,
                    width, height, selected);
        }
        @Override public void processKeyEvent(KeyEvent event) { }
        @Override public CompletionTask createDocumentationTask() { return null; }
        @Override public CompletionTask createToolTipTask() { return null; }
        @Override public boolean instantSubstitution(JTextComponent component) { return false; }
        @Override public int getSortPriority() { return order; }
        @Override public CharSequence getSortText() { return version; }
        @Override public CharSequence getInsertPrefix() { return version; }
    }
}
