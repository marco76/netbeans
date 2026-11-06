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
package org.netbeans.modules.angular.debug;

import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.*;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.angular.project.AngularProject;
import org.openide.awt.*;
import org.openide.filesystems.FileObject;
import org.openide.util.*;
import org.openide.util.actions.Presenter;

/** Project-aware Firefox launch button with nearby session controls. */
@ActionID(category="Debug", id="org.netbeans.modules.angular.debug.DebugFirefoxAction")
@ActionRegistration(displayName="#CTL_DebugFirefox", lazy=false)
@ActionReference(path="Toolbars/Build", position=550)
@NbBundle.Messages("CTL_DebugFirefox=Debug in Firefox")
public final class DebugFirefoxAction extends AbstractAction implements Presenter.Toolbar, LookupListener {
    private final Lookup.Result<Project> projects=Utilities.actionsGlobalContext().lookupResult(Project.class);
    private final Lookup.Result<FileObject> files=Utilities.actionsGlobalContext().lookupResult(FileObject.class);
    private final java.beans.PropertyChangeListener openProjectsListener=e -> update();
    public DebugFirefoxAction() {
        putValue(NAME,Bundle.CTL_DebugFirefox());
        putValue(SHORT_DESCRIPTION,"Debug the selected Angular project in Firefox");
        putValue(SMALL_ICON,icon("firefox-debug"));
        putValue("iconBase","org/netbeans/modules/angular/debug/firefox-debug.svg");
        projects.addLookupListener(WeakListeners.create(LookupListener.class,this,projects));
        files.addLookupListener(WeakListeners.create(LookupListener.class,this,files));
        OpenProjects.getDefault().addPropertyChangeListener(WeakListeners.propertyChange(openProjectsListener,OpenProjects.getDefault()));
        update();
    }
    private AngularProject project() {
        Project selected=Utilities.actionsGlobalContext().lookup(Project.class);
        FileObject file=Utilities.actionsGlobalContext().lookup(FileObject.class);
        if (selected==null && file!=null) selected=FileOwnerQuery.getOwner(file);
        if (selected==null) selected=OpenProjects.getDefault().getMainProject();
        return selected==null ? null : selected.getLookup().lookup(AngularProject.class);
    }
    private void update() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::update);
            return;
        }
        boolean available = project()!=null;
        setEnabled(available);
        putValue(SHORT_DESCRIPTION, available
                ? "Debug the selected Angular project in Firefox"
                : "Select an Angular project or one of its files to debug in Firefox");
    }
    @Override public void resultChanged(LookupEvent event) { update(); }
    @Override public void actionPerformed(ActionEvent event) {
        AngularProject project=project();
        if (project!=null) FirefoxDebugger.debug(project);
    }
    private static Icon icon(String name) {
        return ImageUtilities.loadImageIcon("org/netbeans/modules/angular/debug/"+name+".svg",true);
    }
    @Override public Component getToolbarPresenter() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem settings = new JMenuItem("Debug settings…", icon("debug-settings"));
        settings.addActionListener(e -> {
            AngularProject selected = project();
            if (selected != null) FirefoxDebugger.show(selected);
        });
        menu.add(settings);
        menu.addSeparator();
        JMenuItem restart = new JMenuItem("Restart session", icon("debug-restart"));
        restart.addActionListener(e -> {
            AngularProject selected = project();
            if (selected != null) FirefoxDebugger.restart(selected);
        });
        menu.add(restart);
        JMenuItem stop = new JMenuItem("Stop Angular + Firefox", icon("debug-stop"));
        stop.addActionListener(e -> {
            AngularProject selected = project();
            if (selected != null) FirefoxDebugger.stop(selected);
        });
        menu.add(stop);

        JButton button = DropDownButtonFactory.createDropDownButton(icon("firefox-debug"), menu);
        Actions.connect(button, this);
        button.setFocusable(false);
        button.getAccessibleContext().setAccessibleName(Bundle.CTL_DebugFirefox());
        return button;
    }
}
