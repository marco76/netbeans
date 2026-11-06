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

import java.awt.BorderLayout;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.netbeans.modules.lsp.client.spi.DiagnosticExplanationProvider.Explanation;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.HtmlBrowser;
import org.openide.util.NbBundle;

final class DiagnosticExplanationUI {
    private DiagnosticExplanationUI() { }

    static void apply(Explanation explanation, String title, Runnable action) {
        if (explanation == null || show(explanation, title)) action.run();
    }

    static boolean show(Explanation explanation, String action) {
        JTextArea text = new JTextArea(NbBundle.getMessage(DiagnosticExplanationUI.class,
                "Explain.details", explanation.cause(), explanation.guidance(), explanation.result(),
                action == null ? "" : action), 14, 60);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setCaretPosition(0);
        text.getAccessibleContext().setAccessibleName(NbBundle.getMessage(DiagnosticExplanationUI.class, "Explain.title"));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        JButton docs = new JButton(NbBundle.getMessage(DiagnosticExplanationUI.class, "Explain.docs"));
        docs.addActionListener(event -> {
            try {
                HtmlBrowser.URLDisplayer.getDefault().showURL(new URL(explanation.documentation()));
            } catch (MalformedURLException ex) {
                org.openide.util.Exceptions.printStackTrace(ex);
            }
        });
        panel.add(docs, BorderLayout.SOUTH);
        String apply = NbBundle.getMessage(DiagnosticExplanationUI.class, "Explain.apply");
        Object[] options = action == null ? new Object[]{NotifyDescriptor.CLOSED_OPTION}
                : new Object[]{apply, NotifyDescriptor.CANCEL_OPTION};
        NotifyDescriptor descriptor = new NotifyDescriptor(panel,
                NbBundle.getMessage(DiagnosticExplanationUI.class, "Explain.title"),
                NotifyDescriptor.DEFAULT_OPTION, NotifyDescriptor.INFORMATION_MESSAGE, options,
                action == null ? NotifyDescriptor.CLOSED_OPTION : NotifyDescriptor.CANCEL_OPTION);
        return apply.equals(DialogDisplayer.getDefault().notify(descriptor));
    }
}
