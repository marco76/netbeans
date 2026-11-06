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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.RequestProcessor;

/** Profile-aware lexical accents for Angular control flow and expressions. */
@MimeRegistration(mimeType = "text/html", service = HighlightsLayerFactory.class)
public final class AngularControlHighlights implements HighlightsLayerFactory {
    private static final RequestProcessor WORKER = new RequestProcessor(AngularControlHighlights.class);
    private static final Pattern CONTROL = Pattern.compile(
            "<!--[\\s\\S]*?(?:-->|$)|(?<tag><(?:\"[^\"]*\"|'[^']*'|[^'\">])*>)|(?<interpolation>\\{\\{)|@(?<control>if|else|for|empty|switch|case|default|defer|placeholder|loading|error|let)\\b");
    private static final Pattern BINDING = Pattern.compile(
            "(?:\\[\\([^)]*\\)\\]|\\[[^]]+\\]|\\([^)]*\\)|\\*\\w+)\\s*=\\s*(['\"])(.*?)\\1", Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile(
            "'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|`(?:\\\\.|[^`\\\\])*`|\\b(?:of|track|as|when|on|prefetch|true|false|null|undefined)\\b|\\b\\d+(?:\\.\\d+)?\\b|[a-zA-Z_$][\\w$]*(?=\\s*:)");

    @Override public HighlightsLayer[] createLayers(Context context) {
        Document doc = context.getDocument();
        Object stream = doc.getProperty(Document.StreamDescriptionProperty);
        FileObject file = stream instanceof DataObject ? ((DataObject) stream).getPrimaryFile()
                : stream instanceof FileObject ? (FileObject) stream : null;
        if (file == null || AngularWorkspace.findWorkspaceRoot(file) == null) return new HighlightsLayer[0];
        State state;
        synchronized (doc) {
            state = (State) doc.getProperty(AngularControlHighlights.class);
            if (state == null) {
                state = new State(doc);
                doc.putProperty(AngularControlHighlights.class, state);
                doc.addDocumentListener(state);
            }
        }
        state.task.schedule(0);
        return new HighlightsLayer[]{HighlightsLayer.create(AngularControlHighlights.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(1100), true, state.bag)};
    }

    // Each span is {start, end, kind}: control keyword, string, keyword, number, property, expression foreground.
    static List<int[]> spans(String source) {
        List<int[]> result = new ArrayList<>();
        Matcher control = CONTROL.matcher(source);
        while (control.find()) {
            if (control.group("tag") != null) {
                Matcher binding = BINDING.matcher(source).region(control.start(), control.end());
                while (binding.find()) addExpression(source, binding.start(2), binding.end(2), result);
                continue;
            }
            if (control.group("interpolation") != null) {
                int end = control.end();
                char quote = 0;
                for (; end < source.length(); end++) {
                    char c = source.charAt(end);
                    if (quote != 0) {
                        if (c == '\\') end++;
                        else if (c == quote) quote = 0;
                    } else if (c == '\'' || c == '"' || c == '`') quote = c;
                    else if (source.startsWith("}}", end)) break;
                }
                end = Math.min(end, source.length());
                addExpression(source, control.end(), end, result);
                control.region(Math.min(end + 2, source.length()), source.length());
                continue;
            }
            if (control.group("control") == null) continue;
            result.add(new int[]{control.start(), control.end(), 0});
            int start = control.end();
            while (start < source.length() && Character.isWhitespace(source.charAt(start))) start++;
            boolean declaration = "let".equals(control.group("control"));
            if (!declaration && (start == source.length() || source.charAt(start) != '(')) continue;
            int end = start, depth = 0;
            char quote = 0;
            for (; end < source.length(); end++) {
                char c = source.charAt(end);
                if (quote != 0) {
                    if (c == '\\') end++;
                    else if (c == quote) quote = 0;
                } else if (c == '\'' || c == '"' || c == '`') quote = c;
                else if (declaration && c == ';') break;
                else if (c == '(') depth++;
                else if (c == ')' && --depth == 0) break;
            }
            end = Math.min(end, source.length());
            addTokens(source, start, end, result);
            control.region(Math.min(end + 1, source.length()), source.length());
        }
        return result;
    }

    private static void addExpression(String source, int start, int end, List<int[]> result) {
        if (start < end) result.add(new int[]{start, end, 5});
        addTokens(source, start, end, result);
    }

    private static void addTokens(String source, int start, int end, List<int[]> result) {
        Matcher tokens = TOKEN.matcher(source).region(start, end);
        while (tokens.find()) {
            char first = source.charAt(tokens.start());
            int kind = first == '\'' || first == '"' || first == '`' ? 1
                    : Character.isDigit(first) ? 3
                    : source.substring(tokens.start(), tokens.end()).matches("of|track|as|when|on|prefetch|true|false|null|undefined") ? 2 : 4;
            result.add(new int[]{tokens.start(), tokens.end(), kind});
        }
    }

    private static final class State implements DocumentListener {
        final Document doc;
        final OffsetsBag bag;
        final RequestProcessor.Task task;
        State(Document doc) {
            this.doc = doc;
            bag = new OffsetsBag(doc);
            task = WORKER.create(() -> doc.render(() -> {
                FontColorSettings colors = MimeLookup.getLookup("text/html").lookup(FontColorSettings.class);
                if (colors == null) return;
                OffsetsBag next = new OffsetsBag(doc);
                String[] names = {"keyword", "string", "keyword", "number", "markup-attribute"};
                try {
                    for (int[] span : spans(doc.getText(0, doc.getLength()))) {
                        AttributeSet attributes;
                        if (span[2] == 5) {
                            javax.swing.text.SimpleAttributeSet foreground = new javax.swing.text.SimpleAttributeSet();
                            AttributeSet defaults = colors.getFontColors("default");
                            if (defaults == null) continue;
                            javax.swing.text.StyleConstants.setForeground(foreground,
                                    javax.swing.text.StyleConstants.getForeground(defaults));
                            attributes = foreground;
                        } else {
                            attributes = colors.getTokenFontColors(names[span[2]]);
                        }
                        if (attributes != null) next.addHighlight(span[0], span[1], attributes);
                    }
                    bag.setHighlights(next);
                } catch (BadLocationException ex) {
                    throw new IllegalStateException(ex);
                }
            }));
        }
        @Override public void insertUpdate(DocumentEvent e) { task.schedule(120); }
        @Override public void removeUpdate(DocumentEvent e) { task.schedule(120); }
        @Override public void changedUpdate(DocumentEvent e) { }
    }
}
