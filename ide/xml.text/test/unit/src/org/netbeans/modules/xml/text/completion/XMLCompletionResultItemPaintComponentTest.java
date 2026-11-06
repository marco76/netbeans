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

package org.netbeans.modules.xml.text.completion;

import java.awt.Color;
import java.awt.Font;
import org.netbeans.junit.NbTestCase;

public class XMLCompletionResultItemPaintComponentTest extends NbTestCase {
    public XMLCompletionResultItemPaintComponentTest(String name) {
        super(name);
    }

    public void testEachRowKeepsItsOwnTextAndUsesActualFontWidth() {
        var first = new XMLCompletionResultItemPaintComponent.StringPaintComponent(Color.BLACK);
        var second = new XMLCompletionResultItemPaintComponent.StringPaintComponent(Color.BLACK);
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        first.setFont(font);
        second.setFont(font);
        first.setString("artifactId");
        int width = first.getPreferredSize().width;
        second.setString("version");
        assertEquals(width, first.getPreferredSize().width);
        assertEquals(first.getInsets().left + first.getInsets().right
                + first.getFontMetrics(font).stringWidth("artifactId"), width);
    }

    public void testNeutralTextUsesThemeForeground() {
        var row = new XMLCompletionResultItemPaintComponent();
        row.setForeground(Color.WHITE);
        assertEquals(Color.WHITE, row.getColor("version", Color.BLACK));
        assertEquals(Color.WHITE, row.getColor("version", Color.DARK_GRAY));
        row.setSelected(true);
        assertEquals(Color.WHITE, row.getColor("version", Color.BLUE));
    }
}
