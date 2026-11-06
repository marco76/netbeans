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
package org.netbeans.modules.angular.rxjs;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.*;
import junit.framework.TestCase;

public class RxjsMarblesTest extends TestCase {
    public void testMapPreservesTimingAndChangesValues() {
        var result = RxjsMarbles.calculate("-a-b-c-|", 0);
        assertEquals("-a-b-c-|", result.output());
        assertEquals(Integer.valueOf(20), result.outputValues().get('b'));
        assertEquals(3, result.result().get(1).frame());
        assertTrue(result.test().contains("map(value => value * 10)"));
        assertTrue(result.test().contains("{ a: 10, b: 20, c: 30 }"));
    }
    public void testFilterPreservesCompletionAndFrames() {
        var result = RxjsMarbles.calculate("-a-b-c-d-|", 1);
        assertEquals("---b---d-|", result.output());
        assertEquals(3, result.result().get(0).frame());
        assertEquals("---------|", RxjsMarbles.calculate("-a-c-e-g-|", 1).output());
    }
    public void testTakeCompletesInSameFrameAndIgnoresLaterError() {
        var result = RxjsMarbles.calculate("-a-b-c-#", 2);
        assertEquals("-a-(b|)", result.output());
        assertEquals(3, result.result().get(2).frame());
        assertEquals(5, result.source().get(2).frame());
        assertEquals("-a-|", RxjsMarbles.calculate("-a-|", 2).output());
        assertEquals("-#", RxjsMarbles.calculate("-#", 2).output());
    }
    public void testDistinctKeepsOriginalFramePositions() {
        assertEquals("-a---b---a-|", RxjsMarbles.calculate("-a-a-b-b-a-|", 3).output());
        assertEquals("|", RxjsMarbles.calculate("|", 3).output());
        assertEquals("-a-#", RxjsMarbles.calculate("-a-#", 0).output());
    }
    public void testRejectsUnsupportedOrMalformedTimelines() {
        for (String input : new String[]{"", "-a", "a|b", "(ab)|", "1ms a|", "a||", "A|", "-".repeat(81) + "|"}) {
            try { RxjsMarbles.calculate(input, 0); fail(input); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Use up to 80")); }
        }
    }
    public void testLightAndDarkDocumentationAndPanelRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object bg = UIManager.get("Panel.background"), fg = UIManager.get("Label.foreground");
            try {
                for (boolean dark : new boolean[]{false, true}) {
                    UIManager.put("Panel.background", dark ? new Color(35, 39, 45) : Color.WHITE);
                    UIManager.put("Label.foreground", dark ? new Color(225, 230, 235) : new Color(30, 35, 40));
                    String html = RxjsOperators.find("switchMap").html();
                    assertTrue(html.contains("class='caution'"));
                    assertTrue(html.contains(dark ? "#423822" : "#fff3d8"));
                    JPanel panel = new RxjsMarblePanel(text -> {});
                    panel.setSize(1050, 650); layout(panel);
                    BufferedImage image = new BufferedImage(1050, 650, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    panel.printAll(graphics); graphics.dispose();
                    JEditorPane doc = new JEditorPane("text/html", html);
                    doc.setSize(700, 700);
                    image = new BufferedImage(700, 700, BufferedImage.TYPE_INT_RGB);
                    graphics = image.createGraphics(); doc.printAll(graphics); graphics.dispose();
                }
            } finally { UIManager.put("Panel.background", bg); UIManager.put("Label.foreground", fg); }
        });
    }
    private static void layout(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents()) if (child instanceof java.awt.Container container) layout(container);
    }
}
