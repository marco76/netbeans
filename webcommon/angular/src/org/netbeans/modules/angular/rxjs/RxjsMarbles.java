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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, synchronous teaching model; generated tests use real RxJS TestScheduler. */
final class RxjsMarbles {
    static final List<String> OPERATORS = List.of("map × 10", "filter even values", "take(2)", "distinctUntilChanged");
    record Event(int frame, char symbol, String label) {}
    record Result(String input, String output, List<Event> source, List<Event> result,
            Map<Character, Integer> values, Map<Character, Integer> outputValues, String expression) {
        String test() {
            String operator = expression.substring(0, expression.indexOf('('));
            return "import { TestScheduler } from 'rxjs/testing';\nimport { " + operator + " } from 'rxjs';\n\n"
                    + "describe('" + operator + " pipeline', () => {\n  it('matches the expected timeline', () => {\n"
                    + "    const scheduler = new TestScheduler((actual, expected) => {\n      expect(actual).toEqual(expected);\n    });\n"
                    + "    scheduler.run(({ cold, expectObservable }) => {\n"
                    + "      const source$ = cold('" + input + "', " + literal(values) + ");\n"
                    + "      const result$ = source$.pipe(" + expression + ");\n"
                    + "      expectObservable(result$).toBe('" + output + "', " + literal(outputValues) + ");\n"
                    + "    });\n  });\n});\n";
        }
    }
    static Result calculate(String input, int operator) {
        if (operator < 0 || operator >= OPERATORS.size()) throw new IllegalArgumentException("Choose an operator.");
        if (input.length() > 80 || !input.matches("[-a-z]*[|#]"))
            throw new IllegalArgumentException("Use up to 80 characters: - for a frame, a–z for values, and one final | or #. Groups and time syntax are not supported.");
        List<Event> source = new ArrayList<>(), result = new ArrayList<>();
        Map<Character, Integer> values = new LinkedHashMap<>(), outputValues = new LinkedHashMap<>();
        StringBuilder output = new StringBuilder();
        char previous = 0;
        int count = 0;
        boolean ended = false;
        for (int frame = 0; frame < input.length(); frame++) {
            char c = input.charAt(frame);
            if (c >= 'a' && c <= 'z') {
                int value = c - 'a' + 1;
                values.put(c, value);
                source.add(new Event(frame, c, Integer.toString(value)));
                if (ended) continue;
                boolean emit = operator != 1 || value % 2 == 0;
                if (operator == 3 && c == previous) emit = false;
                previous = c;
                if (emit) {
                    int transformed = operator == 0 ? value * 10 : value;
                    result.add(new Event(frame, c, Integer.toString(transformed)));
                    outputValues.put(c, transformed);
                    if (operator == 2 && ++count == 2) {
                        output.append('(').append(c).append("|)");
                        result.add(new Event(frame, '|', "complete")); ended = true;
                    } else output.append(c);
                } else output.append('-');
            } else {
                if (c != '-') source.add(new Event(frame, c, c == '|' ? "complete" : "error"));
                if (!ended) {
                    output.append(c);
                    if (c != '-') result.add(new Event(frame, c, c == '|' ? "complete" : "error"));
                }
            }
        }
        String expression = switch (operator) {
            case 0 -> "map(value => value * 10)";
            case 1 -> "filter(value => value % 2 === 0)";
            case 2 -> "take(2)";
            default -> "distinctUntilChanged()";
        };
        return new Result(input, output.toString(), List.copyOf(source), List.copyOf(result), values, outputValues, expression);
    }
    private static String literal(Map<Character, Integer> values) {
        return values.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(java.util.stream.Collectors.joining(", ", "{ ", " }"));
    }
}
