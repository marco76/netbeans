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
package org.netbeans.modules.angular.signals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Conservative local inspections. Passing a signal as a value is legal and is not flagged. */
public final class SignalsAnalysis {
    private SignalsAnalysis() {}
    public record Finding(int start, int end, String message, boolean readFix) {}
    private static final Pattern MASK = Pattern.compile("//[^\\r\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`|/(?:\\\\.|[^/\\\\\\r\\n])+/[dgimsuvy]*", Pattern.DOTALL);

    static String code(String source) {
        StringBuilder result = new StringBuilder(source);
        var matcher = MASK.matcher(source);
        while (matcher.find()) {
            for (int i = matcher.start(); i < matcher.end(); i++) {
                if (result.charAt(i) != '\n' && result.charAt(i) != '\r') result.setCharAt(i, ' ');
            }
        }
        return result.toString();
    }

    public static List<Finding> inspect(String source) {
        String code = code(source);
        // Avoid resolving this.field across multiple classes or nested scopes without a type checker.
        if (Pattern.compile("\\bclass\\b").matcher(code).results().count() != 1
                || Pattern.compile("\\bfunction\\b").matcher(code).find()) return List.of();
        Map<String, String> factories = new HashMap<>();
        var imports = Pattern.compile("import\\s*\\{([^}]+)\\}\\s*from\\s*['\"]@angular/core['\"]").matcher(source);
        while (imports.find()) {
            if (!code.substring(imports.start(), imports.start() + 6).equals("import")) continue;
            for (String binding : imports.group(1).split(",")) {
                String[] parts = binding.trim().split("\\s+as\\s+");
                if (List.of("signal", "computed", "effect").contains(parts[0])) {
                    factories.put(parts.length == 2 ? parts[1].trim() : parts[0], parts[0]);
                }
            }
        }
        Map<String, Boolean> fields = new HashMap<>();
        var declarations = Pattern.compile("(?m)^\\s*(?:(?:public|private|protected|readonly)\\s+)*([\\w$]+)\\s*=\\s*([\\w$]+)\\s*(?:<[^;=]+>)?\\s*\\(").matcher(code);
        while (declarations.find()) {
            int depth = 0;
            for (int i = 0; i < declarations.start(1); i++) {
                if (code.charAt(i) == '{') depth++;
                if (code.charAt(i) == '}') depth--;
            }
            if (depth != 1) continue;
            String factory = factories.get(declarations.group(2));
            if ("signal".equals(factory) || "computed".equals(factory)) {
                fields.put(declarations.group(1), "signal".equals(factory));
            }
        }
        List<Finding> result = new ArrayList<>();
        var reads = Pattern.compile("\\bthis\\.([\\w$]+)\\b").matcher(code);
        while (reads.find()) {
            Boolean writable = fields.get(reads.group(1));
            if (writable == null) continue;
            String tail = code.substring(reads.end());
            if (!writable && tail.matches("(?s)^\\s*\\.\\s*(set|update)\\s*\\(.*")) {
                result.add(new Finding(reads.start(), reads.end(),
                        "Computed signals are read only. Update their source signals; Angular recalculates the derived value.", false));
            }
            // A bare signal in a condition always tests the function object, rather than its value.
            String before = code.substring(0, reads.start());
            if (before.matches("(?s).*\\b(?:if|while)\\s*\\(\\s*!?\\s*")
                    && tail.matches("(?s)^\\s*\\).*")) {
                result.add(new Finding(reads.start(), reads.end(),
                        "This condition tests the signal function. Call it with () to test its current value.", true));
            }
        }
        for (var factory : factories.entrySet()) {
            if (!factory.getValue().equals("effect")) continue;
            var effects = Pattern.compile("\\b" + Pattern.quote(factory.getKey())
                    + "\\s*\\(\\s*\\(\\s*\\)\\s*=>\\s*(?:\\{\\s*)?this\\.([\\w$]+)\\.set\\s*\\(\\s*this\\.([\\w$]+)\\s*\\(\\s*\\)").matcher(code);
            while (effects.find()) {
                if (Boolean.TRUE.equals(fields.get(effects.group(1))) && fields.containsKey(effects.group(2))) {
                    result.add(new Finding(effects.start(), effects.end(),
                            "This effect copies signal state. Consider computed(() => source()) for derived state. Review other writes and side effects before converting.", false));
                }
            }
        }
        return List.copyOf(result);
    }
}
