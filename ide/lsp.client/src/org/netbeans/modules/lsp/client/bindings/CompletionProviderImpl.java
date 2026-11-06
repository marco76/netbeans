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

package org.netbeans.modules.lsp.client.bindings;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JToolTip;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position.Bias;
import javax.swing.text.StyledDocument;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplate;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.Utils;
import org.netbeans.spi.editor.completion.CompletionDocumentation;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author lahvac
 */
@MimeRegistration(mimeType="", service=CompletionProvider.class, position = 1000)
public class CompletionProviderImpl implements CompletionProvider {

    private static final Logger LOG = Logger.getLogger(CompletionProviderImpl.class.getName());

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if ((queryType & TOOLTIP_QUERY_TYPE) != 0) {
            return new AsyncCompletionTask(new AsyncCompletionQuery() {
                @Override
                protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                    try {
                        FileObject file = NbEditorUtilities.getFileObject(doc);
                        if (file == null) {
                            //TODO: beep
                            return ;
                        }
                        //TODO: active signature?
                        StringBuilder signatures = new StringBuilder();
                        Utils.handleBindings(LSPBindings.getBindings(file),
                                             capa -> capa.getSignatureHelpProvider() != null,
                                             () -> new SignatureHelpParams(new TextDocumentIdentifier(Utils.toURI(file)),
                                                        Utils.createPosition(doc, caretOffset)),
                                             (server, params) -> server.getTextDocumentService().signatureHelp(params),
                                             (server, result) -> handleSignatureInfo(result, signatures));
                        if (signatures.isEmpty()) {
                            return;
                        }
                        signatures.insert(0, "<html>");
                        JToolTip tip = new JToolTip();
                        tip.setTipText(signatures.toString());
                        resultSet.setToolTip(tip);
                    } finally {
                        resultSet.finish();
                    }
                }
            }, component);
        }
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override
            protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                try {
                    FileObject file = NbEditorUtilities.getFileObject(doc);
                    if (file == null) {
                        //TODO: beep
                        return ;
                    }
                    long version = DocumentUtilities.getDocumentVersion(doc);
                    Utils.handleBindings(LSPBindings.getBindings(file),
                                         capa -> capa.getCompletionProvider() != null,
                                         () -> new CompletionParams(new TextDocumentIdentifier(Utils.toURI(file)),
                                                                    Utils.createPosition(doc, caretOffset)),
                                         (server, params) -> requestCompletion(server.getTextDocumentService(), params),
                                         (server, result) -> {
                                             if (!isTaskCancelled() && version == DocumentUtilities.getDocumentVersion(doc)) {
                                                 handleCompletionResult(resultSet, component, doc, caretOffset, server, result);
                                             }
                                         });
                } finally {
                    resultSet.finish();
                }
            }
        }, component);
    }

    @Messages("DN_NoParameter=No parameter.")
    private void handleSignatureInfo(SignatureHelp help, StringBuilder signatures) {
        if (help == null || help.getSignatures().isEmpty()) {
            return ;
        }
        for (SignatureInformation info : help.getSignatures()) {
            if (info.getParameters() == null || info.getParameters().isEmpty()) {
                if (info.getLabel().isEmpty()) {
                    signatures.append(Bundle.DN_NoParameter());
                } else {
                    signatures.append(info.getLabel());
                }
                signatures.append("<br>");
                continue;
            }
            String sigSep = "";
            int idx = 0;
            for (ParameterInformation pi : info.getParameters()) {
                signatures.append(sigSep);
                if (idx == help.getActiveParameter()) {
                    signatures.append("<b>");
                }
                String label;
                if (pi.getLabel().isLeft()) {
                    label = pi.getLabel().getLeft();
                } else {
                    Integer start = pi.getLabel().getRight().getFirst();
                    Integer end = pi.getLabel().getRight().getSecond();

                    label = info.getLabel().substring(start, end);
                }
                signatures.append(label);
                if (idx == help.getActiveParameter()) {
                    signatures.append("</b>");
                }
                sigSep = ", ";
                idx++;
            }
            signatures.append("<br>");
        }
    }

    private void handleCompletionResult(CompletionResultSet resultSet, JTextComponent component, Document doc, int caretOffset, LSPBindings server, Either<List<CompletionItem>, CompletionList> completionResult) {
        if (completionResult == null) {
            return ; //no results
        }
        CompletionOptions completionOptions = server.getInitResult().getCapabilities().getCompletionProvider();
        List<CompletionItem> items;
        boolean incomplete;
        if (completionResult.isLeft()) {
            items = completionResult.getLeft();
            incomplete = true;
        } else {
            items = completionResult.getRight().getItems();
            incomplete = completionResult.getRight().isIncomplete();
        }
        int[] identSpan;
        String ident;
        try {
            identSpan = Utilities.getIdentifierBlock((BaseDocument) doc, caretOffset); //TODO: what is an identifier might (should?) be taken from language configuration?
            if (identSpan != null) {
                int end = Math.min(caretOffset, identSpan[1]);
                ident = doc.getText(identSpan[0], end - identSpan[0]);
            } else {
                ident = "";
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
            return ;
        }
        FileObject sourceFile = NbEditorUtilities.getFileObject(doc);
        boolean pipeArgument = false;
        if (sourceFile != null && "application/x-typescript".equals(sourceFile.getMIMEType())) {
            try {
                pipeArgument = isPipeArgument(doc.getText(0, caretOffset));
            } catch (BadLocationException ex) {
                return;
            }
        }
        final boolean preferOperators = pipeArgument;
        for (CompletionItem i : items) {
            if (!isPipeCandidate(i, preferOperators)) continue;
            if (!matchesCompletion(i, doc, caretOffset, ident)) {
                continue;
            }
            String insert = i.getInsertText() != null ? i.getInsertText() : i.getLabel();
            String leftLabel;
            String rightLabel;
            if (i.getLabelDetails() != null) {
                leftLabel = encode(i.getLabel() + (i.getLabelDetails().getDetail() != null ? i.getLabelDetails().getDetail() : ""));
                if (i.getLabelDetails().getDescription() != null) {
                    rightLabel = encode(i.getLabelDetails().getDescription());
                } else {
                    rightLabel = null;
                }
            } else {
                leftLabel = encode(i.getLabel());
                if (i.getDetail() != null) {
                    rightLabel = encode(i.getDetail());
                } else {
                    rightLabel = null;
                }
            }
            String sortText = i.getSortText() != null ? i.getSortText() : i.getLabel();
            CompletionItemKind kind = i.getKind();
            ImageIcon templateIcon = null;
            FileObject completionFile = NbEditorUtilities.getFileObject(doc);
            if (completionFile != null) {
                for (var support : org.netbeans.api.editor.mimelookup.MimeLookup.getLookup(completionFile.getMIMEType())
                        .lookupAll(org.netbeans.modules.lsp.client.spi.TemplateLanguageSupport.class)) {
                    templateIcon = support.completionIcon(completionFile, i.getLabel());
                    if (templateIcon != null) break;
                }
            }
            String description = rightLabel;
            if (completionFile != null) {
                for (var provider : org.netbeans.api.editor.mimelookup.MimeLookup.getLookup(completionFile.getMIMEType())
                        .lookupAll(org.netbeans.modules.lsp.client.spi.CompletionDocumentationProvider.class)) {
                    String badge = provider.completionDescription(completionFile, i.getLabel(), i.getDetail(),
                            i.getLabelDetails() == null ? null : i.getLabelDetails().getDescription());
                    if (badge != null) {
                        description = encode(badge);
                        break;
                    }
                }
            }
            final String displayDescription = description;
            ImageIcon icon = templateIcon != null ? templateIcon
                    : ImageUtilities.icon2ImageIcon(Icons.getCompletionIcon(kind));
            resultSet.addItem(new org.netbeans.spi.editor.completion.CompletionItem() {
                @Override
                public void defaultAction(JTextComponent jtc) {
                    commit("");
                }
                private void commit(String appendText) {
                    CompletionItem resolved = resolveForCommit(i, completionOptions, server.getTextDocumentService());
                    Either<TextEdit, InsertReplaceEdit> edit = resolved.getTextEdit();
                    if (edit != null && edit.isRight()) {
                        //TODO: the NetBeans client does not current support InsertReplaceEdits, should not happen
                        Completion.get().hideDocumentation();
                        Completion.get().hideCompletion();
                        return ;
                    }
                    boolean[] applied = {false};
                    NbDocument.runAtomic((StyledDocument) doc, () -> {
                        try {
                            CodeTemplate template = null;
                            TextEdit mainEdit;

                            if (edit != null ) {
                                TextEdit providedMainEdit = edit.getLeft();
                                if (resolved.getInsertTextFormat() == InsertTextFormat.Snippet) {
                                    template = CodeTemplateManager.get(doc).createTemporary(convertSnippet2CodeTemplate(providedMainEdit.getNewText()));
                                    mainEdit = new TextEdit(providedMainEdit.getRange(), "");
                                } else {
                                    mainEdit = providedMainEdit;
                                }
                            } else {
                                String toAdd;
                                if (resolved.getInsertTextFormat() == InsertTextFormat.Snippet) {
                                    //TODO: handle appendText
                                    template = CodeTemplateManager.get(doc).createTemporary(convertSnippet2CodeTemplate(resolved.getInsertText()));
                                    toAdd = "";
                                } else {
                                    toAdd = resolved.getInsertText();
                                    if (toAdd == null) {
                                        toAdd = resolved.getLabel();
                                    }
                                }
                                int[] identSpan = Utilities.getIdentifierBlock((BaseDocument) doc, caretOffset);
                                Position start;
                                Position end;
                                if (identSpan != null) {
                                    start = Utils.createPosition(doc, identSpan[0]);
                                    end = Utils.createPosition(doc, identSpan[1]);
                                } else {
                                    end = start = Utils.createPosition(doc, caretOffset);
                                }
                                mainEdit = new TextEdit(new Range(start, end), toAdd);
                            }

                            List<TextEdit> allEdits = new ArrayList<>();

                            allEdits.add(mainEdit);

                            if (resolved.getAdditionalTextEdits() != null) {
                                allEdits.addAll(resolved.getAdditionalTextEdits());
                            }

                            int insertPos = Utils.getOffset(doc, mainEdit.getRange().getStart());
                            LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
                            javax.swing.text.Position startPos = ld.createPosition(insertPos, Bias.Backward);

                            Utils.applyEditsNoLock(doc, allEdits);

                            if (template != null) {
                                //XXX: this does format
                                template.insert(component);
                            } else {
                                int endPos = startPos.getOffset() + mainEdit.getNewText().length();

                                doc.insertString(endPos, appendText, null);
                            }
                            applied[0] = true;
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    });
                    if (applied[0] && resolved.getCommand() != null) {
                        var command = resolved.getCommand();
                        LSPBindings.runOnBackground(() -> server.getWorkspaceService().executeCommand(
                                new org.eclipse.lsp4j.ExecuteCommandParams(command.getCommand(), command.getArguments()))
                                .exceptionally(ex -> { LOG.log(Level.WARNING, "Completion command failed", ex); return null; }));
                    }
                    Completion.get().hideDocumentation();
                    Completion.get().hideCompletion();
                }

                @Override
                public void processKeyEvent(KeyEvent ke) {
                    if (ke.getID() == KeyEvent.KEY_TYPED) {
                        String commitText = String.valueOf(ke.getKeyChar());
                        List<String> commitCharacters = i.getCommitCharacters();

                        if (commitCharacters != null && commitCharacters.contains(commitText)) {
                            commit(commitText);
                            ke.consume();
                            if (isTriggerCharacter(server, commitText)) {
                                Completion.get().showCompletion();
                            }
                        }
                    }
                }

                @Override
                public int getPreferredWidth(Graphics grphcs, Font font) {
                    return CompletionUtilities.getPreferredWidth(leftLabel, displayDescription, grphcs, font);
                }

                @Override
                public void render(Graphics grphcs, Font font, Color color, Color color1, int i, int i1, boolean bln) {
                    COMPLETION_ITEM_RENDERER.renderCompletionItem(icon, leftLabel, displayDescription, grphcs, font, color, i, i1, bln);
                }

                @Override
                public CompletionTask createDocumentationTask() {
                    return new AsyncCompletionTask(new AsyncCompletionQuery() {
                        @Override
                        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                            CompletionItem resolved;
                            if ((i.getDetail() == null || i.getDocumentation() == null) && hasCompletionResolve(completionOptions)) {
                                CompletionItem temp;
                                try {
                                    temp = server.getTextDocumentService().resolveCompletionItem(i).get();
                                } catch (InterruptedException | ExecutionException ex) {
                                    LOG.log(Level.INFO, "Failed to retrieve documentation data", ex);
                                    temp = i;
                                }
                                resolved = temp;
                            } else {
                                resolved = i;
                            }
                            if (resolved != null && (hasUsefulDocumentation(resolved) || i.getLabelDetails() != null)) {
                                resultSet.setDocumentation(new CompletionDocumentation() {
                                    @Override
                                    public String getText() {
                                        if (completionFile != null) {
                                            for (var provider : org.netbeans.api.editor.mimelookup.MimeLookup.getLookup(completionFile.getMIMEType())
                                                    .lookupAll(org.netbeans.modules.lsp.client.spi.CompletionDocumentationProvider.class)) {
                                                String html = provider.documentation(completionFile, resolved.getLabel(), resolved.getDetail(),
                                                        i.getLabelDetails() == null ? null : i.getLabelDetails().getDescription());
                                                if (html == null) {
                                                    html = provider.documentation(completionFile, i.getLabel(), i.getDetail(),
                                                            i.getLabelDetails() == null ? null : i.getLabelDetails().getDescription());
                                                }
                                                if (html != null) return html;
                                            }
                                        }
                                        StringBuilder documentation = new StringBuilder();
                                        documentation.append("<html><body style='font-family: sans-serif; margin: 12px;'>");
                                        documentation.append("<h3>").append(escape(resolved.getLabel())).append("</h3>");
                                        if (resolved.getDetail() != null) {
                                            documentation.append("<div style='font-family: monospace; font-weight: normal; margin-bottom: 12px;'>").append(escape(resolved.getDetail()).replace("\n", "<br>")).append("</div>");
                                            documentation.append("\n<p>");
                                        }
                                        if (resolved.getDocumentation() != null) {
                                            MarkupContent content;
                                            if (resolved.getDocumentation().isLeft()) {
                                                content = new MarkupContent();
                                                content.setKind("plaintext");
                                                content.setValue(resolved.getDocumentation().getLeft());
                                            } else {
                                                content = resolved.getDocumentation().getRight();
                                            }
                                            switch (content.getKind()) {
                                                default:
                                                case "plaintext": documentation.append("<pre>\n").append(escape(content.getValue())).append("\n</pre>"); break;
                                                case "markdown": documentation.append(HtmlRenderer.builder().build().render(Parser.builder().build().parse(content.getValue()))); break;
                                                case "html": documentation.append(content.getValue()); break;
                                            }
                                        }
                                        return documentation.append("</body></html>").toString();
                                    }
                                    @Override
                                    public URL getURL() {
                                        return null;
                                    }
                                    @Override
                                    public CompletionDocumentation resolveLink(String link) {
                                        return null;
                                    }
                                    @Override
                                    public Action getGotoSourceAction() {
                                        return null;
                                    }
                                });
                            }
                            resultSet.finish();
                        }
                    });
                }

                @Override
                public CompletionTask createToolTipTask() {
                    return null;
                }

                @Override
                public boolean instantSubstitution(JTextComponent jtc) {
                    return false;
                }

                @Override
                public int getSortPriority() {
                    return completionPriority(i, preferOperators);
                }

                @Override
                public CharSequence getSortText() {
                    return sortText;
                }

                @Override
                public CharSequence getInsertPrefix() {
                    return insert;
                }
            });
        }
    }

    // Rank semantic server results, preserving their edits and auto-import resolution.
    // Do not manufacture operators: a project may use a different RxJS version.
    static int completionPriority(CompletionItem item, boolean pipeArgument) {
        if (!pipeArgument || item.getKind() != CompletionItemKind.Function) return 100;
        String source = item.getLabelDetails() != null
                ? item.getLabelDetails().getDescription() : null;
        if (source != null && !source.equals("rxjs") && !source.startsWith("rxjs/")) return 100;
        // Already imported operators often have no module description.
        return RXJS_OPERATORS.contains(item.getLabel()) ? 90 : 100;
    }

    static boolean isPipeCandidate(CompletionItem item, boolean pipeArgument) {
        if (!pipeArgument || item.getKind() == null) return true;
        switch (item.getKind()) {
            case Class: case Interface: case Module: case Enum: case EnumMember:
            case Keyword: case Constructor: case File: case Folder: case Struct:
            case TypeParameter:
                return false;
            default:
                // Variables and custom factories may hold or produce OperatorFunctions.
                return true;
        }
    }

    private static final java.util.Set<String> RXJS_OPERATORS = java.util.Set.of(
            "map", "filter", "tap", "switchMap", "mergeMap", "concatMap", "exhaustMap",
            "catchError", "finalize", "take", "takeUntil", "takeWhile", "first", "last",
            "debounceTime", "debounce", "distinctUntilChanged", "distinct", "delay",
            "retry", "timeout", "scan", "reduce", "startWith", "endWith", "share",
            "shareReplay", "skip", "skipUntil", "skipWhile", "toArray", "withLatestFrom",
            "combineLatestWith", "concatWith", "mergeWith", "pairwise", "throttleTime",
            "auditTime", "sampleTime", "observeOn", "subscribeOn", "ignoreElements");

    static boolean isPipeArgument(String text) {
        java.util.ArrayDeque<Integer> openings = new java.util.ArrayDeque<>();
        char quote = 0;
        boolean lineComment = false, blockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n' || c == '\r') lineComment = false;
            } else if (blockComment) {
                if (c == '*' && next == '/') { blockComment = false; i++; }
            } else if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '/' && next == '/') { lineComment = true; i++;
            } else if (c == '/' && next == '*') { blockComment = true; i++;
            } else if (c == '\'' || c == '"' || c == '`') { quote = c;
            } else if (c == '(' || c == '[' || c == '{') { openings.push(i);
            } else if (c == ')' || c == ']' || c == '}') {
                if (!openings.isEmpty()) openings.pop();
            }
        }
        if (quote != 0 || lineComment || blockComment || openings.isEmpty()) return false;
        int open = openings.peek();
        if (text.charAt(open) != '(') return false;
        return text.substring(0, open).matches("(?s).*\\.\\s*pipe\\s*");
    }

    static java.util.concurrent.CompletableFuture<Either<List<CompletionItem>, CompletionList>> requestCompletion(
            org.eclipse.lsp4j.services.TextDocumentService service, CompletionParams params) {
        // didChange uses this queue too: the server must see typing before completion.
        java.util.concurrent.CompletableFuture<Either<List<CompletionItem>, CompletionList>> pending = new java.util.concurrent.CompletableFuture<>();
        LSPBindings.runOnBackground(() -> {
            try {
                service.completion(params).whenComplete((value, error) -> {
                    if (error != null) {
                        pending.completeExceptionally(error);
                    } else {
                        pending.complete(value);
                    }
                });
            } catch (RuntimeException ex) {
                pending.completeExceptionally(ex);
            }
        });
        return pending;
    }

    static boolean hasUsefulDocumentation(CompletionItem item) {
        if (item.getDocumentation() != null) {
            String text = item.getDocumentation().isLeft()
                    ? item.getDocumentation().getLeft() : item.getDocumentation().getRight().getValue();
            if (text != null && !text.isBlank() && !text.strip().equals(item.getLabel())) {
                return true;
            }
        }
        String detail = item.getDetail();
        return detail != null && !detail.isBlank() && !detail.strip().equals(item.getLabel());
    }

    static boolean matchesCompletion(CompletionItem item, Document doc, int caretOffset, String identifier) {
        String filterText = item.getFilterText() != null ? item.getFilterText() : item.getLabel();
        if (filterText.startsWith(identifier)) {
            return true;
        }
        // A server may replace the whole statement (e.g. "import { HttpC").
        // Its filterText is relative to that edit, rather than the last identifier.
        if (item.getTextEdit() != null) {
            org.eclipse.lsp4j.Range range = item.getTextEdit().isLeft()
                    ? item.getTextEdit().getLeft().getRange()
                    : item.getTextEdit().getRight().getInsert();
            int start = Utils.getOffset(doc, range.getStart());
            int end = Utils.getOffset(doc, range.getEnd());
            if (start >= 0 && start < caretOffset && caretOffset <= end && end <= doc.getLength()) {
                try {
                    return filterText.startsWith(doc.getText(start, caretOffset - start));
                } catch (BadLocationException ex) {
                    LOG.log(Level.FINE, "Invalid completion filter range", ex);
                }
            }
        }
        return false;
    }

    static CompletionItem resolveForCommit(CompletionItem item, CompletionOptions options, org.eclipse.lsp4j.services.TextDocumentService service) {
        if (options == null || !Boolean.TRUE.equals(options.getResolveProvider())) return item;
        try {
            CompletionItem resolved = service.resolveCompletionItem(item).get();
            return resolved != null ? resolved : item;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return item;
        } catch (ExecutionException ex) {
            LOG.log(Level.FINE, "Cannot resolve completion", ex);
            return item;
        }
    }

    private boolean hasCompletionResolve(CompletionOptions completionOptions) {
        Boolean resolveProvider = completionOptions.getResolveProvider();
        return resolveProvider != null && resolveProvider;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String encode(String str) {
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;");
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        FileObject file = NbEditorUtilities.getFileObject(component.getDocument());
        if (file == null) {
            return 0;
        }
        List<LSPBindings> servers = LSPBindings.getBindings(file);
        if (servers.isEmpty()) {
            return 0;
        }
        if (servers.stream().noneMatch(server -> server.getInitResult() != null
                && server.getInitResult().getCapabilities().getCompletionProvider() != null)) return 0;
        boolean[] templateTrigger = {false};
        Document document = component.getDocument();
        document.render(() -> {
            try {
                String text = document.getText(0, document.getLength());
                for (var support : org.netbeans.api.editor.mimelookup.MimeLookup.getLookup(file.getMIMEType())
                        .lookupAll(org.netbeans.modules.lsp.client.spi.TemplateLanguageSupport.class)) {
                    if (support.autoComplete(file, text, component.getCaretPosition(), typedText)) {
                        templateTrigger[0] = true;
                        break;
                    }
                }
            } catch (BadLocationException ex) { /* document changed */ }
        });
        return templateTrigger[0] || allowsAutomaticCompletion(file.getMIMEType(), typedText)
                && (isIdentifierTrigger(file.getMIMEType(), typedText)
                    || servers.stream().anyMatch(server -> isTriggerCharacter(server, typedText))) ? COMPLETION_QUERY_TYPE : 0;
    }

    static boolean isIdentifierTrigger(String mimeType, String typedText) {
        if (!"application/x-typescript".equals(mimeType) || typedText.isEmpty()) return false;
        int last = typedText.codePointBefore(typedText.length());
        return Character.isUnicodeIdentifierPart(last) || last == '$';
    }

    static boolean allowsAutomaticCompletion(String mimeType, String typedText) {
        if (typedText.isEmpty()) return false;
        if ("application/x-typescript".equals(mimeType)) {
            char last = typedText.charAt(typedText.length() - 1);
            // The TypeScript server advertises space and braces as triggers too.
            // Opening a method body must not preselect a keyword that Enter inserts.
            return !Character.isWhitespace(last) && last != '{' && last != '}';
        }
        return true;
    }
    
    private boolean isTriggerCharacter(LSPBindings server, String text) {
        InitializeResult init = server.getInitResult();
        if (init == null) return false;
        ServerCapabilities capabilities = init.getCapabilities();
        if (capabilities == null) return false;
        CompletionOptions completionOptions = capabilities.getCompletionProvider();
        if (completionOptions == null) return false;
        List<String> triggerCharacters = completionOptions.getTriggerCharacters();
        if (triggerCharacters == null) return false;
        return triggerCharacters.stream().anyMatch(trigger -> text.endsWith(trigger));
    }

    static String convertSnippet2CodeTemplate(String snippet) {
        //TODO: error states
        StringBuilder template = new StringBuilder();
        int placeholderIdx = 0;

        for (int i = 0; i < snippet.length(); i++) {
            char c = snippet.charAt(i);
            if (c == '$') {
                c = snippet.charAt(++i);

                boolean hasBody = false;

                if (c == '{') {
                    hasBody = true;
                    c = snippet.charAt(++i);
                }

                if (Character.isLetter(c)) {
                    if (hasBody) {
                        while (c != '}') {
                            c = snippet.charAt(++i);
                        }
                    } else {
                        while (Character.isLetter(c)) {
                            c = snippet.charAt(++i);
                        }
                        i--;
                    }
                    template.append("${P").append(placeholderIdx).append("}");
                } else {
                    int tabStopIndexStart = i;

                    while (Character.isDigit(c)) {
                        if (++i >= snippet.length()) {
                            break;
                        }

                        c = snippet.charAt(i);
                    }

                    String tabStopIndex = snippet.substring(tabStopIndexStart, i);
                    String tabStopContent = "";

                    //TODO: handle variables
                    if (hasBody) {
                        int pos = i;

                        while (c != '}') {
                            c = snippet.charAt(++i);
                        }
                        tabStopContent = snippet.substring(pos, i);
                    } else {
                        i--;
                    }

                    if ("0".equals(tabStopIndex)) {
                        template.append("${cursor}");
                    } else {
                        template.append("${T");
                        template.append(tabStopIndex);
                        //TODO: choices
                        if (tabStopContent.startsWith(":")) {
                            template.append(" default=\"");
                            template.append(tabStopContent.substring(1));
                            template.append("\"");
                        }
                        template.append("}");
                    }
                }
            } else {
                template.append(c);
            }
        }

        return template.toString();
    }

    //for tests:
    static IndirectCompletionItemRenderer COMPLETION_ITEM_RENDERER = new IndirectCompletionItemRenderer() {
        @Override
        public void renderCompletionItem(ImageIcon icon, String leftLabel, String rightLabel, Graphics grphcs, Font font, Color color, int i, int i1, boolean bln) {
            CompletionUtilities.renderHtml(icon, leftLabel, rightLabel, grphcs, font, color, i, i1, bln);
        }
    };

    interface IndirectCompletionItemRenderer {
        public void renderCompletionItem(ImageIcon icon, String leftLabel, String rightLabel, Graphics grphcs, Font font, Color color, int i, int i1, boolean bln);
    }

}
