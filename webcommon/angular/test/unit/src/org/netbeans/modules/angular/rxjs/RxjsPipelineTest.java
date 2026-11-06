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
package org.netbeans.modules.angular.rxjs;

import junit.framework.TestCase;

public class RxjsPipelineTest extends TestCase {
    public void testNestedPipelinesStaySeparateAndPreserveOffsets() {
        String source = "events$.pipe(switchMap(v => inner$.pipe(map(x => x))), customOp({a: [1, 2]}), tap(log))";
        var pipes = RxjsPipeline.parse(source);
        assertEquals(2, pipes.size());
        assertEquals(3, pipes.get(0).stages().size());
        assertEquals("customOp", pipes.get(0).stages().get(1).name());
        assertEquals("map", pipes.get(1).stages().get(0).name());
        assertEquals(source.indexOf("customOp"), pipes.get(0).stages().get(1).offset());
    }
    public void testCommentsStringsAndUnfinishedPipeline() {
        String source = "// fake.pipe(map())\nconst text = 'fake.pipe(tap())'; real$.pipe(/* note */ filter(x => x !== ','), map(x => x";
        var pipes = RxjsPipeline.parse(source);
        assertEquals(1, pipes.size());
        assertEquals(2, pipes.get(0).stages().size());
        assertEquals("filter", pipes.get(0).stages().get(0).name());
        assertEquals("map", pipes.get(0).stages().get(1).name());
        assertEquals(source.length(), pipes.get(0).end());
    }
}
