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
package org.netbeans.modules.angular.debug;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import org.openide.filesystems.FileObject;

/** Bounded trace data; source paths are always resolved inside the workspace. */
public final class RenderTrace {
    public static final int LIMIT=2000;
    public record Event(long time,String instance,String component,String source,String kind,String detail,String changes) {}
    private final Deque<Event> events=new ArrayDeque<>();
    public synchronized void add(List<Event> batch) {
        for (Event event : batch) { events.addLast(event); if (events.size()>LIMIT) events.removeFirst(); }
    }
    public synchronized List<Event> events() { return List.copyOf(events); }
    public synchronized void clear() { events.clear(); }
    public static List<Event> parse(String json) throws IOException {
        try {
            JsonObject root=JsonParser.parseString(json).getAsJsonObject();
            if (root.get("version").getAsInt()!=1) throw new IllegalArgumentException();
            JsonArray array=root.getAsJsonArray("events");
            if (array.size()>LIMIT) throw new IllegalArgumentException();
            List<Event> result=new ArrayList<>();
            for (JsonElement value : array) {
                JsonObject item=value.getAsJsonObject();
                String kind=text(item,"kind",20);
                if (!Set.of("inputs","check","mark","pick").contains(kind)) throw new IllegalArgumentException();
                result.add(new Event(item.get("time").getAsLong(),text(item,"instance",80),text(item,"component",200),
                        text(item,"source",1024),kind,text(item,"detail",2048), item.has("changes") ? item.get("changes").toString() : ""));
            }
            return result;
        } catch (RuntimeException ex) { throw new IOException("Invalid render trace. Expected version 1 lifecycle events (maximum 2000).",ex); }
    }
    private static String text(JsonObject item,String key,int limit) {
        JsonElement value=item.get(key);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        String text=value.getAsString(); if (text.length()>limit) throw new IllegalArgumentException(); return text;
    }
    public static FileObject source(FileObject root,String path) {
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")) return null;
        for (String part : path.split("/")) if (part.equals("..")) return null;
        FileObject file=root.getFileObject(path);
        return file!=null && file.isData() && "ts".equals(file.getExt()) ? file : null;
    }
}
