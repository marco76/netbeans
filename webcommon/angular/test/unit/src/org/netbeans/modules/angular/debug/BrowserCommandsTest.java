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

import com.google.gson.*;
import junit.framework.TestCase;

public class BrowserCommandsTest extends TestCase {
    public void testQueueIsBoundedAndCommandsAreConsumedOnce() {
        var commands=new BrowserCommands();
        for (int i=0;i<40;i++) commands.highlight("Card"+i,"app-card");
        JsonArray batch=JsonParser.parseString(commands.drain()).getAsJsonArray();
        assertEquals(32,batch.size()); assertEquals("Card8",batch.get(0).getAsJsonObject().get("component").getAsString());
        assertEquals("[]",commands.drain()); commands.pick(); commands.cancel();
        batch=JsonParser.parseString(commands.drain()).getAsJsonArray();
        assertEquals("pick",batch.get(0).getAsJsonObject().get("type").getAsString()); assertEquals("cancel",batch.get(1).getAsJsonObject().get("type").getAsString());
    }
}
