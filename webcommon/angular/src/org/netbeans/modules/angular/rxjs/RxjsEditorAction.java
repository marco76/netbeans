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
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.*;
import org.openide.util.NbBundle.Messages;

@ActionID(category="Edit", id="org.netbeans.modules.angular.rxjs.RxjsEditorAction")
@ActionRegistration(displayName="#CTL_RxjsEditorGuide")
@ActionReferences({
    @ActionReference(path="Editors/text/typescript/Popup", position=1650, separatorBefore=1649),
    @ActionReference(path="Editors/application/x-typescript/Popup", position=1650, separatorBefore=1649)
})
@Messages("CTL_RxjsEditorGuide=RxJS Operator Documentation")
public final class RxjsEditorAction implements ActionListener {
    @Override public void actionPerformed(ActionEvent event) {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        String name = "";
        if (editor != null) {
            try {
                String text = editor.getDocument().getText(0, editor.getDocument().getLength());
                int start = editor.getCaretPosition(), end = start;
                while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
                while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                String token = text.substring(start, end);
                if (RxjsOperators.find(token) != null) name = token;
            } catch (javax.swing.text.BadLocationException ex) { /* open unfiltered guide */ }
        }
        RxjsLearningTopComponent.showOperator(name);
    }
}
