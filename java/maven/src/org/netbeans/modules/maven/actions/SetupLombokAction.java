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

package org.netbeans.modules.maven.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.modules.maven.NbMavenProjectImpl;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.model.Utilities;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.cookies.SaveCookie;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

@ActionID(category = "Project", id = "org.netbeans.modules.maven.actions.SetupLombokAction")
@ActionRegistration(displayName = "#ACT_SetupLombok")
@ActionReference(path = "Projects/org-netbeans-modules-maven/Actions", position = 1660)
@Messages({"ACT_SetupLombok=Set up Lombok",
    "MSG_LombokProgress=Setting up Lombok...",
    "MSG_LombokNoProject=Select a Maven project in the Projects window and try Set up Lombok again.",
    "# {0} - POM path",
    "MSG_LombokConfigured=Lombok configuration saved to {0}. The project is reloading and dependency download has been requested. Run Build to verify annotation processing. Check Maven output if dependencies remain unresolved.",
    "MSG_LombokFailed=Could not configure Lombok. Check that pom.xml is valid and writable."})
public final class SetupLombokAction implements ActionListener {
    private static final Logger LOG = Logger.getLogger(SetupLombokAction.class.getName());
    private static final RequestProcessor RP = new RequestProcessor(SetupLombokAction.class);
    private final List<NbMavenProjectImpl> projects;

    public SetupLombokAction(List<NbMavenProjectImpl> projects) {
        this.projects = List.copyOf(projects);
    }

    @Override public void actionPerformed(ActionEvent event) {
        if (projects.isEmpty()) {
            DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                    Bundle.MSG_LombokNoProject(), NotifyDescriptor.WARNING_MESSAGE));
            return;
        }
        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_LombokProgress());
        RP.post(() -> {
            ProgressHandle progress = ProgressHandle.createHandle(Bundle.MSG_LombokProgress());
            progress.start();
            try {
                for (NbMavenProjectImpl project : projects) {
                    try {
                        FileObject pom = project.getProjectDirectory().getFileObject("pom.xml");
                        if (pom == null || !pom.canWrite()) {
                            throw new IllegalStateException(Bundle.MSG_LombokFailed());
                        }
                        progress.progress(pom.getPath());
                        var effective = project.getLookup().lookup(NbMavenProject.class).getMavenProject();
                        LombokSetup.validate(effective);
                        // Utilities reports model/save failures itself; report completion only after verification.
                        Utilities.performPOMModelOperations(pom, List.of(model -> LombokSetup.configure(model, effective)));
                        final boolean[] configured = {false};
                        Utilities.performPOMModelOperations(pom, List.of(model -> configured[0] = LombokSetup.isConfigured(model)));
                        if (!configured[0]) {
                            throw new IllegalStateException(Bundle.MSG_LombokFailed());
                        }
                        // A previous save may have failed while the in-memory model remained configured.
                        SaveCookie save = DataObject.find(pom).getLookup().lookup(SaveCookie.class);
                        if (save != null) {
                            save.save();
                        }
                        project.fireProjectReload().waitFinished();
                        project.getLookup().lookup(NbMavenProject.class).triggerDependencyDownload();
                        String message = Bundle.MSG_LombokConfigured(pom.getPath());
                        StatusDisplayer.getDefault().setStatusText(message);
                        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                                message, NotifyDescriptor.INFORMATION_MESSAGE));
                    } catch (IOException | RuntimeException ex) {
                        LOG.log(Level.WARNING, "Lombok setup failed for " + project.getProjectDirectory(), ex);
                        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                                Bundle.MSG_LombokFailed() + "\n" + ex.getLocalizedMessage(), NotifyDescriptor.ERROR_MESSAGE));
                    }
                }
            } finally {
                progress.finish();
            }
        });
    }
}
