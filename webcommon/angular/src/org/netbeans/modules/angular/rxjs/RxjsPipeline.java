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

/** Offset-preserving syntax model: nested pipelines stay independent. */
final class RxjsPipeline {
    record Stage(String name, String expression, int offset) {
        @Override public String toString() {
            var operator = RxjsOperators.find(name);
            return name + "  ·  " + (operator == null ? "Custom / unresolved operator" : operator.category());
        }
    }
    record Pipe(int offset, int end, List<Stage> stages) {
        @Override public String toString() { return "Pipeline at offset " + offset + " · " + stages.size() + " stages"; }
    }
    static List<Pipe> parse(String source) {
        String code = RxjsAnalysis.code(source);
        var matcher = Pattern.compile("\\.pipe\\s*\\(").matcher(code);
        List<Pipe> pipes = new ArrayList<>();
        while (matcher.find()) {
            List<Stage> stages = new ArrayList<>();
            int depth = 1, argument = matcher.end(), end = code.length();
            for (int i = argument; i < code.length(); i++) {
                char c = code.charAt(i);
                if (depth == 1 && (c == ',' || c == ')')) {
                    addStage(source, code, argument, i, stages);
                    argument = i + 1;
                }
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                if (depth == 0) { end = i + 1; break; }
            }
            if (depth != 0) addStage(source, code, argument, code.length(), stages);
            pipes.add(new Pipe(matcher.start(), end, List.copyOf(stages)));
        }
        return List.copyOf(pipes);
    }
    private static void addStage(String source, String code, int start, int end, List<Stage> stages) {
        while (start < end && Character.isWhitespace(code.charAt(start))) start++;
        if (start == end) return;
        int nameEnd = start;
        while (nameEnd < end && (Character.isJavaIdentifierPart(code.charAt(nameEnd)) || code.charAt(nameEnd) == '.')) nameEnd++;
        String name = code.substring(start, nameEnd);
        stages.add(new Stage(name.isEmpty() ? "Expression" : name, source.substring(start, end).stripTrailing(), start));
    }
}
