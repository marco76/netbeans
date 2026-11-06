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

import java.util.Locale;
import java.util.prefs.Preferences;

final class PresenterPresets {
    enum Layout {
        EXPLAIN, RUN, DEBUG;
        boolean keep(String id) {
            String value = id.toLowerCase(Locale.ROOT);
            boolean output = value.contains("output") || value.contains("terminal");
            boolean debug = value.contains("debug") || value.contains("variables") || value.contains("locals")
                    || value.contains("callstack") || value.contains("watches") || value.contains("breakpoint")
                    || value.contains("threads") || value.contains("sessions");
            return this == RUN ? output : this == DEBUG && (output || debug);
        }
    }
    enum Corner { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT }
    enum Preset {
        LIVE_TALK("liveTalk", new Settings(26, 22, Layout.EXPLAIN, true, Corner.BOTTOM_RIGHT)),
        YOUTUBE("youtube", new Settings(22, 20, Layout.RUN, true, Corner.BOTTOM_LEFT));
        final String key;
        final Settings defaults;
        Preset(String key, Settings defaults) { this.key = key; this.defaults = defaults; }
    }
    record Settings(int editorSize, int outputSize, Layout layout, boolean commandOverlay, Corner corner) {
        Settings {
            editorSize = Math.max(14, Math.min(48, editorSize));
            outputSize = Math.max(12, Math.min(40, outputSize));
        }
        Settings resize(int delta) { return new Settings(editorSize + delta, outputSize, layout, commandOverlay, corner); }
    }
    static Preset selected(Preferences prefs) {
        for (Preset preset : Preset.values()) if (preset.key.equals(prefs.get("selectedPreset", "liveTalk"))) return preset;
        return Preset.LIVE_TALK;
    }
    static Settings load(Preferences prefs, Preset preset) {
        Settings defaults = preset.defaults;
        String p = preset.key + ".";
        int legacy = prefs.getInt(p + "fontIncrease", preset == Preset.LIVE_TALK ? prefs.getInt("fontIncrease", -1) : -1);
        return new Settings(prefs.getInt(p + "editorSize", legacy < 0 ? defaults.editorSize() : 14 + legacy),
                prefs.getInt(p + "outputSize", defaults.outputSize()),
                enumValue(Layout.class, prefs.get(p + "layout", ""), defaults.layout()),
                prefs.getBoolean(p + "commandOverlay", defaults.commandOverlay()),
                enumValue(Corner.class, prefs.get(p + "corner", ""), defaults.corner()));
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException ex) { return fallback; }
    }
    static void save(Preferences prefs, Preset preset, Settings s) {
        String p = preset.key + ".";
        prefs.putInt(p + "editorSize", s.editorSize());
        prefs.putInt(p + "outputSize", s.outputSize());
        prefs.put(p + "layout", s.layout().name());
        prefs.putBoolean(p + "commandOverlay", s.commandOverlay());
        prefs.put(p + "corner", s.corner().name());
    }
}
