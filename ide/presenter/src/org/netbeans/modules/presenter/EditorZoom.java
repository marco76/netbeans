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
package org.netbeans.modules.presenter;

import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.text.JTextComponent;

/** Keeps original per-editor zoom, including an unset value. Used on the EDT. */
final class EditorZoom {
    private static final String ZOOM = "text-zoom";
    private final Map<JTextComponent, Object> originals = new WeakHashMap<>();

    void apply(JTextComponent editor, int increase) {
        if (originals.containsKey(editor)) {
            return;
        }
        originals.put(editor, editor.getClientProperty(ZOOM));
        Object original = originals.get(editor);
        int base = original instanceof Integer ? (Integer) original : 0;
        editor.putClientProperty(ZOOM, base + increase);
    }

    private final Map<JTextComponent, Integer> targets = new WeakHashMap<>();

    void target(JTextComponent editor, int size, int baseSize) {
        if (!originals.containsKey(editor)) originals.put(editor, editor.getClientProperty(ZOOM));
        if (!Integer.valueOf(size).equals(targets.put(editor, size))) {
            editor.putClientProperty(ZOOM, size - baseSize);
        }
    }

    void restore() {
        originals.forEach((editor, original) -> editor.putClientProperty(ZOOM, original));
        originals.clear();
        targets.clear();
    }
}
