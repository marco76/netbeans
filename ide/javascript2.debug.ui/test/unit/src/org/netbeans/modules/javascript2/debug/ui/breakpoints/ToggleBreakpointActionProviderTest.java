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

package org.netbeans.modules.javascript2.debug.ui.breakpoints;

import java.beans.PropertyChangeListener;
import java.net.URL;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.javascript2.debug.ui.TextLineHandler;
import org.openide.text.Line;
import org.openide.util.lookup.Lookups;
import org.netbeans.modules.javascript2.debug.breakpoints.JSLineBreakpoint;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class ToggleBreakpointActionProviderTest extends NbTestCase {
    public ToggleBreakpointActionProviderTest(String name) {
        super(name);
    }

    public void testRemoveAllAtSourceLocation() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject file = root.createData("app.ts");
        FileObject other = root.createData("other.ts");
        JSLineBreakpoint first = breakpoint(file, 16);
        JSLineBreakpoint duplicate = breakpoint(file, 16);
        JSLineBreakpoint nextLine = breakpoint(file, 19);
        JSLineBreakpoint otherFile = breakpoint(other, 16);
        DebuggerManager manager = DebuggerManager.getDebuggerManager();
        JSLineBreakpoint[] breakpoints = {first, duplicate, nextLine, otherFile};
        try {
            for (JSLineBreakpoint breakpoint : breakpoints) {
                manager.addBreakpoint(breakpoint);
            }
            assertTrue(ToggleBreakpointActionProvider.removeBreakpoints(manager, file, 16));
            assertFalse(java.util.Arrays.asList(manager.getBreakpoints()).contains(first));
            assertFalse(java.util.Arrays.asList(manager.getBreakpoints()).contains(duplicate));
            assertTrue(java.util.Arrays.asList(manager.getBreakpoints()).contains(nextLine));
            assertTrue(java.util.Arrays.asList(manager.getBreakpoints()).contains(otherFile));
            assertFalse(ToggleBreakpointActionProvider.removeBreakpoints(manager, file, 16));
        } finally {
            for (JSLineBreakpoint breakpoint : breakpoints) {
                manager.removeBreakpoint(breakpoint);
            }
        }
    }

    private static JSLineBreakpoint breakpoint(FileObject file, int line) {
        Line editorLine = new Line(Lookups.singleton(file)) {
            public int getLineNumber() { return line - 1; }
            public void show(int kind, int column) { }
            public void setBreakpoint(boolean value) { }
            public boolean isBreakpoint() { return false; }
            public void markError() { }
            public void unmarkError() { }
            public void markCurrentLine() { }
            public void unmarkCurrentLine() { }
        };
        return new JSLineBreakpoint(new TextLineHandler() {
            public Line getLine() { return editorLine; }
            public FileObject getFileObject() { return file; }
            public URL getURL() { return file.toURL(); }
            public int getLineNumber() { return line; }
            public void setLineNumber(int number) { }
            public void dispose() { }
            public void addPropertyChangeListener(PropertyChangeListener listener) { }
            public void removePropertyChangeListener(PropertyChangeListener listener) { }
        });
    }
}
