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

import java.util.regex.Pattern;

/** Exact generated snippets are removable; user-authored taps are preserved. */
final class RxjsLogging {
    static final String ALIAS = "__alpRxTap";
    static final String IMPORT = "import { tap as " + ALIAS + " } from 'rxjs';\n";
    static String insert(String source, int caret) {
        RxjsPipeline.Stage chosen = null;
        for (var pipe : RxjsPipeline.parse(source)) {
            if (pipe.end() > source.length() || source.charAt(pipe.end() - 1) != ')') continue;
            for (var stage : pipe.stages()) {
                if (caret >= stage.offset() && caret <= stage.offset() + stage.expression().length()
                        && (chosen == null || stage.offset() >= chosen.offset())) chosen = stage;
            }
        }
        if (chosen == null) throw new IllegalArgumentException("Place the caret inside a complete pipeline stage.");
        String withoutImport = source.replace(IMPORT, "");
        if (!source.contains(IMPORT) && RxjsAnalysis.code(withoutImport).contains(ALIAS))
            throw new IllegalArgumentException("The generated logging identifier is already in use.");
        int end = chosen.offset() + chosen.expression().length();
        String code = RxjsAnalysis.code(source);
        while (end > chosen.offset() && Character.isWhitespace(code.charAt(end - 1))) end--;
        String snippet = ", /* AlpLibre RxJS log */ " + ALIAS
                + "(value => console.debug('[RxJS stage @ " + chosen.offset() + "]', value))";
        String result = source.substring(0, end) + snippet + source.substring(end);
        if (!source.contains(IMPORT)) {
            int at = source.startsWith("#!") ? source.indexOf('\n') + 1 : 0;
            result = result.substring(0, at) + IMPORT + result.substring(at);
        }
        return result;
    }
    static String remove(String source) {
        Pattern generated = Pattern.compile(", /\\* AlpLibre RxJS log \\*/ " + ALIAS
                + "\\(value => console\\.debug\\('\\[RxJS stage @ [0-9]+\\]', value\\)\\)");
        var matches = generated.matcher(source);
        String code = RxjsAnalysis.code(source);
        StringBuilder result = new StringBuilder(source);
        java.util.List<int[]> ranges = new java.util.ArrayList<>();
        while (matches.find()) {
            int alias = source.indexOf(ALIAS, matches.start());
            if (code.startsWith(ALIAS, alias)) ranges.add(new int[]{matches.start(), matches.end()});
        }
        for (int i = ranges.size() - 1; i >= 0; i--) result.delete(ranges.get(i)[0], ranges.get(i)[1]);
        String cleaned = result.toString();
        String withoutImport = cleaned.replace(IMPORT, "");
        return RxjsAnalysis.code(withoutImport).contains(ALIAS) ? cleaned : withoutImport;
    }
}
