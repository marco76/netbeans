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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import javax.swing.*;
import org.netbeans.junit.NbTestCase;

public class PresentationControlsTest extends NbTestCase {
    public PresentationControlsTest(String name) { super(name); }
    public void testReservesSpaceAndRestoresMainToolbarAndStatusBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestRoot root = new TestRoot();
            JPanel content = new JPanel(new BorderLayout());
            JPanel toolbar = new JPanel();
            JLabel status = new JLabel("Status");
            content.add(toolbar, BorderLayout.NORTH); content.add(status, BorderLayout.SOUTH);
            root.setContentPane(content);
            PresentationControls controls = new PresentationControls(null, () -> root);
            try {
                controls.show(PresenterPresets.Preset.LIVE_TALK.defaults);
                BorderLayout layout = (BorderLayout) content.getLayout();
                Container header = (Container) layout.getLayoutComponent(BorderLayout.NORTH);
                assertNotSame(toolbar, header);
                assertSame(toolbar, ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.NORTH));
                assertSame(status, layout.getLayoutComponent(BorderLayout.SOUTH));
                controls.show(PresenterPresets.Preset.YOUTUBE.defaults);
                assertSame(header, layout.getLayoutComponent(BorderLayout.NORTH));
                assertEquals(2, header.getComponentCount());
                controls.hide();
                assertSame(toolbar, layout.getLayoutComponent(BorderLayout.NORTH));
                assertSame(content, toolbar.getParent());
                assertSame(status, layout.getLayoutComponent(BorderLayout.SOUTH));
            } finally { controls.hide(); }
        });
    }
    private static final class TestRoot implements RootPaneContainer {
        private final JRootPane root = new JRootPane();
        @Override public JRootPane getRootPane() { return root; }
        @Override public Container getContentPane() { return root.getContentPane(); }
        @Override public void setContentPane(Container content) { root.setContentPane(content); }
        @Override public JLayeredPane getLayeredPane() { return root.getLayeredPane(); }
        @Override public void setLayeredPane(JLayeredPane pane) { root.setLayeredPane(pane); }
        @Override public Component getGlassPane() { return root.getGlassPane(); }
        @Override public void setGlassPane(Component glass) { root.setGlassPane(glass); }
    }
}
