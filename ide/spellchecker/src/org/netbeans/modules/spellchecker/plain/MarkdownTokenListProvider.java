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
package org.netbeans.modules.spellchecker.plain;

import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.Document;
import javax.swing.text.BadLocationException;
import org.netbeans.modules.spellchecker.spi.language.TokenList;
import org.netbeans.modules.spellchecker.spi.language.TokenListProvider;

/** Check prose, leaving code and link destinations untouched. */
public final class MarkdownTokenListProvider implements TokenListProvider {
    @Override public TokenList findTokenList(Document doc) {
        return new PlainTokenList(doc) {
            private final BitSet excluded = new BitSet();
            @Override public void setStartOffset(int offset) {
                super.setStartOffset(offset);
                excluded.clear();
                doc.render(() -> {
                    try {
                        String text = doc.getText(0, doc.getLength());
                        Matcher lines = Pattern.compile("(?m)^.*$").matcher(text);
                        char fence = 0;
                        int width = 0;
                        while (lines.find()) {
                            String line = lines.group();
                            Matcher marker = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$").matcher(line);
                            if (marker.matches()) {
                                char ch = marker.group(1).charAt(0);
                                int length = marker.group(1).length();
                                if (fence == 0) { fence = ch; width = length; }
                                else if (ch == fence && length >= width && marker.group(2).isBlank()) { fence = 0; }
                                excluded.set(lines.start(), lines.end());
                            } else if (fence != 0 || line.startsWith("    ") || line.startsWith("\t")) {
                                excluded.set(lines.start(), lines.end());
                            }
                        }
                        mask(text, "(`+)([\\s\\S]*?)\\1");
                        mask(text, "!?\\[[^\\]\\n]*\\]\\((?:[^()\\n]|\\([^()]*\\))*\\)", true);
                        mask(text, "(?m)^ {0,3}\\[[^\\]]+\\]:.*$");
                        mask(text, "(?:https?://|www\\.)[^\\s<>]+|<[^>\\n]+>");
                    } catch (BadLocationException ex) { throw new IllegalStateException(ex); }
                });
            }
            private void mask(String text, String regex) { mask(text, regex, false); }
            private void mask(String text, String regex, boolean destinationOnly) {
                Matcher matcher = Pattern.compile(regex).matcher(text);
                while (matcher.find()) {
                    int start = destinationOnly ? text.indexOf("](", matcher.start()) + 1 : matcher.start();
                    excluded.set(start, matcher.end());
                }
            }
            @Override public boolean nextWord() {
                while (super.nextWord()) {
                    int start = getCurrentWordStartOffset();
                    int hit = excluded.nextSetBit(start);
                    if (hit < 0 || hit >= start + getCurrentWordText().length()) return true;
                }
                return false;
            }
        };
    }
}
