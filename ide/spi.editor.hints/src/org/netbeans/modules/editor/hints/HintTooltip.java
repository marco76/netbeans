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
package org.netbeans.modules.editor.hints;

import java.awt.Color;
import javax.swing.UIManager;
import org.openide.util.NbBundle;

/** HTML presentation of diagnostics and their editor action. */
final class HintTooltip {
    static final String SHOW_HINTS_ACTION = "show-diagnostic-hints"; // NOI18N
    private HintTooltip() { }

    static String format(String description) {
        if (description == null || description.isEmpty()) return null;
        Color foreground = UIManager.getColor("nb.completion.documentation.foreground"); // NOI18N
        if (foreground == null) foreground = UIManager.getColor("ToolTip.foreground"); // NOI18N
        if (foreground == null) foreground = Color.BLACK;
        Color background = UIManager.getColor("nb.completion.documentation.background"); // NOI18N
        if (background == null) background = UIManager.getColor("ToolTip.background"); // NOI18N
        if (background == null) background = Color.WHITE;
        Color muted = blend(background, foreground, 0.75);
        Color separator = blend(background, foreground, 0.16);
        Color badge = blend(background, foreground, 0.07);
        return "<html><body><div>" + escape(description).replace("\r\n", "\n").replace("\n", "<br>") // NOI18N
                + "</div><table cellspacing='0' cellpadding='0' style='margin-top:10px; border-top:1px solid " + hex(separator) // NOI18N
                + ";'><tr><td style='padding-top:8px; padding-right:8px;'><code style='background-color:" + hex(badge) // NOI18N
                + ";'><a href='editor-action:" + SHOW_HINTS_ACTION + "'>" + escape(NbBundle.getMessage(HintTooltip.class, "LBL_hint_shortcut")) // NOI18N
                + "</a></code></td><td style='padding-top:8px; color:" + hex(muted) + "; font-size:90%;'><a href='editor-action:" + SHOW_HINTS_ACTION + "'>" // NOI18N
                + escape(NbBundle.getMessage(HintTooltip.class, "LBL_hint_actions")) + "</a></td></tr></table></body></html>"; // NOI18N
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); // NOI18N
    }

    private static Color blend(Color background, Color foreground, double amount) {
        return new Color((int) (background.getRed() * (1 - amount) + foreground.getRed() * amount),
                (int) (background.getGreen() * (1 - amount) + foreground.getGreen() * amount),
                (int) (background.getBlue() * (1 - amount) + foreground.getBlue() * amount));
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); // NOI18N
    }
}
