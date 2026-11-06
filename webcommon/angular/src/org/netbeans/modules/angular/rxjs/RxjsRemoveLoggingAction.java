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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.*;
import org.openide.util.NbBundle.Messages;

@ActionID(category="Edit", id="org.netbeans.modules.angular.rxjs.RxjsRemoveLoggingAction")
@ActionRegistration(displayName="#CTL_RxjsRemoveLoggingAction")
@ActionReferences({
    @ActionReference(path="Editors/text/typescript/Popup", position=1653),
    @ActionReference(path="Editors/application/x-typescript/Popup", position=1653)
})
@Messages("CTL_RxjsRemoveLoggingAction=Remove Generated RxJS Logging")
public final class RxjsRemoveLoggingAction implements ActionListener {
    @Override public void actionPerformed(ActionEvent event) {
        var editor = EditorRegistry.lastFocusedComponent();
        if (editor == null || !editor.isEditable()) return;
        var doc = editor.getDocument();
        Runnable edit = () -> {
            try {
                String source = doc.getText(0, doc.getLength());
                String changed = RxjsLogging.remove(source);
                if (source.equals(changed)) return;
                int start = 0;
                while (start < source.length() && start < changed.length() && source.charAt(start) == changed.charAt(start)) start++;
                int before = source.length(), after = changed.length();
                while (before > start && after > start && source.charAt(before - 1) == changed.charAt(after - 1)) { before--; after--; }
                doc.remove(start, before - start);
                doc.insertString(start, changed.substring(start, after), null);
            } catch (javax.swing.text.BadLocationException | IllegalArgumentException ex) {
                StatusDisplayer.getDefault().setStatusText(ex.getMessage());
            }
        };
        var atomic = org.netbeans.api.editor.document.LineDocumentUtils.as(doc,
                org.netbeans.api.editor.document.AtomicLockDocument.class);
        if (atomic != null) atomic.runAtomic(edit); else edit.run();
    }
}
