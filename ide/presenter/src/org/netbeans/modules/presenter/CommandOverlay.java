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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.windows.WindowManager;

/** Click-through toast; never replaces the IDE glass pane or takes focus. */
final class CommandOverlay {
    private final CommandObserver observer = new CommandObserver(this::received);
    private final Bubble bubble = new Bubble();
    private final Timer timer = new Timer(40, event -> tick());
    private JLayeredPane pane;
    private boolean active;
    private volatile long generation;
    private final Supplier<JLayeredPane> panes;
    private final BooleanSupplier windowActive;
    private final Supplier<JTextComponent> editors;
    private final LongSupplier clock;

    CommandOverlay() {
        this(() -> WindowManager.getDefault().getMainWindow() instanceof RootPaneContainer root
                ? root.getLayeredPane() : null,
                () -> WindowManager.getDefault().getMainWindow().isActive(),
                EditorRegistry::lastFocusedComponent, System::nanoTime);
    }

    CommandOverlay(Supplier<JLayeredPane> panes, BooleanSupplier windowActive,
            Supplier<JTextComponent> editors, LongSupplier clock) {
        this.panes = panes;
        this.windowActive = windowActive;
        this.editors = editors;
        this.clock = clock;
    }
    private long shownAt;
    private PresenterPresets.Corner corner = PresenterPresets.Corner.BOTTOM_RIGHT;
    void corner(PresenterPresets.Corner corner) { this.corner = corner; }

    void start() {
        if (active) return;
        pane = panes.get();
        if (pane == null) return;
        generation++;
        active = true;
        observer.start();
    }

    void stop() {
        active = false;
        generation++;
        observer.close();
        timer.stop();
        if (pane != null) {
            pane.remove(bubble);
            pane.repaint();
            pane = null;
        }
    }

    void received(CommandObserver.Command command) {
        // Retain only the display name and KeyStroke, never the log/event payload.
        long session = generation;
        SwingUtilities.invokeLater(() -> {
            if (session != generation || !active || !windowActive.getAsBoolean()) return;
            bubble.text = command.name() + shortcutText(command.shortcut());
            bubble.alpha = 1f;
            shownAt = clock.getAsLong();
            if (bubble.getParent() == null) pane.add(bubble, JLayeredPane.POPUP_LAYER);
            position();
            bubble.setVisible(true);
            bubble.repaint();
            timer.restart();
        });
    }

    private static String shortcutText(KeyStroke key) {
        if (key == null) return "";
        String modifiers = KeyEvent.getModifiersExText(key.getModifiers());
        return "  ·  " + (modifiers.isEmpty() ? "" : modifiers + "+") + KeyEvent.getKeyText(key.getKeyCode());
    }

    void tick() {
        long elapsed = (clock.getAsLong() - shownAt) / 1_000_000;
        if (!active || !windowActive.getAsBoolean() || elapsed >= 2200) {
            bubble.setVisible(false);
            timer.stop();
            return;
        }
        bubble.alpha = elapsed < 1700 ? 1f : (2200 - elapsed) / 500f;
        position();
        bubble.repaint();
    }

    private void position() {
        Rectangle area = new Rectangle(0, 0, pane.getWidth(), pane.getHeight());
        JTextComponent editor = editors.get();
        if (editor != null && editor.isShowing() && SwingUtilities.getRootPane(editor) == SwingUtilities.getRootPane(pane)) {
            area = SwingUtilities.convertRectangle(editor, editor.getVisibleRect(), pane).intersection(area);
        }
        int width = Math.max(0, Math.min(area.width - 32, bubble.getFontMetrics(bubble.getFont()).stringWidth(bubble.text) + 36));
        boolean left = corner == PresenterPresets.Corner.BOTTOM_LEFT || corner == PresenterPresets.Corner.TOP_LEFT;
        boolean top = corner == PresenterPresets.Corner.TOP_LEFT || corner == PresenterPresets.Corner.TOP_RIGHT;
        bubble.setBounds(area.x + (left ? Math.min(16, Math.max(0, area.width - width)) : Math.max(0, area.width - width - 16)),
                area.y + (top ? 12 : Math.max(0, area.height - 62 - javax.swing.UIManager.getInt("nb.presenter.controlsHeight"))), width, Math.min(46, area.height));
    }

    @SuppressWarnings("serial")
    private static final class Bubble extends JComponent {
        private String text = "";
        private float alpha = 1f;
        Bubble() {
            Font labelFont = UIManager.getFont("Label.font");
            setFont(labelFont != null ? labelFont.deriveFont(Math.max(18f, labelFont.getSize2D()))
                    : new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            setFocusable(false);
            setOpaque(false);
        }
        @Override public boolean contains(int x, int y) { return false; }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color background = UIManager.getColor("ToolTip.background");
                Color foreground = UIManager.getColor("ToolTip.foreground");
                g.setColor(background != null ? background : new Color(25, 30, 40));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g.setFont(getFont());
                g.setColor(foreground != null ? foreground : Color.WHITE);
                FontMetrics metrics = g.getFontMetrics();
                String display = text;
                while (display.length() > 1 && metrics.stringWidth(display) > getWidth() - 36) {
                    display = display.substring(0, display.length() - 1);
                }
                if (!display.equals(text) && display.length() > 1) display = display.substring(0, display.length() - 1) + "…";
                g.drawString(display, 18, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            } finally {
                g.dispose();
            }
        }
    }
}
