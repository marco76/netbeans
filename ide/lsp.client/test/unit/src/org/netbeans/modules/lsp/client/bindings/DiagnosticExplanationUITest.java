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
package org.netbeans.modules.lsp.client.bindings;

import java.awt.Dialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import junit.framework.TestCase;
import org.netbeans.modules.lsp.client.spi.DiagnosticExplanationProvider.Explanation;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.test.MockLookup;

public class DiagnosticExplanationUITest extends TestCase {
    public void testApplyingRequiresExplicitSelectionAndShowsServerAction() throws Exception {
        RecordingDisplayer displayer = new RecordingDisplayer();
        MockLookup.setInstances(displayer);
        try {
            SwingUtilities.invokeAndWait(() -> {
                Explanation explanation = new Explanation("Missing pipe", "Inspect imports", "Pipe resolves", "https://angular.dev/errors/NG0302");
                java.util.concurrent.atomic.AtomicInteger applied = new java.util.concurrent.atomic.AtomicInteger();
                DiagnosticExplanationUI.apply(explanation, "Import AsyncPipe", applied::incrementAndGet);
                assertEquals(0, applied.get());
                assertTrue(displayer.text.contains("Missing pipe"));
                assertTrue(displayer.text.contains("Inspect imports"));
                assertTrue(displayer.text.contains("Pipe resolves"));
                assertTrue(displayer.text.contains("Import AsyncPipe"));
                displayer.apply = true;
                DiagnosticExplanationUI.apply(explanation, "Import AsyncPipe", applied::incrementAndGet);
                assertEquals(1, applied.get());
                // Unsupported diagnostics retain the existing direct action behavior.
                DiagnosticExplanationUI.apply(null, "Other action", applied::incrementAndGet);
                assertEquals(2, applied.get());
                displayer.apply = false;
                assertFalse(DiagnosticExplanationUI.show(explanation, null));
                assertEquals(1, displayer.optionCount);
            });
        } finally {
            MockLookup.setInstances();
        }
    }

    private static final class RecordingDisplayer extends DialogDisplayer {
        boolean apply;
        String text;
        int optionCount;
        @Override public Object notify(NotifyDescriptor descriptor) {
            JPanel panel = (JPanel) descriptor.getMessage();
            text = ((JTextArea) ((JScrollPane) panel.getComponent(0)).getViewport().getView()).getText();
            optionCount = descriptor.getOptions().length;
            return apply ? descriptor.getOptions()[0] : NotifyDescriptor.CANCEL_OPTION;
        }
        @Override public Dialog createDialog(DialogDescriptor descriptor) { throw new UnsupportedOperationException(); }
    }
}
