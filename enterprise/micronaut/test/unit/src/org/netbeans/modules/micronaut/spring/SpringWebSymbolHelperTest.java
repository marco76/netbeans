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

import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import junit.framework.TestCase;

public class SpringWebSymbolHelperTest extends TestCase {

    public void testRequestMappingMethods() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("Tests require a JDK", compiler);
        SimpleJavaFileObject source = new SimpleJavaFileObject(URI.create("string:///org/springframework/web/bind/annotation/Example.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "package org.springframework.web.bind.annotation;"
                        + "enum RequestMethod { GET, POST, HEAD, OPTIONS }"
                        + "@interface RequestMapping { RequestMethod[] method() default {}; }"
                        + "@interface GetMapping {}"
                        + "class Example {"
                        + "@RequestMapping(method=RequestMethod.GET) void get() {}"
                        + "@RequestMapping(method={RequestMethod.POST, RequestMethod.GET}) void multiple() {}"
                        + "@RequestMapping void any() {}"
                        + "@RequestMapping(method={}) void empty() {}"
                        + "@RequestMapping(method={RequestMethod.HEAD, RequestMethod.OPTIONS}) void other() {}"
                        + "@GetMapping void shorthand() {}"
                        + "}";
            }
        };
        try (javax.tools.StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null, List.of(source));
            task.parse();
            task.analyze();
            javax.lang.model.element.TypeElement type = task.getElements().getTypeElement("org.springframework.web.bind.annotation.Example");
            java.util.Map<String, String> expected = java.util.Map.of("get", "GET", "multiple", "POST, GET", "any", "ANY", "empty", "ANY", "other", "HEAD, OPTIONS", "shorthand", "GET");
            int checked = 0;
            for (Element element : type.getEnclosedElements()) {
                String result = expected.get(element.getSimpleName().toString());
                if (result != null) {
                    AnnotationMirror annotation = element.getAnnotationMirrors().get(0);
                    assertEquals(result, SpringWebSymbolHelper.getEndpointMethod(annotation));
                    checked++;
                }
            }
            assertEquals(expected.size(), checked);
        }
    }
}
