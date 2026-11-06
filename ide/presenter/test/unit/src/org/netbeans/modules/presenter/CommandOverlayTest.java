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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import org.netbeans.junit.NbTestCase;

public class CommandOverlayTest extends NbTestCase {
    public CommandOverlayTest(String name) { super(name); }

    public void testPaintFadeClickThroughAndStop() throws Exception {
        AtomicLong clock = new AtomicLong();
        JLayeredPane pane = new JLayeredPane();
        CommandOverlay overlay = new CommandOverlay(() -> pane, () -> true, () -> null, clock::get);
        try {
            SwingUtilities.invokeAndWait(() -> {
                pane.setSize(800, 600);
                overlay.start();
                overlay.received(new CommandObserver.Command("Go to Declaration", null));
            });
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, pane.getComponentCount());
                Component bubble = pane.getComponent(0);
                assertTrue(bubble.isVisible());
                assertFalse(bubble.isFocusable());
                assertFalse(bubble.contains(20, 20));
                assertTrue(pane.getBounds().contains(bubble.getBounds()));
                int initialAlpha = paintedAlpha(bubble);
                assertTrue(initialAlpha > 200);
                clock.set(2_000_000_000L);
                overlay.tick();
                int fadingAlpha = paintedAlpha(bubble);
                assertTrue(fadingAlpha > 0 && fadingAlpha < initialAlpha);
                pane.setSize(160, 100);
                overlay.tick();
                assertTrue(pane.getBounds().contains(bubble.getBounds()));
                clock.set(2_300_000_000L);
                overlay.tick();
                assertFalse(bubble.isVisible());
                overlay.stop();
                assertEquals(0, pane.getComponentCount());
            });
        } finally {
            SwingUtilities.invokeAndWait(overlay::stop);
        }
    }

    public void testOverlayCornerAndControlStripAvoidance() throws Exception {
        JLayeredPane pane = new JLayeredPane();
        CommandOverlay overlay = new CommandOverlay(() -> pane, () -> true, () -> null, () -> 0L);
        Object original = javax.swing.UIManager.get("nb.presenter.controlsHeight");
        try {
            SwingUtilities.invokeAndWait(() -> {
                pane.setSize(800, 600);
                overlay.corner(PresenterPresets.Corner.TOP_LEFT);
                overlay.start();
                overlay.received(new CommandObserver.Command("Build Project", null));
            });
            SwingUtilities.invokeAndWait(() -> {
                Component bubble = pane.getComponent(0);
                assertEquals(16, bubble.getX());
                assertEquals(12, bubble.getY());
                overlay.corner(PresenterPresets.Corner.BOTTOM_RIGHT);
                overlay.tick();
                assertTrue(bubble.getX() > 16);
                int bottom = bubble.getY();
                javax.swing.UIManager.put("nb.presenter.controlsHeight", 80);
                overlay.tick();
                assertEquals(bottom - 80, bubble.getY());
            });
        } finally {
            SwingUtilities.invokeAndWait(overlay::stop);
            javax.swing.UIManager.put("nb.presenter.controlsHeight", original);
        }
    }

    public void testQueuedCommandsCannotLeakIntoNextSession() throws Exception {
        JLayeredPane pane = new JLayeredPane();
        CommandOverlay overlay = new CommandOverlay(() -> pane, () -> true, () -> null, () -> 0L);
        try {
            SwingUtilities.invokeAndWait(() -> {
                pane.setSize(800, 600);
                overlay.start();
                overlay.received(new CommandObserver.Command("Old session", null));
                overlay.stop();
                overlay.start();
            });
            SwingUtilities.invokeAndWait(() -> assertEquals(0, pane.getComponentCount()));
        } finally {
            SwingUtilities.invokeAndWait(overlay::stop);
        }
    }

    public void testInactiveWindowDoesNotDisplayCommands() throws Exception {
        JLayeredPane pane = new JLayeredPane();
        CommandOverlay overlay = new CommandOverlay(() -> pane, () -> false, () -> null, () -> 0L);
        try {
            SwingUtilities.invokeAndWait(() -> {
                pane.setSize(800, 600);
                overlay.start();
                overlay.received(new CommandObserver.Command("Save All", null));
            });
            SwingUtilities.invokeAndWait(() -> assertEquals(0, pane.getComponentCount()));
        } finally {
            SwingUtilities.invokeAndWait(overlay::stop);
        }
    }

    private static int paintedAlpha(Component component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(10, 10) >>> 24;
    }
}
