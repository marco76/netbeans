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

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.lsp.client.spi.DiagnosticExplanationProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/** Recognizes compiler codes; does not guess imports from diagnostic wording. */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/html", service = DiagnosticExplanationProvider.class),
    @MimeRegistration(mimeType = "application/x-typescript", service = DiagnosticExplanationProvider.class)
})
public final class AngularDiagnosticExplanations implements DiagnosticExplanationProvider {
    @Override public Explanation explain(FileObject file, String code) {
        if (AngularWorkspace.findWorkspaceRoot(file) == null) return null;
        return forCode(code);
    }

    static Explanation forCode(String code) {
        String kind;
        String documentation;
        if ("-998001".equals(code) || "NG8001".equals(code)) {
            kind = "element";
            documentation = "https://angular.dev/errors/NG8001";
        } else if ("-998002".equals(code) || "NG8002".equals(code)) {
            kind = "binding";
            documentation = "https://angular.dev/errors/NG8002";
        } else if ("-998004".equals(code) || "NG8004".equals(code)) {
            kind = "pipe";
            documentation = "https://angular.dev/errors/NG0302";
        } else if ("2339".equals(code)) {
            kind = "member";
            documentation = "https://angular.dev/tools/cli/template-typecheck";
        } else return null;
        return new Explanation(message(kind + ".cause"), message(kind + ".guidance"),
                message("result"), documentation);
    }

    private static String message(String key) {
        return NbBundle.getMessage(AngularDiagnosticExplanations.class, "Diagnostic." + key);
    }
}
