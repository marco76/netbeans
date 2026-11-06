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

package org.netbeans.modules.debugger.ui.actions;

import java.util.Arrays;
import javax.swing.Action;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.debugger.ui.BreakpointAnnotation;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

public class DebuggerActionTest extends NbTestCase {
    public DebuggerActionTest(String name) {
        super(name);
    }

    public void testMarkerRemovalWithoutDebuggerOrEditor() {
        var manager = DebuggerManager.getDebuggerManager();
        Breakpoint selected = breakpoint();
        Breakpoint unrelated = breakpoint();
        manager.addBreakpoint(selected);
        manager.addBreakpoint(unrelated);
        try {
            var toggle = DebuggerAction.createToggleBreakpointAction();
            Action remove = toggle.createContextAwareInstance(Lookups.fixed(annotation(selected), annotation(selected)));
            assertTrue(remove.isEnabled());
            remove.actionPerformed(null);
            assertFalse(Arrays.asList(manager.getBreakpoints()).contains(selected));
            assertTrue(Arrays.asList(manager.getBreakpoints()).contains(unrelated));
            assertSame(toggle, toggle.createContextAwareInstance(Lookup.EMPTY));
        } finally {
            manager.removeBreakpoint(selected);
            manager.removeBreakpoint(unrelated);
        }
    }

    private static Breakpoint breakpoint() {
        return new Breakpoint() {
            public boolean isEnabled() { return true; }
            public void enable() { }
            public void disable() { }
        };
    }

    private static BreakpointAnnotation annotation(Breakpoint breakpoint) {
        return new BreakpointAnnotation() {
            public Breakpoint getBreakpoint() { return breakpoint; }
            public String getAnnotationType() { return "Breakpoint"; }
            public String getShortDescription() { return "Breakpoint"; }
        };
    }
}
