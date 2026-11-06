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

package org.netbeans.modules.editor.java;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.netbeans.junit.NbTestCase;

public class MethodParamsTipPaintComponentTest extends NbTestCase {
    public MethodParamsTipPaintComponentTest(String name) {
        super(name);
    }

    public void testAttributeExpansionActiveRowAndTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea editor = new JTextArea();
            editor.setBackground(new Color(40, 40, 40));
            editor.setForeground(Color.WHITE);
            MethodParamsTipPaintComponent tip = new MethodParamsTipPaintComponent(editor);
            tip.setData(Collections.singletonList(Collections.singletonList("unused")), -1);
            tip.setAnnotationAttributes(Arrays.asList(
                    Arrays.asList("name", "String", "\"\"", ""),
                    Arrays.asList("value", "String[]", "{}", ""),
                    Arrays.asList("path", "String[]", "{}", ""),
                    Arrays.asList("produces", "String[]", "{}", ""),
                    Arrays.asList("consumes", "String[]", "{}", ""),
                    Arrays.asList("headers", "String[]", "{}", "Use <header> & value")), 5);
            JLabel view = (JLabel) tip.getComponent(0);
            JButton more = (JButton) tip.getComponent(1);
            assertEquals(editor.getBackground(), tip.getBackground());
            assertFalse(view.getText().contains("<td>name</td>"));
            assertTrue(view.getText().contains("<b>▸ headers</b>"));
            assertTrue(view.getText().contains("Use &lt;header&gt; &amp; value"));
            assertTrue(view.getText().indexOf("value</td>") < view.getText().indexOf("path</td>"));
            more.doClick();
            assertTrue(view.getText().contains("<td>name</td>"));
            more.doClick();
            assertFalse(view.getText().contains("<td>name</td>"));
            tip.setAnnotationAttributes(null, -1);
            assertEquals(0, tip.getComponentCount());
            assertTrue(tip.getPreferredSize().height > 0);
        });
    }
}
