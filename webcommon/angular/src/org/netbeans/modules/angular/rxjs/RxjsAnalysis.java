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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Conservative, syntax-only learning aid; never presents findings as compiler errors. */
public final class RxjsAnalysis {
    private RxjsAnalysis() {}
    public record Stage(String name, int offset) {}
    public record Finding(int offset, String message) {
        @Override public String toString() { return message; }
    }
    // Preserve offsets while excluding comments and string literals from pattern matching.
    static String code(String source) {
        StringBuilder out = new StringBuilder(source);
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            int end = i;
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                end = source.indexOf('\n', i + 2); if (end < 0) end = source.length();
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                end = source.indexOf("*/", i + 2); end = end < 0 ? source.length() : end + 2;
            } else if (c == '\'' || c == '"' || c == '`') {
                end = i + 1;
                while (end < source.length()) {
                    char next = source.charAt(end++);
                    if (next == '\\' && end < source.length()) end++;
                    else if (next == c) break;
                }
            }
            if (end > i) {
                for (int j = i; j < end; j++) if (out.charAt(j) != '\n') out.setCharAt(j, ' ');
                i = end;
            } else i++;
        }
        return out.toString();
    }
    public static List<Stage> stages(String source) {
        String code = code(source);
        List<Stage> result = new ArrayList<>();
        var pipes = Pattern.compile("\\.pipe\\s*\\(").matcher(code);
        while (pipes.find()) {
            int depth = 1;
            boolean start = true;
            for (int i = pipes.end(); i < code.length() && depth > 0; i++) {
                char c = code.charAt(i);
                if (depth == 1 && start && Character.isJavaIdentifierStart(c)) {
                    int end = i + 1;
                    while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) end++;
                    String name = code.substring(i, end);
                    if (RxjsOperators.find(name) != null) result.add(new Stage(name, i));
                    start = false;
                }
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (depth == 1 && c == ',') start = true;
            }
        }
        return List.copyOf(result);
    }
    public static List<Finding> inspect(String source) {
        String code = code(source);
        List<Finding> result = new ArrayList<>();
        Pattern subscription = Pattern.compile("\\.subscribe\\s*\\(");
        var subscriptions = subscription.matcher(code);
        while (subscriptions.find()) {
            int depth = 1;
            for (int i = subscriptions.end(); i < code.length() && depth > 0; i++) {
                char c = code.charAt(i);
                if (c == '(') depth++;
                if (c == ')') depth--;
                if (c == '.' && subscription.matcher(code).region(i, code.length()).lookingAt()) {
                    result.add(new Finding(i, "Nested subscribe: compose the streams; choose switchMap, concatMap, mergeMap or exhaustMap based on intent."));
                    break;
                }
            }
        }
        for (Stage stage : stages(source)) {
            if (stage.name().equals("switchMap")) result.add(new Finding(stage.offset(),
                    "switchMap cancels the previous inner subscription. Review this choice for writes; unsubscription cannot undo a server operation."));
            if (stage.name().equals("catchError")) result.add(new Finding(stage.offset(),
                    "Review catchError placement: recovery inside a flattening operator can preserve the outer event stream."));
            if (stage.name().equals("shareReplay")) result.add(new Finding(stage.offset(),
                    "Review shareReplay buffer size, refCount and cache lifetime."));
        }
        if (code.contains(".subscribe") && !code.contains("takeUntilDestroyed") && !code.contains("takeUntil")
                && !code.contains(".unsubscribe") && code.contains("@Component")) {
            result.add(new Finding(code.indexOf(".subscribe"), "Review subscription ownership: long-lived streams may need takeUntilDestroyed, AsyncPipe or explicit cleanup. Finite HTTP streams normally complete."));
        }
        return List.copyOf(result);
    }
    public static String explain(String source) {
        StringBuilder out = new StringBuilder("<h2>Pipeline explanation</h2><p>Static learning view. Operators are recognized by name; aliases and custom operators need semantic analysis. Each pipe is read in source order. Nested pipes are listed separately. Types below describe operator behavior, not inferred TypeScript types.</p>");
        List<Stage> stages = stages(source);
        if (stages.isEmpty()) out.append("<p>No known operators found in a .pipe(...) expression. Select a complete pipeline or analyze the current file.</p>");
        for (Stage stage : stages) {
            var op = RxjsOperators.find(stage.name());
            out.append("<h3>").append(op.name()).append("</h3><p>").append(RxjsOperators.escape(op.behavior()))
                    .append("</p><p><code>").append(RxjsOperators.escape(op.types())).append("</code></p>");
        }
        out.append("<h2>Review suggestions</h2>");
        for (Finding finding : inspect(source)) out.append("<p>• ").append(RxjsOperators.escape(finding.message())).append("</p>");
        return RxjsDocumentation.wrap(out.toString());
    }
    public static String marbleTest() {
        return "import { TestScheduler } from 'rxjs/testing';\nimport { map } from 'rxjs';\n\ndescribe('pipeline', () => {\n  it('transforms emitted values', () => {\n    const scheduler = new TestScheduler((actual, expected) => {\n      expect(actual).toEqual(expected);\n    });\n    scheduler.run(({ cold, expectObservable }) => {\n      const source$ = cold('-a-b-|', { a: 1, b: 2 });\n      // Replace this example operator and expected values with your pipeline.\n      const result$ = source$.pipe(map(value => value * 10));\n      expectObservable(result$).toBe('-a-b-|', { a: 10, b: 20 });\n    });\n  });\n});\n";
    }
}
