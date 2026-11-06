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
import java.util.ArrayDeque;

/** Bounded, session-local commands. Only explicit IDE actions enqueue browser work. */
public final class BrowserCommands {
    private final ArrayDeque<JsonObject> commands=new ArrayDeque<>();
    public synchronized void pick() { add("pick","",""); }
    public synchronized void highlight(String component,String selector) { add("highlight",component,selector==null ? "" : selector); }
    public synchronized void cancel() { add("cancel","",""); }
    private void add(String type,String component,String selector) {
        JsonObject item=new JsonObject(); item.addProperty("type",type); item.addProperty("component",component); item.addProperty("selector",selector);
        if (commands.size()==32) commands.removeFirst(); commands.addLast(item);
    }
    public synchronized String drain() {
        JsonArray result=new JsonArray(); while (!commands.isEmpty()) result.add(commands.removeFirst()); return result.toString();
    }
}
