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

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Ordered checkpoints; current navigation advances only after a successful jump. */
final class DemoCheckpointStore {
    static final int LIMIT = 100;
    private final Preferences prefs;
    final List<DemoCheckpoint> entries = new ArrayList<>();
    int current = -1;

    DemoCheckpointStore(Preferences prefs) {
        this.prefs = prefs;
        int count = Math.max(0, Math.min(LIMIT, prefs.getInt("count", 0)));
        for (int i = 0; i < count; i++) {
            String prefix = i + ".";
            String name = prefs.get(prefix + "name", "");
            String root = prefs.get(prefix + "root", "");
            String path = prefs.get(prefix + "path", "");
            if (!name.isBlank() && !root.isBlank() && !path.isBlank()) {
                entries.add(new DemoCheckpoint(name, root, path, prefs.getInt(prefix + "start", 0),
                        prefs.getInt(prefix + "end", 0)));
                entries.get(entries.size() - 1).notes = prefs.get(prefix + "notes", "");
            }
        }
    }

    int next() { return current + 1 < entries.size() ? current + 1 : -1; }
    int resume() { return current >= 0 && current < entries.size() ? current : next(); }
    int previous() { return current > 0 ? current - 1 : -1; }

    void save() {
        int oldCount = Math.max(0, Math.min(LIMIT, prefs.getInt("count", 0)));
        for (int i = 0; i < entries.size(); i++) {
            DemoCheckpoint entry = entries.get(i);
            String prefix = i + ".";
            prefs.put(prefix + "name", entry.name);
            prefs.put(prefix + "notes", entry.notes);
            prefs.put(prefix + "root", entry.rootUrl);
            prefs.put(prefix + "path", entry.relativePath);
            prefs.putInt(prefix + "start", entry.start());
            prefs.putInt(prefix + "end", entry.end());
        }
        for (int i = entries.size(); i < oldCount; i++) {
            for (String key : new String[]{"name", "notes", "root", "path", "start", "end"}) prefs.remove(i + "." + key);
        }
        prefs.putInt("count", entries.size());
    }
}
