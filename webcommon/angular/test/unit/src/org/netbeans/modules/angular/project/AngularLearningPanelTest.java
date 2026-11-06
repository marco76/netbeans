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
package org.netbeans.modules.angular.project;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import junit.framework.TestCase;

public class AngularLearningPanelTest extends TestCase {
    public void testExplanationsStayOptionalAndUpdate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AngularLearningPanel panel = new AngularLearningPanel();
            JToggleButton toggle = (JToggleButton) panel.getComponent(0);
            JScrollPane scroll = (JScrollPane) panel.getComponent(1);
            JTextArea text = (JTextArea) scroll.getViewport().getView();
            assertFalse(scroll.isVisible());
            panel.explain(AngularLearningPanel.message("Learning.styles", "Inline", "scss"));
            toggle.doClick();
            assertTrue(scroll.isVisible());
            assertTrue(text.getText().contains("Inline"));
            assertTrue(text.getText().contains("h2 { color: navy; }"));
            panel.explain(AngularLearningPanel.message("Learning.tests", "Skip"));
            assertTrue(text.getText().contains("Skip"));
            assertFalse(text.getText().contains("Inline"));
            toggle.doClick();
            assertFalse(scroll.isVisible());
        });
    }

    public void testVersionMetadata() {
        assertEquals(18, AngularLearningPanel.major("18.2.0"));
        assertEquals(22, AngularLearningPanel.major("22.0.0-next.1"));
        assertEquals(-1, AngularLearningPanel.major(null));
        assertEquals(-1, AngularLearningPanel.major("unknown"));
    }
}
