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
package org.netbeans.modules.angular.signals;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.RequestProcessor;

/** Installs debounced inspections when a TypeScript editor opens. */
@MimeRegistration(mimeType="text/typescript", service=HighlightsLayerFactory.class)
public final class SignalsHints implements HighlightsLayerFactory {
    private static final RequestProcessor WORKER = new RequestProcessor(SignalsHints.class);
    private static final String LAYER = SignalsHints.class.getName();

    @Override public HighlightsLayer[] createLayers(Context context) {
        Document doc = context.getDocument();
        Object stream = doc.getProperty(Document.StreamDescriptionProperty);
        FileObject file = stream instanceof DataObject data ? data.getPrimaryFile()
                : stream instanceof FileObject f ? f : null;
        if (file != null && AngularWorkspace.findWorkspaceRoot(file) != null) {
            synchronized (doc) {
                if (doc.getProperty(SignalsHints.class) == null) {
                    State state = new State(doc);
                    doc.putProperty(SignalsHints.class, state);
                    doc.addDocumentListener(state);
                    state.task.schedule(0);
                }
            }
        }
        return new HighlightsLayer[0];
    }

    private static final class State implements DocumentListener {
        final RequestProcessor.Task task;
        State(Document doc) {
            task = WORKER.create(() -> doc.render(() -> {
                try {
                    String source = doc.getText(0, doc.getLength());
                    List<ErrorDescription> hints = new ArrayList<>();
                    for (var finding : SignalsAnalysis.inspect(source)) {
                        List<Fix> fixes = finding.readFix() ? List.of(readFix(doc, source, finding)) : List.of();
                        hints.add(ErrorDescriptionFactory.createErrorDescription(Severity.HINT, finding.message(), fixes,
                                doc, doc.createPosition(finding.start()), doc.createPosition(finding.end())));
                    }
                    HintsController.setErrors(doc, LAYER, hints);
                } catch (BadLocationException ex) {
                    HintsController.setErrors(doc, LAYER, List.of());
                }
            }));
        }
        @Override public void insertUpdate(DocumentEvent event) { task.schedule(300); }
        @Override public void removeUpdate(DocumentEvent event) { task.schedule(300); }
        @Override public void changedUpdate(DocumentEvent event) {}
    }

    static Fix readFix(Document doc, String source, SignalsAnalysis.Finding finding) {
        return new Fix() {
            @Override public String getText() { return "Read the signal value with ()"; }
            @Override public ChangeInfo implement() {
                LineDocumentUtils.asRequired(doc, org.netbeans.api.editor.document.AtomicLockDocument.class).runAtomicAsUser(() -> {
                    try {
                        // An edit elsewhere can change scope: require the entire inspected snapshot.
                        if (doc.getText(0, doc.getLength()).equals(source)) doc.insertString(finding.end(), "()", null);
                    } catch (BadLocationException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                return null;
            }
        };
    }
}
