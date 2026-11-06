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

import java.util.regex.*;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.lsp.client.spi.TemplateLanguageSupport;
import org.openide.filesystems.FileObject;

/** HTML editor bridge; semantic results always come from Angular's compiler. */
@MimeRegistration(mimeType="text/html", service=TemplateLanguageSupport.class)
public final class AngularTemplateLanguageSupport implements TemplateLanguageSupport {
    private static final Pattern BINDING = Pattern.compile("(?:\\[\\([^)]*\\)\\]|\\[[^]]+\\]|\\([^)]*\\)|\\*[\\w]+)\\s*=\\s*(['\"])");
    @Override public javax.swing.ImageIcon completionIcon(FileObject file, String label) {
        if (label == null || !(label.startsWith("mat-") || label.startsWith("mat")
                && label.length() > 3 && Character.isUpperCase(label.charAt(3)))
                || AngularWorkspace.findWorkspaceRoot(file) == null) return null;
        return org.openide.util.ImageUtilities.loadImageIcon(AngularWorkspace.ICON_RESOURCE, true);
    }
    @Override public int[] hyperlinkSpan(FileObject file, CharSequence text, int offset) {
        if (AngularWorkspace.findWorkspaceRoot(file)==null) return null;
        return span(text.toString(), offset);
    }
    @Override public boolean autoComplete(FileObject file, CharSequence text, int offset, String typedText) {
        if (AngularWorkspace.findWorkspaceRoot(file)==null || typedText.isEmpty()) return false;
        String source = text.toString();
        if (offset<0 || offset>source.length() || inComment(source, offset)) return false;
        char last = typedText.charAt(typedText.length()-1);
        return (Character.isJavaIdentifierPart(last) || ".|[({<".indexOf(last)>=0)
                && (expression(source, offset) || tag(source, offset));
    }
    static int[] span(String text, int offset) {
        if (offset<0 || offset>text.length() || inComment(text, offset)) return null;
        boolean expression = expression(text, offset);
        int start=offset, end=offset;
        while (start>0 && identifier(text.charAt(start-1), !expression)) start--;
        while (end<text.length() && identifier(text.charAt(end), !expression)) end++;
        if (start==end) return null;
        if (expression) return new int[]{start, end};
        if (!tag(text, offset)) return null;
        int open = text.lastIndexOf('<', offset);
        String before = text.substring(open+1, start).trim();
        // Custom element names and Angular input/output names; leave native HTML to HTML support.
        if ((before.isEmpty() || before.equals("/")) && text.substring(start,end).contains("-")) return new int[]{start,end};
        if (start>0 && "[( *".indexOf(text.charAt(start-1))>=0 && text.charAt(start-1)!=' ') return new int[]{start,end};
        return null;
    }
    private static boolean identifier(char c, boolean hyphen) {
        return Character.isJavaIdentifierPart(c) || hyphen && c=='-';
    }
    private static boolean inComment(String text, int offset) {
        return text.lastIndexOf("<!--", offset)>text.lastIndexOf("-->", offset);
    }
    private static boolean tag(String text, int offset) {
        int open=text.lastIndexOf('<', offset);
        if (open<0 || open<text.lastIndexOf('>', offset)) return false;
        // Completion in tag/attribute names only, never ordinary quoted HTML values.
        String prefix=text.substring(open, offset);
        boolean single=false, dual=false;
        for (char c : prefix.toCharArray()) {
            if (c=='\'' && !dual) single=!single;
            if (c=='"' && !single) dual=!dual;
        }
        return !single && !dual;
    }
    private static boolean controlExpression(String text, int offset) {
        Matcher control = Pattern.compile("@(if|for|switch|case|defer)\\s*\\(").matcher(text);
        while (control.find() && control.end()<=offset) {
            int depth=1;
            char quote=0;
            int i=control.end();
            for (; i<offset && depth>0; i++) {
                char c=text.charAt(i);
                if (quote!=0) {
                    if (c=='\\') i++;
                    else if (c==quote) quote=0;
                } else if (c=='\'' || c=='"' || c=='`') quote=c;
                else if (c=='(') depth++;
                else if (c==')') depth--;
            }
            if (depth>0) return true;
        }
        return false;
    }

    private static boolean expression(String text, int offset) {
        if (controlExpression(text, offset)) return true;
        int interpolation=text.lastIndexOf("{{", offset);
        if (interpolation>=0 && interpolation>text.lastIndexOf("}}", offset)) return true;
        int open=text.lastIndexOf('<', offset);
        if (open<0) return false;
        Matcher binding=BINDING.matcher(text);
        binding.region(open, text.length());
        while (binding.find() && binding.end()<=offset) {
            int end=text.indexOf(binding.group(1), binding.end());
            if (end<0 || offset<=end) return true;
        }
        return false;
    }
}
