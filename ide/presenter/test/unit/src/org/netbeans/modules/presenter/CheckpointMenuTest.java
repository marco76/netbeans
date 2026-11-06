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

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.netbeans.junit.NbTestCase;

public class CheckpointMenuTest extends NbTestCase {
    public CheckpointMenuTest(String name) { super(name); }
    public void testNativeMenuStartsPopulatedAndRefreshesBeforeDisplay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int[] count = {0};
            DemoCheckpoints.CheckpointMenu menu = new DemoCheckpoints.CheckpointMenu(target -> {
                target.add(new JMenuItem("Checkpoint " + ++count[0]));
            });
            assertEquals(1, menu.getItemCount());
            assertEquals("Checkpoint 1", menu.getItem(0).getText());
            JComponent[] presenters = menu.getMenuPresenters();
            assertSame(menu, presenters[0]);
            assertEquals(1, menu.getItemCount());
            assertEquals("Checkpoint 2", menu.getItem(0).getText());
            menu.synchMenuPresenters(presenters);
            assertEquals(1, menu.getItemCount());
            assertEquals("Checkpoint 3", menu.getItem(0).getText());
        });
    }
}
