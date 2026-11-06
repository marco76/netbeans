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
package org.netbeans.modules.angular.ui;

import junit.framework.TestCase;
import org.netbeans.modules.angular.model.*;
import org.openide.filesystems.*;

public class AngularComponentPickerTest extends TestCase {
    public void testDuplicateNamesRequireSelectorDisambiguation() throws Exception {
        var root=FileUtil.createMemoryFileSystem().getRoot();
        var one=AngularComponentScanner.parseTypeScriptContent(root.createData("one.ts"),"@Component({selector: 'app-one'}) export class Card {}").get(0);
        var two=AngularComponentScanner.parseTypeScriptContent(root.createData("two.ts"),"@Component({selector: 'app-two'}) export class Card {}").get(0);
        var artifacts=java.util.List.of(one,two);
        assertEquals(java.util.List.of(two),AngularComponentPicker.matches(artifacts,"Card","{\"selector\":\"app-two\"}"));
        assertEquals(2,AngularComponentPicker.matches(artifacts,"Card","{}").size());
        assertTrue(AngularComponentPicker.matches(artifacts,"LibraryComponent","{}").isEmpty());
    }
}
