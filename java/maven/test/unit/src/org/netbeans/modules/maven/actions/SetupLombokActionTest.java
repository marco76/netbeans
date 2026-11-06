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

import java.awt.Dialog;
import java.util.List;
import javax.swing.SwingUtilities;
import org.netbeans.junit.MockServices;
import org.netbeans.junit.NbTestCase;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

public class SetupLombokActionTest extends NbTestCase {
    public SetupLombokActionTest(String name) { super(name); }

    public void testMissingProjectShowsWarning() throws Exception {
        MockServices.setServices(RecordingDialogs.class);
        try {
            RecordingDialogs dialogs = (RecordingDialogs) DialogDisplayer.getDefault();
            SwingUtilities.invokeAndWait(() -> new SetupLombokAction(List.of()).actionPerformed(null));
            SwingUtilities.invokeAndWait(() -> {});
            assertNotNull("An empty selection must not silently do nothing", dialogs.message);
            assertEquals(NotifyDescriptor.WARNING_MESSAGE, dialogs.message.getMessageType());
            assertEquals(Bundle.MSG_LombokNoProject(), dialogs.message.getMessage());
        } finally {
            MockServices.setServices();
        }
    }

    public static final class RecordingDialogs extends DialogDisplayer {
        volatile NotifyDescriptor message;

        @Override public Object notify(NotifyDescriptor descriptor) {
            message = descriptor;
            return NotifyDescriptor.OK_OPTION;
        }

        @Override public Dialog createDialog(DialogDescriptor descriptor) {
            throw new AssertionError("Unexpected dialog");
        }
    }
}
