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

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;

/** A project-relative file reference and an optional selection. */
final class DemoCheckpoint {
    String name;
    String notes = "";
    final String rootUrl;
    final String relativePath;
    private int start;
    private int end;
    private WeakReference<Document> document = new WeakReference<>(null);
    private Position liveStart;
    private Position liveEnd;

    DemoCheckpoint(String name, String rootUrl, String relativePath, int start, int end) {
        this.name = name;
        this.rootUrl = rootUrl;
        this.relativePath = relativePath;
        this.start = Math.max(0, start);
        this.end = Math.max(this.start, end);
    }

    DemoCheckpoint copy() {
        DemoCheckpoint copy = new DemoCheckpoint(name, rootUrl, relativePath, start(), end());
        copy.notes = notes;
        copy.document = document;
        copy.liveStart = liveStart;
        copy.liveEnd = liveEnd;
        return copy;
    }

    int start() { return document.get() != null && liveStart != null ? liveStart.getOffset() : start; }
    int end() { return document.get() != null && liveEnd != null ? liveEnd.getOffset() : end; }

    void track(Document doc) throws BadLocationException {
        start = Math.min(start(), doc.getLength());
        end = Math.min(Math.max(start, end()), doc.getLength());
        liveStart = doc.createPosition(start);
        liveEnd = doc.createPosition(end);
        document = new WeakReference<>(doc);
    }

    FileObject resolve() {
        try {
            FileObject root = URLMapper.findFileObject(new URL(rootUrl));
            return root == null ? null : root.getFileObject(relativePath);
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    @Override public String toString() { return name; }
}
