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

import java.util.List;

/** Shared offline reference for editor documentation and the learning window. */
public final class RxjsOperators {
    private RxjsOperators() {}
    public record Operator(String name, String category, String summary, String behavior,
            String types, String example, String caution, String url) {
        @Override public String toString() { return name + "  ·  " + category; }
        public String html() {
            return RxjsDocumentation.wrap("<p class='category'>" + escape(category) + " · RxJS reference</p><h2>" + escape(name) + "</h2><p>" + escape(summary)
                    + "</p>" + mappingComparison(name) + "<h3>Behavior</h3><p>" + escape(behavior)
                    + "</p><h3>Value flow (conceptual)</h3><p class='flow'><code>" + escape(types)
                    + "</code></p>" + parameters(name) + marblePreview(name) + "<h3>Example</h3><pre>" + escape(example)
                    + "</pre><h3>Watch for</h3><p class='caution'>" + escape(caution)
                    + "</p><p><a href='" + url + "'>Official documentation ↗</a></p>");
        }
    }
    private static final RxjsReference REFERENCE = RxjsReference.load();

    static String parameters(String name) {
        String text = REFERENCE.parameters().getOrDefault(name, "");
        return text.isEmpty() ? "" : "<h3>Parameters</h3><p>" + escape(text) + "</p>";
    }

    static String mappingComparison(String name) {
        if (!REFERENCE.mappingOperators().contains(name)) return "";
        String html = REFERENCE.mappingHtml();
        var placeholders = java.util.regex.Pattern.compile("\\{\\{([^}]+)\\}\\}").matcher(html);
        return placeholders.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(coloredTimeline(match.group(1))));
    }

    static String marblePreview(String name) {
        var marble = REFERENCE.marbles().get(name);
        if (marble == null) return "";
        return "<h3>Marble preview</h3><p><code>" + escape(marble.expression())
                + "</code></p><pre>Source  " + coloredTimeline(marble.source())
                + "\nResult  " + coloredTimeline(marble.result())
                + "</pre><p>Each dash is one frame; | means completion; parentheses group simultaneous notifications.</p>";
    }

    private static String coloredTimeline(String timeline) {
        StringBuilder html = new StringBuilder();
        for (char c : timeline.toCharArray()) {
            if (Character.isLetter(c)) {
                html.append("<font color='").append(RxjsDocumentation.color(
                        RxjsDocumentation.eventColor(Character.toLowerCase(c) - 'a')))
                        .append("'><b>").append(c).append("</b></font>");
            } else html.append(c);
        }
        return html.toString();
    }

    public static final List<Operator> ALL = REFERENCE.operators();
    public static Operator find(String name) {
        return ALL.stream().filter(op -> op.name().equals(name)).findFirst().orElse(null);
    }
    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
