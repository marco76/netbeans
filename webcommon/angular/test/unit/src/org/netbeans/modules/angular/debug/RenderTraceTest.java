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

import java.util.*;
import junit.framework.TestCase;
import org.openide.filesystems.*;

public class RenderTraceTest extends TestCase {
    static String batch() { return """
        {"version":1,"events":[{"time":1000,"instance":"1","component":"Card","source":"src/card.ts","kind":"inputs","detail":"Inputs changed: title","changes":{"title":{"previous":"a","current":"b"}}}]}
        """; }
    public void testInputHistoryAndBoundedRetention() throws Exception {
        var event=RenderTrace.parse(batch()).get(0);
        assertTrue(event.changes().contains("previous"));
        RenderTrace trace=new RenderTrace();
        trace.add(Collections.nCopies(2100,event)); assertEquals(2000,trace.events().size());
        trace.clear(); assertTrue(trace.events().isEmpty());
    }
    public void testInvalidDataIsRejectedAtomically() throws Exception {
        for (String json : List.of("{}",batch().replace("\"version\":1","\"version\":2"),batch().replace("\"inputs\"","\"rendered\""))) {
            try { RenderTrace.parse(json); fail(); } catch (java.io.IOException expected) {}
        }
    }
    public void testSourceCannotEscapeWorkspace() throws Exception {
        FileObject root=FileUtil.createMemoryFileSystem().getRoot();
        FileObject source=FileUtil.createData(root,"src/card.ts");
        assertEquals(source,RenderTrace.source(root,"src/card.ts"));
        for (String path : List.of("../card.ts","/src/card.ts","src/../../card.ts","file:/tmp/card.ts","C:\\tmp\\card.ts")) assertNull(RenderTrace.source(root,path));
    }
}
