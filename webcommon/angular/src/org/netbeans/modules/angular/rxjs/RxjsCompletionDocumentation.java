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

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.lsp.client.spi.CompletionDocumentationProvider;
import org.openide.filesystems.FileObject;

/** Enrich server completions only when their origin identifies RxJS. */
@MimeRegistrations({
    @MimeRegistration(mimeType="text/typescript", service=CompletionDocumentationProvider.class),
    @MimeRegistration(mimeType="application/x-typescript", service=CompletionDocumentationProvider.class)
})
public final class RxjsCompletionDocumentation implements CompletionDocumentationProvider {
    @Override public String documentation(FileObject file, String label, String detail) {
        if (AngularWorkspace.findWorkspaceRoot(file) == null) return null;
        return reference(label, detail);
    }

    @Override public String completionDescription(FileObject file, String label, String detail, String origin) {
        if (AngularWorkspace.findWorkspaceRoot(file) == null) return null;
        return description(label, detail, origin);
    }

    static String description(String label, String detail, String origin) {
        var op = recognized(label, detail, origin);
        return op == null ? null : op.summary() + "  ·  " + op.types() + "  ·  " + op.category();
    }

    @Override public String documentation(FileObject file, String label, String detail, String origin) {
        if (AngularWorkspace.findWorkspaceRoot(file) == null) return null;
        var op = recognized(label, detail, origin);
        if (op == null) return null;
        String signature = detail == null ? "" : "<h3>TypeScript signature / overloads</h3><pre>"
                + RxjsOperators.escape(detail) + "</pre>";
        return op.html().replace("</body>", signature + "</body>");
    }

    static RxjsOperators.Operator recognized(String label, String detail, String origin) {
        boolean module = java.util.Set.of("rxjs", "rxjs/operators", "@angular/core/rxjs-interop")
                .contains(origin == null ? "" : origin);
        boolean signature = detail != null && detail.matches("(?s).*['\"](?:rxjs(?:/operators)?|@angular/core/rxjs-interop)['\"].*");
        return module || signature ? RxjsOperators.find(label) : null;
    }

    static String reference(String label, String detail) {
        if (detail == null || !detail.matches("(?s).*['\"](?:rxjs(?:/operators)?|@angular/core/rxjs-interop)['\"].*")) return null;
        var operator = RxjsOperators.find(label);
        return operator == null ? null : operator.html();
    }
}
