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
package org.netbeans.modules.presenter;

import java.awt.Color;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/** Presentation-only highlighting, isolated per editor rather than per file. */
final class SelectionSpotlight {
    final Document document;
    final OffsetsBag highlights;
    private boolean active;
    private Runnable onClear;
    private final DocumentListener edits = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { clear(); }
        @Override public void removeUpdate(DocumentEvent event) { clear(); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };

    SelectionSpotlight(Document document) {
        this.document = document;
        highlights = new OffsetsBag(document);
    }

    boolean isActive() { return active; }

    void activate(int start, int end, Color foreground, Color background, Runnable onClear) {
        clear();
        if (start < 0 || end <= start || end > document.getLength()) return;
        SimpleAttributeSet dim = new SimpleAttributeSet();
        Color fg = foreground == null ? Color.GRAY : foreground;
        Color bg = background == null ? Color.WHITE : background;
        StyleConstants.setForeground(dim, new Color(
                blend(fg.getRed(), bg.getRed()), blend(fg.getGreen(), bg.getGreen()),
                blend(fg.getBlue(), bg.getBlue())));
        OffsetsBag next = new OffsetsBag(document);
        if (start > 0) next.addHighlight(0, start, dim);
        if (end < document.getLength()) next.addHighlight(end, document.getLength(), dim);
        highlights.setHighlights(next);
        this.onClear = onClear;
        active = true;
        document.addDocumentListener(edits);
    }

    private static int blend(int foreground, int background) {
        return (foreground * 40 + background * 60) / 100;
    }

    void clear() {
        if (active) document.removeDocumentListener(edits);
        active = false;
        highlights.clear();
        Runnable callback = onClear;
        onClear = null;
        if (callback != null) callback.run();
    }
}
