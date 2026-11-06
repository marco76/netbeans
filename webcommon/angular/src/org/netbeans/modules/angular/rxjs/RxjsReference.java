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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Loads the bundled, offline editorial content once; no network access. */
record RxjsReference(List<RxjsOperators.Operator> operators, Map<String, String> parameters,
        Map<String, Marble> marbles, Set<String> mappingOperators, String mappingHtml) {
    record Marble(String source, String result, String expression) {}

    static RxjsReference load() {
        try (var input = RxjsReference.class.getResourceAsStream("reference/operators.xml");
             var comparison = RxjsReference.class.getResourceAsStream("reference/mapping-comparison.html")) {
            if (input == null || comparison == null) throw new IOException("Missing RxJS reference resources");
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder().parse(input).getDocumentElement();
            if (!root.getTagName().equals("rxjs-reference") || !root.getAttribute("schema-version").equals("1"))
                throw new IOException("Unsupported RxJS reference schema");
            List<RxjsOperators.Operator> operators = new ArrayList<>();
            Map<String, String> parameters = new HashMap<>();
            Map<String, Marble> marbles = new HashMap<>();
            Set<String> names = new HashSet<>(), mapping = new HashSet<>();
            var entries = root.getElementsByTagName("operator");
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String name = entry.getAttribute("name");
                if (!name.matches("[A-Za-z][A-Za-z0-9]*") || !names.add(name))
                    throw new IOException("Invalid or duplicate operator: " + name);
                String url = required(entry, "url");
                var uri = java.net.URI.create(url);
                if (!"https".equals(uri.getScheme()) || !Set.of("rxjs.dev", "angular.dev").contains(uri.getHost()))
                    throw new IOException("Unexpected documentation URL: " + url);
                operators.add(new RxjsOperators.Operator(name, required(entry, "category"), required(entry, "summary"),
                        required(entry, "behavior"), required(entry, "types"), required(entry, "example"), required(entry, "caution"), url));
                String params = optional(entry, "parameters");
                if (!params.isBlank()) parameters.put(name, params);
                var marble = entry.getElementsByTagName("marble");
                if (marble.getLength() > 0) {
                    Element example = (Element) marble.item(0);
                    marbles.put(name, new Marble(required(example, "source"), required(example, "result"), required(example, "expression")));
                }
                String group = optional(entry, "comparison");
                if (group.equals("mapping")) mapping.add(name);
                else if (!group.isBlank()) throw new IOException("Unknown comparison: " + group);
            }
            if (operators.isEmpty()) throw new IOException("Empty RxJS reference");
            return new RxjsReference(List.copyOf(operators), Map.copyOf(parameters), Map.copyOf(marbles),
                    Set.copyOf(mapping), new String(comparison.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load bundled RxJS reference", ex);
        }
    }
    private static String optional(Element entry, String field) {
        var children = entry.getElementsByTagName(field);
        return children.getLength() == 0 ? "" : children.item(0).getTextContent();
    }
    private static String required(Element entry, String field) throws IOException {
        String value = optional(entry, field);
        if (value.isBlank()) throw new IOException("Missing " + field + " in " + entry.getAttribute("name"));
        return value;
    }
}
