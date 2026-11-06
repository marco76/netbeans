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
package org.netbeans.modules.diff.builtin.visualizer.editable;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.diff.Difference;
import org.openide.util.NbBundle;

/** Read-only review surface; collapsing context never alters either source document. */
final class InlineDiffPanel extends JPanel {
    private final EditableDiffView master;
    private final JTextPane editor = new JTextPane();
    private final Set<Integer> expanded = new HashSet<>();
    private String[] left = new String[0];
    private String[] right = new String[0];
    private Difference[] differences = new Difference[0];
    private int[] offsets = new int[0];
    private boolean collapse = true;

    InlineDiffPanel(EditableDiffView master) {
        super(new BorderLayout());
        this.master = master;
        editor.setEditable(false);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.getAccessibleContext().setAccessibleName(message("InlineDiff.name"));
        add(new JScrollPane(editor));
    }

    void setContent(String first, String second, Difference[] diffs) {
        left = first.split("\\n", -1);
        right = second.split("\\n", -1);
        differences = diffs.clone();
        expanded.clear();
        render();
    }

    void setCollapsed(boolean collapsed) {
        collapse = collapsed;
        render();
    }

    void selectDifference(int index) {
        if (index >= 0 && index < offsets.length) {
            editor.setCaretPosition(Math.min(offsets[index], editor.getDocument().getLength()));
        }
    }

    /** Only pair unique, exact, nontrivial blocks. Repeated snippets stay unclassified. */
    static Map<Integer, Integer> findMoves(Difference[] diffs) {
        Map<String, Integer> removed = new HashMap<>();
        Map<String, Integer> added = new HashMap<>();
        for (int i = 0; i < diffs.length; i++) {
            Difference diff = diffs[i];
            String text = diff.getType() == Difference.DELETE ? diff.getFirstText()
                    : diff.getType() == Difference.ADD ? diff.getSecondText() : null;
            if (text == null || text.trim().length() < 30 || text.lines().filter(line -> !line.isBlank()).count() < 3) {
                continue;
            }
            Map<String, Integer> blocks = diff.getType() == Difference.DELETE ? removed : added;
            blocks.merge(text, i, (oldValue, newValue) -> -1);
        }
        Map<Integer, Integer> moves = new HashMap<>();
        removed.forEach((text, from) -> {
            Integer to = added.get(text);
            if (from >= 0 && to != null && to >= 0) {
                moves.put(from, to);
                moves.put(to, from);
            }
        });
        return moves;
    }

    private void render() {
        if (master != null && master.getEditorPane2() != null) {
            javax.swing.JEditorPane source = master.getEditorPane2().getEditorPane();
            editor.setFont(source.getFont());
            editor.setBackground(source.getBackground());
            editor.setForeground(source.getForeground());
            editor.setCaretColor(source.getCaretColor());
        }
        editor.setDocument(new javax.swing.text.DefaultStyledDocument());
        offsets = new int[differences.length];
        Map<Integer, Integer> moves = findMoves(differences);
        int oldLine = 0;
        int newLine = 0;
        for (int i = 0; i < differences.length; i++) {
            Difference diff = differences[i];
            int startOld = diff.getType() == Difference.ADD ? diff.getFirstStart() : diff.getFirstStart() - 1;
            int startNew = diff.getType() == Difference.DELETE ? diff.getSecondStart() : diff.getSecondStart() - 1;
            context(newLine, Math.max(newLine, startNew), oldLine);
            offsets[i] = editor.getDocument().getLength();
            Integer partner = moves.get(i);
            if (partner != null) {
                append(message(diff.getType() == Difference.DELETE ? "InlineDiff.movedTo" : "InlineDiff.movedFrom", partner + 1) + "\n", null);
            }
            if (diff.getType() != Difference.ADD) {
                for (int line = Math.max(0, startOld); line < Math.min(left.length, diff.getFirstEnd()); line++) {
                    append(String.format("%5d       − %s\n", line + 1, left[line]), diff);
                }
            }
            if (diff.getType() != Difference.DELETE) {
                for (int line = Math.max(0, startNew); line < Math.min(right.length, diff.getSecondEnd()); line++) {
                    append(String.format("      %5d + %s\n", line + 1, right[line]), diff);
                }
            }
            oldLine = diff.getType() == Difference.ADD ? startOld : diff.getFirstEnd();
            newLine = diff.getType() == Difference.DELETE ? startNew : diff.getSecondEnd();
        }
        context(newLine, right.length, oldLine);
        editor.setCaretPosition(0);
    }

    private void context(int start, int end, int oldStart) {
        int hidden = end - start - 6;
        for (int line = start; line < end; line++) {
            if (collapse && hidden > 0 && line == start + 3 && !expanded.contains(start)) {
                final int key = start;
                final int position = editor.getDocument().getLength();
                JButton expand = new JButton(message("InlineDiff.expand", hidden));
                expand.addActionListener(event -> {
                    expanded.add(key);
                    render();
                    editor.setCaretPosition(Math.min(position, editor.getDocument().getLength()));
                });
                editor.setCaretPosition(editor.getDocument().getLength());
                editor.insertComponent(expand);
                append("\n", null);
                line += hidden - 1;
                continue;
            }
            append(String.format("%5d %5d   %s\n", oldStart + line - start + 1, line + 1, right[line]), null);
        }
    }

    private void append(String text, Difference diff) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        if (diff != null) {
            StyleConstants.setBackground(attributes, master.getColor(diff));
        }
        StyleConstants.setFontFamily(attributes, editor.getFont().getFamily());
        StyleConstants.setFontSize(attributes, editor.getFont().getSize());
        StyleConstants.setForeground(attributes, editor.getForeground());
        try {
            editor.getDocument().insertString(editor.getDocument().getLength(), text, attributes);
        } catch (BadLocationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String message(String key, Object... arguments) {
        return NbBundle.getMessage(InlineDiffPanel.class, key, arguments);
    }
}
