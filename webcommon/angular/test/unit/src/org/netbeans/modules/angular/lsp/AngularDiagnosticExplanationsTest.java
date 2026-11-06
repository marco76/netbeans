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
package org.netbeans.modules.angular.lsp;

import junit.framework.TestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class AngularDiagnosticExplanationsTest extends TestCase {
    public void testNumericAndNamedCompilerCodes() {
        assertEquals(AngularDiagnosticExplanations.forCode("NG8001"), AngularDiagnosticExplanations.forCode("-998001"));
        assertEquals(AngularDiagnosticExplanations.forCode("NG8002"), AngularDiagnosticExplanations.forCode("-998002"));
        assertEquals(AngularDiagnosticExplanations.forCode("NG8004"), AngularDiagnosticExplanations.forCode("-998004"));
        assertTrue(AngularDiagnosticExplanations.forCode("-998004").guidance().contains("TypeScript import alone"));
        assertTrue(AngularDiagnosticExplanations.forCode("-998002").guidance().contains("misspelled"));
        assertTrue(AngularDiagnosticExplanations.forCode("-998001").documentation().startsWith("https://angular.dev/"));
    }

    public void testMissingMemberExplainsReceiverAndScope() {
        var explanation = AngularDiagnosticExplanations.forCode("2339");
        assertNotNull(explanation);
        assertTrue(explanation.guidance().contains("nested object"));
        assertTrue(explanation.guidance().contains("create-property"));
    }

    public void testUnknownDiagnosticsAreNotGuessed() {
        assertNull(AngularDiagnosticExplanations.forCode("2322"));
        assertNull(AngularDiagnosticExplanations.forCode("8004"));
        assertNull(AngularDiagnosticExplanations.forCode(null));
    }

    public void testAngularWorkspaceIsRequired() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject file = FileUtil.createData(root, "src/card.html");
        AngularDiagnosticExplanations provider = new AngularDiagnosticExplanations();
        assertNull(provider.explain(file, "-998004"));
        FileUtil.createData(root, "angular.json");
        assertNotNull(provider.explain(file, "-998004"));
    }
}
