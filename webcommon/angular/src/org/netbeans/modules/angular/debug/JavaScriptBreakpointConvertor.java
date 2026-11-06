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

import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.modules.javascript2.debug.breakpoints.JSLineBreakpoint;
import org.netbeans.modules.lsp.client.debugger.spi.BreakpointConvertor;
import org.openide.util.lookup.ServiceProvider;

/** Sends editor JavaScript and TypeScript breakpoints to the Firefox DAP adapter. */
@ServiceProvider(service=BreakpointConvertor.class)
public final class JavaScriptBreakpointConvertor implements BreakpointConvertor {
    @Override
    public void convert(Breakpoint breakpoint, ConvertedBreakpointConsumer consumer) {
        if (breakpoint instanceof JSLineBreakpoint line && line.getFileObject() != null) {
            consumer.lineBreakpoint(line.getFileObject().toURI(), line.getLineNumber(), line.getCondition());
        }
    }
}
