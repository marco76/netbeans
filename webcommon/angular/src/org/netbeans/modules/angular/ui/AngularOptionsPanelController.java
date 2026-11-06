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
package org.netbeans.modules.angular.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.modules.angular.lsp.AngularOptions;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.*;

@NbBundle.Messages("AngularOptionsName=Angular")
@OptionsPanelController.SubRegistration(location="Html5", id="Angular", displayName="#AngularOptionsName")
public final class AngularOptionsPanelController extends OptionsPanelController {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final String[] keys = {AngularOptions.KEY_NODE_PATH, AngularOptions.KEY_NG_CLI_PATH,
        AngularOptions.KEY_NGSERVER_PATH, AngularOptions.KEY_TS_PROBE_LOCATIONS, AngularOptions.KEY_NG_PROBE_LOCATIONS};
    private final String[] labels = {"Node.js executable", "Angular CLI JavaScript entry (ng.js)",
        "Angular language server entry", "TypeScript probe locations", "Angular probe locations"};
    private final JTextField[] fields = new JTextField[keys.length];
    private JPanel panel;
    private JLabel error;

    private JPanel panel() {
        if (panel == null) {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(new JLabel("Leave paths empty for automatic discovery. Separate probe locations with commas."));
            DocumentListener listener = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { changed(); }
                public void removeUpdate(DocumentEvent e) { changed(); }
                public void changedUpdate(DocumentEvent e) { changed(); }
            };
            for (int i=0; i<fields.length; i++) {
                JLabel label = new JLabel(labels[i]);
                fields[i] = new JTextField(45);
                fields[i].setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, fields[i].getPreferredSize().height));
                label.setLabelFor(fields[i]);
                fields[i].getDocument().addDocumentListener(listener);
                panel.add(label); panel.add(fields[i]);
            }
            error = new JLabel(" "); panel.add(error);
        }
        return panel;
    }
    private void changed() {
        changes.firePropertyChange(PROP_CHANGED, null, null);
        changes.firePropertyChange(PROP_VALID, null, null);
    }
    @Override public void update() {
        panel();
        for (int i=0; i<fields.length; i++) fields[i].setText(AngularOptions.getInstance().getConfiguredPath(keys[i]));
    }
    @Override public void applyChanges() {
        String[] values = java.util.Arrays.stream(fields).map(f -> f.getText().trim()).toArray(String[]::new);
        AngularOptions o = AngularOptions.getInstance();
        o.setNodePath(values[0]); o.setNgCliPath(values[1]); o.setNgServerPath(values[2]);
        o.setCustomTsProbeLocations(values[3]); o.setCustomNgProbeLocations(values[4]);
    }
    @Override public void cancel() { update(); }
    @Override public boolean isValid() {
        panel();
        for (int i=0; i<3; i++) {
            String value=fields[i].getText().trim();
            if (!value.isEmpty() && (!new File(value).isFile() || (i==0 && !new File(value).canExecute()))) {
                error.setText("Choose a valid file for " + labels[i]); return false;
            }
        }
        if (fields[1].getText().matches("(?i).*\\.(cmd|bat)$")) {
            error.setText("Choose @angular/cli/bin/ng.js instead of a command wrapper."); return false;
        }
        error.setText(" "); return true;
    }
    @Override public boolean isChanged() {
        if (panel==null) return false;
        for (int i=0;i<fields.length;i++) if (!fields[i].getText().trim().equals(AngularOptions.getInstance().getConfiguredPath(keys[i]))) return true;
        return false;
    }
    @Override public JComponent getComponent(Lookup lookup) { return panel(); }
    @Override public HelpCtx getHelpCtx() { return HelpCtx.DEFAULT_HELP; }
    @Override public void addPropertyChangeListener(PropertyChangeListener l) { changes.addPropertyChangeListener(l); }
    @Override public void removePropertyChangeListener(PropertyChangeListener l) { changes.removePropertyChangeListener(l); }
}
