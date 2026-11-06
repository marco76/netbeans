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

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import org.netbeans.junit.NbTestCase;

public class EditorZoomTest extends NbTestCase {
    public EditorZoomTest(String name) { super(name); }

    public void testTargetSizeIsIndependentOfOriginalZoomAndAdjustable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorZoom zoom = new EditorZoom();
            JEditorPane first = new JEditorPane();
            JEditorPane second = new JEditorPane();
            first.putClientProperty("text-zoom", 3);
            zoom.target(first, 26, 14);
            zoom.target(second, 26, 18);
            assertEquals(12, first.getClientProperty("text-zoom"));
            assertEquals(8, second.getClientProperty("text-zoom"));
            zoom.target(first, 22, 14);
            assertEquals(8, first.getClientProperty("text-zoom"));
            first.putClientProperty("text-zoom", 9);
            zoom.target(first, 22, 14);
            assertEquals(9, first.getClientProperty("text-zoom"));
            zoom.restore();
            assertEquals(3, first.getClientProperty("text-zoom"));
            assertNull(second.getClientProperty("text-zoom"));
        });
    }

    public void testRestoresUnsetAndExistingZoomWithoutAccumulating() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorZoom zoom = new EditorZoom();
            JEditorPane first = new JEditorPane();
            JEditorPane second = new JEditorPane();
            second.putClientProperty("text-zoom", -2);
            zoom.apply(first, 8);
            zoom.apply(first, 8);
            zoom.apply(second, 8);
            assertEquals(8, first.getClientProperty("text-zoom"));
            assertEquals(6, second.getClientProperty("text-zoom"));
            // A manual zoom during the demo must not replace the original value.
            first.putClientProperty("text-zoom", 12);
            zoom.apply(first, 8);
            assertEquals(12, first.getClientProperty("text-zoom"));
            zoom.restore();
            assertNull(first.getClientProperty("text-zoom"));
            assertEquals(-2, second.getClientProperty("text-zoom"));
            zoom.apply(first, 4);
            assertEquals(4, first.getClientProperty("text-zoom"));
            zoom.restore();
            assertNull(first.getClientProperty("text-zoom"));
        });
    }
}
