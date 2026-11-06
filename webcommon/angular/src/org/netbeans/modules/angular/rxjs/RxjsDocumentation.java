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
import javax.swing.UIManager;

/** Small Swing-HTML palette that follows the current look and feel. */
final class RxjsDocumentation {
    private RxjsDocumentation() {}
    static String color(Color color) { return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    static boolean dark() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null && (bg.getRed() * .299 + bg.getGreen() * .587 + bg.getBlue() * .114) < 128;
    }
    static Color accent() { return dark() ? new Color(125, 190, 255) : new Color(27, 94, 164); }
    static Color eventColor(int index) {
        Color[] light = {new Color(25, 103, 180), new Color(138, 63, 163), new Color(0, 116, 103), new Color(164, 87, 12)};
        Color[] dark = {new Color(126, 188, 255), new Color(215, 156, 242), new Color(105, 214, 190), new Color(255, 193, 112)};
        return (dark() ? dark : light)[Math.floorMod(index, light.length)];
    }
    static String wrap(String body) {
        Color fg = UIManager.getColor("Label.foreground");
        Color bg = UIManager.getColor("Panel.background");
        if (fg == null) fg = Color.BLACK;
        if (bg == null) bg = Color.WHITE;
        String accent = color(accent());
        return "<html><head><style>body { font-family: sans-serif; font-size: 12pt; margin: 14px; color: " + color(fg)
                + "; background-color: " + color(bg) + "; } h2 { color: " + accent
                + "; font-size: 20pt; margin-top: 4px; margin-bottom: 8px; } p { margin-top: 6px; margin-bottom: 10px; } h3 { color: " + accent + "; font-size: 12pt; margin-top: 16px; margin-bottom: 6px; }"
                + " a { color: " + accent + "; } code { font-family: monospace; } pre { font-family: monospace; font-size: 11pt; padding: 12px; background-color: "
                + (dark() ? "#283442" : "#edf4fb") + "; } .category { color: "
                + (dark() ? "#cf9cf3" : "#78409a") + "; } .flow { color: "
                + (dark() ? "#69d6be" : "#007467") + "; } .caution { padding: 10px; background-color: "
                + (dark() ? "#423822" : "#fff3d8") + "; }</style></head><body>" + body + "</body></html>";
    }
}
