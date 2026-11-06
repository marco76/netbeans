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

import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.netbeans.modules.javascript2.debug.EditorLineHandler;
import org.netbeans.modules.javascript2.debug.breakpoints.JSLineBreakpoint;
import org.netbeans.modules.lsp.client.debugger.spi.BreakpointConvertor;
import org.netbeans.modules.lsp.client.debugger.spi.BreakpointConvertor.ConvertedBreakpointConsumer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

public class JavaScriptBreakpointConvertorTest extends TestCase {
    public void testEditorTypeScriptBreakpointReachesDAP() throws Exception {
        FileObject file=FileUtil.createMemoryFileSystem().getRoot().createData("app.ts");
        JSLineBreakpoint breakpoint=new JSLineBreakpoint(new EditorLineHandler() {
            private int line=21;
            public FileObject getFileObject() { return file; }
            public URL getURL() { return file.toURL(); }
            public int getLineNumber() { return line; }
            public void setLineNumber(int value) { line=value; }
            public void dispose() {}
            public void addPropertyChangeListener(PropertyChangeListener listener) {}
            public void removePropertyChangeListener(PropertyChangeListener listener) {}
        });
        breakpoint.setCondition("data != null");
        List<Object> converted=new ArrayList<>();
        // The public SPI deliberately hides consumer construction; exercise the
        // same consumer used by DAPDebugger without linking to internal classes.
        var constructor=ConvertedBreakpointConsumer.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var consumer=constructor.newInstance(converted);
        var convertor=Lookup.getDefault().lookupAll(BreakpointConvertor.class).stream()
                .filter(c -> c instanceof JavaScriptBreakpointConvertor).findFirst();
        assertTrue("Breakpoint bridge must be registered globally",convertor.isPresent());
        convertor.get().convert(breakpoint,consumer);
        assertEquals(1,converted.size());
        Object line=converted.get(0);
        assertEquals(file.toURI(),line.getClass().getMethod("uri").invoke(line));
        assertEquals(21,line.getClass().getMethod("lineNumber").invoke(line));
        assertEquals("data != null",line.getClass().getMethod("condition").invoke(line));
    }
}
