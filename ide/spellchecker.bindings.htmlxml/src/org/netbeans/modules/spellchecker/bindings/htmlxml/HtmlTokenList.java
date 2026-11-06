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
package org.netbeans.modules.spellchecker.bindings.htmlxml;

import java.util.Iterator;
import java.util.Set;
import javax.swing.text.BadLocationException;
import org.netbeans.api.html.lexer.HTMLTokenId;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;


/**
 * Tokenize RHTML for spell checking: Spell check Ruby comments AND HTML text content!
 *
 * @author Tor Norbye, Marek Fukala
 */
public final class HtmlTokenList extends AbstractTokenList {

    private final String fileType;
    private boolean hidden = false;
    private Iterator<TokenSequence<?>> tss;
    private TokenSequence<?> ts;
    private final java.util.List<int[]> expressions = new java.util.ArrayList<>();


    public HtmlTokenList(BaseDocument doc, String fileType) {
        super(doc);
        this.fileType = fileType;
    }

    @Override
    public void setStartOffset(int offset) {
        super.setStartOffset (offset);
        expressions.clear();
        // Modern Angular control-flow headers are HTML TEXT tokens, but contain
        // code (including URLs and identifiers), not prose to spellcheck.
        try {
            String source = doc.getText(0, doc.getLength());
            java.util.regex.Matcher headers = java.util.regex.Pattern.compile(
                    "<!--[\\s\\S]*?(?:-->|$)|<[^>]*>|@(if|for|switch|case|defer|let)\\b").matcher(source);
            while (headers.find()) {
                if (headers.group(1) == null) continue;
                int start = headers.start(), end = headers.end();
                while (end < source.length() && Character.isWhitespace(source.charAt(end))) end++;
                boolean declaration = "let".equals(headers.group(1));
                if (!declaration && (end == source.length() || source.charAt(end) != '(')) continue;
                int depth = 0;
                char quote = 0;
                for (; end < source.length(); end++) {
                    char c = source.charAt(end);
                    if (quote != 0) {
                        if (c == '\\') end++;
                        else if (c == quote) quote = 0;
                    } else if (c == '\'' || c == '"' || c == '`') quote = c;
                    else if (declaration && c == ';') { end++; break; }
                    else if (c == '(') depth++;
                    else if (c == ')' && --depth == 0) { end++; break; }
                }
                expressions.add(new int[]{start, Math.min(end, source.length())});
                headers.region(Math.min(end, source.length()), source.length());
            }
        } catch (BadLocationException ex) {
            // The token sequence below will reject an invalid document snapshot.
        }


        if(fileType != null) {
            FileObject fileObject = FileUtil.getConfigFile ("Spellcheckers/" + fileType);
            Boolean b = (Boolean) fileObject.getAttribute ("Hidden");
            hidden = Boolean.TRUE.equals (b);
        }

        //find top most html embedding token sequence list
        TokenHierarchy<?> th = TokenHierarchy.get(doc);
        LanguagePath htmlPath = null;
        Set<LanguagePath> paths = th.languagePaths();
        for(LanguagePath path : paths) {
            if(path.innerLanguage() == HTMLTokenId.language()) {
                if(htmlPath == null) {
                    htmlPath = path;
                } else {
                    if(htmlPath.size() > path.size()) {
                        //current path is more shallow
                        htmlPath = path;
                    }
                }
            }
        }

        assert htmlPath != null;

        tss = th.tokenSequenceList(htmlPath, offset, Integer.MAX_VALUE).iterator(); //no range provided by the API
        if(tss.hasNext()) {
            ts = tss.next(); //position to the first sequence
            ts.move(offset);
        } else {
           //no html code in the file
        }

    }

    //fast hack for making the spellchecking embedding aware, should be fixed properly
    //performance: the current approach is wrong since a new token sequence is obtained
    //and positioned for each search offset!
    @Override
    protected SpellSpan findNextSpellSpan() throws BadLocationException {
        if (ts == null || !ts.isValid() || hidden) {
            return SpellSpan.NONE;
        }

        ts.move(nextSearchOffset);

        while (ts.moveNext()) {
            TokenId id = ts.token().id();

            if (id == HTMLTokenId.SGML_COMMENT || id == HTMLTokenId.BLOCK_COMMENT || id == HTMLTokenId.TEXT) {
                int start = Math.max(nextSearchOffset, ts.offset());
                int end = ts.offset() + ts.token().length();
                if (id == HTMLTokenId.TEXT) {
                    for (int[] expression : expressions) {
                        if (expression[1] <= start) continue;
                        if (expression[0] >= end) break;
                        if (expression[0] > start) {
                            return new SpellSpan(start, expression[0]);
                        }
                        start = Math.max(start, expression[1]);
                    }
                }
                if (start < end) return new SpellSpan(start, end);
            }
        }

        //we are out of the token sequence, try another one
        if(tss.hasNext()) {
            ts = tss.next();
            return findNextSpellSpan();
        }

        return SpellSpan.NONE;
    }


}
