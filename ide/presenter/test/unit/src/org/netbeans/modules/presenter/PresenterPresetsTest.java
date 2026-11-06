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

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import org.netbeans.junit.NbTestCase;

public class PresenterPresetsTest extends NbTestCase {
    public PresenterPresetsTest(String name) { super(name); }

    public void testDefaultsAndIndependentCustomization() {
        Preferences prefs = new MemoryPreferences();
        assertEquals(26, PresenterPresets.load(prefs, PresenterPresets.Preset.LIVE_TALK).editorSize());
        assertEquals(22, PresenterPresets.load(prefs, PresenterPresets.Preset.YOUTUBE).editorSize());
        var custom = new PresenterPresets.Settings(24, 18, PresenterPresets.Layout.DEBUG, false, PresenterPresets.Corner.TOP_LEFT);
        PresenterPresets.save(prefs, PresenterPresets.Preset.YOUTUBE, custom);
        prefs.put("selectedPreset", "youtube");
        assertEquals(PresenterPresets.Preset.YOUTUBE, PresenterPresets.selected(prefs));
        assertEquals(custom, PresenterPresets.load(prefs, PresenterPresets.selected(prefs)));
        assertTrue(PresenterPresets.load(prefs, PresenterPresets.Preset.LIVE_TALK).commandOverlay());
    }
    public void testLegacySettingsAndInvalidStoredValues() throws Exception {
        Preferences prefs = new MemoryPreferences();
        prefs.putInt("fontIncrease", 9);
        assertEquals(23, PresenterPresets.load(prefs, PresenterPresets.Preset.LIVE_TALK).editorSize());
        assertEquals(1, prefs.keys().length);
        prefs.put("selectedPreset", "unknown");
        assertEquals(PresenterPresets.Preset.LIVE_TALK, PresenterPresets.selected(prefs));
        prefs.putInt("liveTalk.editorSize", 999);
        prefs.put("liveTalk.layout", "INVALID");
        assertEquals(48, PresenterPresets.load(prefs, PresenterPresets.Preset.LIVE_TALK).editorSize());
        assertEquals(PresenterPresets.Layout.EXPLAIN, PresenterPresets.load(prefs, PresenterPresets.Preset.LIVE_TALK).layout());
    }
    public void testDemoAndDebugLayoutsKeepEssentialPanels() {
        assertFalse(PresenterPresets.Layout.EXPLAIN.keep("output"));
        assertTrue(PresenterPresets.Layout.RUN.keep("output"));
        assertTrue(PresenterPresets.Layout.RUN.keep("TerminalContainerTopComponent"));
        assertFalse(PresenterPresets.Layout.RUN.keep("localsView"));
        assertTrue(PresenterPresets.Layout.DEBUG.keep("localsView"));
        assertTrue(PresenterPresets.Layout.DEBUG.keep("CallStackView"));
        assertTrue(PresenterPresets.Layout.DEBUG.keep("output"));
        assertFalse(PresenterPresets.Layout.DEBUG.keep("projectTabLogical_tc"));
    }

    static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        MemoryPreferences() { super(null, ""); }
        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { values.clear(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }
}
