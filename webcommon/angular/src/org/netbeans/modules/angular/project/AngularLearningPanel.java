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

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import org.openide.util.NbBundle;

/** Optional, keyboard-accessible explanations shared by Angular wizards. */
final class AngularLearningPanel extends JPanel {
    private final JTextArea text = new JTextArea();

    AngularLearningPanel() {
        super(new BorderLayout(0, 5));
        JToggleButton toggle = new JToggleButton(message("Learning.title"));
        toggle.setMnemonic('U');
        toggle.getAccessibleContext().setAccessibleDescription(message("Learning.toggle"));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        text.getAccessibleContext().setAccessibleName(message("Learning.title"));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(420, 180));
        scroll.setVisible(false);
        toggle.addActionListener(event -> {
            scroll.setVisible(toggle.isSelected());
            revalidate();
            repaint();
        });
        add(toggle, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    void explain(String... paragraphs) {
        text.setText(String.join("\n\n", paragraphs));
        text.setCaretPosition(0);
    }

    static String message(String key, Object... arguments) {
        return NbBundle.getMessage(AngularLearningPanel.class, key, arguments);
    }

    static int major(String version) {
        if (version == null) return -1;
        try {
            return Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
