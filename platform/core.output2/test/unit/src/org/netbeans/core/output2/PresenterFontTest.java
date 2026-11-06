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
package org.netbeans.core.output2;

import java.awt.Font;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.netbeans.core.output2.options.OutputOptions;
import org.netbeans.junit.NbTestCase;

public class PresenterFontTest extends NbTestCase {
    public PresenterFontTest(String name) { super(name); }

    public void testExistingAndNewPanesRestoreWithoutChangingOptions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object previous = UIManager.get("nb.presenter.fontIncrease");
            Font configured = OutputOptions.getDefault().getFont();
            try {
                UIManager.put("nb.presenter.fontIncrease", null);
                OutputPane existing = new OutputPane(null);
                Font original = new Font(Font.MONOSPACED, Font.BOLD, 13);
                existing.setViewFont(original);
                UIManager.put("nb.presenter.fontIncrease", 8);
                assertEquals(original.deriveFont(21f), existing.getViewFont());
                OutputPane added = new OutputPane(null);
                added.setViewFont(original.deriveFont(15f));
                assertEquals(23, added.getViewFont().getSize());
                UIManager.put("nb.presenter.fontIncrease", 4);
                assertEquals(17, existing.getViewFont().getSize());
                assertEquals(19, added.getViewFont().getSize());
                UIManager.put("nb.presenter.fontIncrease", null);
                UIManager.put("nb.presenter.outputFontSize", 22);
                assertEquals(original.deriveFont(22f), existing.getViewFont());
                assertEquals(22, added.getViewFont().getSize());
                UIManager.put("nb.presenter.outputFontSize", null);
                assertEquals(original, existing.getViewFont());
                assertEquals(original.deriveFont(15f), added.getViewFont());
                assertEquals(configured, OutputOptions.getDefault().getFont());
            } finally {
                UIManager.put("nb.presenter.fontIncrease", previous);
            }
        });
    }
}
