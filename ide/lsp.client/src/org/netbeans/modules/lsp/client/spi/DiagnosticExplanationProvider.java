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

/**
 * Optional explanations for language-server diagnostics. Register in MIME lookup.
 * Providers must return null for unsupported files or codes and must not modify source.
 * @since 1.38
 */
public interface DiagnosticExplanationProvider {
    /** Returns curated guidance, or null when this diagnostic is not recognized. */
    Explanation explain(FileObject file, String code);

    /** Cause, inspection guidance, expected result, and an official documentation URL. */
    record Explanation(String cause, String guidance, String result, String documentation) { }
}
