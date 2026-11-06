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

import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.*;
import org.netbeans.lib.editor.hyperlink.spi.*;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.lsp.client.spi.TemplateLanguageSupport;
import org.openide.filesystems.FileObject;

/** Gives registered template languages first chance before native HTML links. */
@MimeRegistration(mimeType="text/html", service=HyperlinkProviderExt.class, position=50)
public final class TemplateHyperlinkProvider extends HyperlinkProviderImpl {
    @Override public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        FileObject file=NbEditorUtilities.getFileObject(doc);
        if (file==null) return null;
        int[][] span={null};
        doc.render(() -> {
            try {
                String text=doc.getText(0, doc.getLength());
                for (TemplateLanguageSupport support : MimeLookup.getLookup(file.getMIMEType()).lookupAll(TemplateLanguageSupport.class)) {
                    span[0]=support.hyperlinkSpan(file, text, offset);
                    if (span[0]!=null) break;
                }
            } catch (javax.swing.text.BadLocationException ex) { /* document changed */ }
        });
        return span[0];
    }
}
