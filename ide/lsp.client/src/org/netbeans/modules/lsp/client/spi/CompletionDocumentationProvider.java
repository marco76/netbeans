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
package org.netbeans.modules.lsp.client.spi;

import org.openide.filesystems.FileObject;

/** Optional MIME-specific completion reference, independent of the language server. */
public interface CompletionDocumentationProvider {
    /** Optional plain-text completion badge; null preserves the server label. */
    default String completionDescription(FileObject file, String label, String detail) {
        return null;
    }

    /** Origin is the server's label-details description, commonly an auto-import module. */
    default String completionDescription(FileObject file, String label, String detail, String origin) {
        return completionDescription(file, label, detail);
    }

    default String documentation(FileObject file, String label, String detail, String origin) {
        return documentation(file, label, detail);
    }

    /** Return complete Swing HTML, or null to retain the server documentation. */
    String documentation(FileObject file, String label, String detail);
}
