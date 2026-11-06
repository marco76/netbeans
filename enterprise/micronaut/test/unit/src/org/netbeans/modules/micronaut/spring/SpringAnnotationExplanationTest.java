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
package org.netbeans.modules.micronaut.spring;

import junit.framework.TestCase;

public class SpringAnnotationExplanationTest extends TestCase {
    public void testUnrelatedAnnotationsAreNotClaimed() {
        assertNull(SpringAnnotationExplanation.recognized("Service", false));
        assertNull(SpringAnnotationExplanation.recognized("example.Service", true));
        assertNull(SpringAnnotationExplanation.recognized("Unknown", true));
    }

    public void testSpringContextAndQualifiedAnnotation() {
        assertEquals("org.springframework.stereotype.Service", SpringAnnotationExplanation.recognized("Service", true));
        assertEquals("org.springframework.stereotype.Service", SpringAnnotationExplanation.recognized("org.springframework.stereotype.Service", false));
        assertEquals("org.springframework.boot.autoconfigure.SpringBootApplication", SpringAnnotationExplanation.recognized("SpringBootApplication", true));
    }

    public void testImportAndDependencyGuidanceDiffer() {
        String type = "org.springframework.stereotype.Service";
        String available = SpringAnnotationExplanation.details(type, true);
        String absent = SpringAnnotationExplanation.details(type, false);
        assertTrue(available.contains(type));
        assertTrue(available.contains("import quick fix"));
        assertFalse(available.contains("not currently resolvable"));
        assertTrue(absent.contains("not currently resolvable"));
        assertTrue(absent.contains("No dependency or version is selected automatically"));
    }
}
