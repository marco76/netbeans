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
package org.netbeans.modules.markdown;

import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import java.awt.Color;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.AbstractHighlightsContainer;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.WeakListeners;

/** Code backgrounds are independent of TextMate's embedded-language scope resets. */
final class MarkdownCodeHighlights extends AbstractHighlightsContainer implements DocumentListener {
    private final Document document;
    private final Lookup.Result<FontColorSettings> settings;
    private final LookupListener colorsChanged = event -> invalidate();
    private volatile boolean dirty = true;
    private OffsetsBag bag;

    MarkdownCodeHighlights(Document document) {
        this.document = document;
        settings = MimeLookup.getLookup(MarkdownDataObject.MIME_TYPE).lookupResult(FontColorSettings.class);
        settings.addLookupListener(WeakListeners.create(LookupListener.class, colorsChanged, settings));
        document.addDocumentListener(WeakListeners.document(this, document));
    }

    private void invalidate() {
        dirty = true;
        fireHighlightsChange(0, Integer.MAX_VALUE);
    }

    @Override public void insertUpdate(DocumentEvent event) { invalidate(); }
    @Override public void removeUpdate(DocumentEvent event) { invalidate(); }
    @Override public void changedUpdate(DocumentEvent event) { }

    @Override public HighlightsSequence getHighlights(int start, int end) {
        document.render(() -> {
            if (dirty || bag == null) {
                FontColorSettings colors = settings.allInstances().stream().findFirst().orElse(null);
                AttributeSet configured = colors == null ? null : colors.getTokenFontColors("markup.fenced_code.block.markdown");
                Color background = configured == null ? null : (Color) configured.getAttribute(StyleConstants.Background);
                if (background == null) {
                    AttributeSet defaults = colors == null ? null : colors.getFontColors("default");
                    Color base = defaults == null ? Color.WHITE : (Color) defaults.getAttribute(StyleConstants.Background);
                    background = base != null && base.getRed() + base.getGreen() + base.getBlue() < 384
                            ? new Color(48, 52, 59) : new Color(246, 248, 250);
                }
                bag = createBag(document, background);
                dirty = false;
            }
        });
        return bag.getHighlights(start, end);
    }

    static OffsetsBag createBag(Document document, Color background) {
        OffsetsBag result = new OffsetsBag(document);
        SimpleAttributeSet inline = new SimpleAttributeSet();
        StyleConstants.setBackground(inline, background);
        SimpleAttributeSet block = new SimpleAttributeSet(inline);
        block.addAttribute(HighlightsContainer.ATTR_EXTENDS_EOL, true);
        block.addAttribute(HighlightsContainer.ATTR_EXTENDS_EMPTY_LINE, true);
        document.render(() -> {
            try {
                Node root = Parser.builder().build().parse(document.getText(0, document.getLength()));
                for (Node node : root.getDescendants()) {
                    if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
                        result.addHighlight(node.getStartOffset(), node.getEndOffset(), block);
                    } else if (node instanceof Code) {
                        result.addHighlight(node.getStartOffset(), node.getEndOffset(), inline);
                    }
                }
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
        return result;
    }

    @MimeRegistration(mimeType = MarkdownDataObject.MIME_TYPE, service = HighlightsLayerFactory.class)
    public static final class Factory implements HighlightsLayerFactory {
        @Override public HighlightsLayer[] createLayers(Context context) {
            return new HighlightsLayer[] { HighlightsLayer.create(MarkdownCodeHighlights.class.getName(),
                    ZOrder.SYNTAX_RACK.forPosition(100), true, new MarkdownCodeHighlights(context.getDocument())) };
        }
    }
}
