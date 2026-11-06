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
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.NotificationDisplayer;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;

/** Optional post-generation tour; never changes the generated source. */
final class AngularGeneratedFilesPanel extends JPanel {
    private final JList<AngularGeneratedFiles.Entry> files;
    private final JTextArea explanation = new JTextArea();
    private final JButton open = new JButton(AngularLearningPanel.message("Explore.open"));

    static void offer(List<AngularGeneratedFiles.Entry> entries) {
        if (entries.isEmpty()) return;
        NotificationDisplayer.getDefault().notify(
                AngularLearningPanel.message("Explore.title"),
                ImageUtilities.loadImageIcon("org/netbeans/modules/angular/resources/angular_icon_16.png", false),
                AngularLearningPanel.message("Explore.offer"), event -> {
                    DialogDescriptor descriptor = new DialogDescriptor(new AngularGeneratedFilesPanel(entries),
                            AngularLearningPanel.message("Explore.title"), false,
                            new Object[]{NotifyDescriptor.CLOSED_OPTION}, NotifyDescriptor.CLOSED_OPTION,
                            DialogDescriptor.DEFAULT_ALIGN, null, null);
                    DialogDisplayer.getDefault().createDialog(descriptor).setVisible(true);
                });
    }

    AngularGeneratedFilesPanel(List<AngularGeneratedFiles.Entry> entries) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextArea intro = new JTextArea(AngularLearningPanel.message("Learning.component"));
        configureText(intro);
        add(intro, BorderLayout.NORTH);
        files = new JList<>(entries.toArray(AngularGeneratedFiles.Entry[]::new));
        files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        files.getAccessibleContext().setAccessibleName(AngularLearningPanel.message("Explore.files"));
        configureText(explanation);
        explanation.getAccessibleContext().setAccessibleName(AngularLearningPanel.message("Explore.explanation"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(files), new JScrollPane(explanation));
        split.setResizeWeight(0.5);
        split.setPreferredSize(new Dimension(650, 350));
        add(split, BorderLayout.CENTER);
        open.setMnemonic('O');
        open.addActionListener(event -> openSelected());
        add(open, BorderLayout.SOUTH);
        files.addListSelectionListener(event -> {
            AngularGeneratedFiles.Entry selected = files.getSelectedValue();
            explanation.setText(selected == null ? "" : selected.explanation());
            explanation.setCaretPosition(0);
            open.setEnabled(selected != null && selected.file().isValid());
        });
        open.setEnabled(false);
        if (!entries.isEmpty()) files.setSelectedIndex(0);
    }

    private static void configureText(JTextArea text) {
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
    }

    private void openSelected() {
        AngularGeneratedFiles.Entry selected = files.getSelectedValue();
        if (selected == null) return;
        if (!selected.file().isValid()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    AngularLearningPanel.message("Explore.missing"), NotifyDescriptor.INFORMATION_MESSAGE));
            open.setEnabled(false);
            return;
        }
        try {
            DataObject data = DataObject.find(selected.file());
            LineCookie lines = data.getLookup().lookup(LineCookie.class);
            if (lines != null) {
                try {
                    lines.getLineSet().getCurrent(selected.line()).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                    return;
                } catch (IndexOutOfBoundsException ex) {
                    // The developer may have edited the file since generation; open the file instead.
                }
            }
            OpenCookie cookie = data.getLookup().lookup(OpenCookie.class);
            if (cookie != null) cookie.open();
        } catch (org.openide.loaders.DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
