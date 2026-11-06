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

import java.util.regex.Pattern;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.lsp.client.spi.CompletionDocumentationProvider;
import org.openide.filesystems.FileObject;

/** Signal guidance based on language-server types, without changing insertion edits. */
@MimeRegistration(mimeType="text/typescript", service=CompletionDocumentationProvider.class)
public final class SignalsCompletionDocumentation implements CompletionDocumentationProvider {
    @Override public String completionDescription(FileObject file, String label, String detail) {
        return AngularWorkspace.findWorkspaceRoot(file) == null ? null : description(label, detail);
    }

    @Override public String documentation(FileObject file, String label, String detail) {
        return AngularWorkspace.findWorkspaceRoot(file) == null ? null : reference(label, detail);
    }

    static String kind(String label, String detail) {
        if (detail == null) return null;
        // Match a value's declared type, not a function returning a signal.
        var type = Pattern.compile(":\\s*(WritableSignal|InputSignalWithTransform|InputSignal|ModelSignal|Signal)\\s*<").matcher(detail);
        if (type.find() && !detail.substring(0, type.start()).stripTrailing().endsWith(")")) return type.group(1);
        if (detail.matches("(?s).*['\"]@angular/core['\"].*")
                && (label.equals("signal") || label.equals("computed"))) return label;
        return null;
    }

    static String description(String label, String detail) {
        String kind = kind(label, detail);
        if (kind == null) return null;
        return switch (kind) {
            case "signal", "WritableSignal" -> "Writable signal · () / set / update";
            case "ModelSignal" -> "Model signal · () / set / update";
            case "computed" -> "Computed signal · read only";
            default -> "Read-only signal · ()";
        };
    }

    static String reference(String label, String detail) {
        String kind = kind(label, detail);
        if (kind == null) return null;
        boolean factory = kind.equals("signal") || kind.equals("computed");
        boolean writable = kind.equals("signal") || kind.equals("WritableSignal") || kind.equals("ModelSignal");
        String name = factory ? "count" : label;
        String example = kind.equals("computed")
                ? "const doubled = computed(() => count() * 2);\nconsole.log(doubled());"
                : (kind.equals("signal") ? "const count = signal(0);\n" : "")
                + "const value = " + name + "();"
                + (writable ? "\n" + name + ".set(nextValue);\n" + name + ".update(value => nextValueFrom(value));" : "");
        return "<html><body style='font-family:sans-serif; margin:12px'>"
                + "<h3>" + escape(label) + "</h3><p><font color='#4676B8'><b>"
                + escape(description(label, detail)) + "</b></font></p><pre>" + escape(detail)
                + "</pre><p>Read the current value by calling the signal with <b>()</b>. "
                + "Angular tracks signal reads in computed expressions and templates.</p>"
                + (writable ? "<p><b>set(value)</b> replaces the value; <b>update(fn)</b> derives it from the previous value. Return a new object or array when updating collections.</p>"
                : "<p>This signal is read only: it has no set() or update(). Use computed() for derived state.</p>")
                + "<h4>Example</h4><pre>" + escape(example) + "</pre>"
                + "<p>Template: <code>{{ " + escape(kind.equals("computed") ? "doubled" : name)
                + "() }}</code></p></body></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
