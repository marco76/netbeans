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
package org.netbeans.modules.maven.hints.errors;

import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Set;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.modules.java.hints.spi.ErrorRule;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.spi.editor.hints.Fix;

/** Offers project setup on unresolved Lombok imports and annotations. */
public final class SetupLombokError implements ErrorRule<Void> {
    @Override public Set<String> getCodes() {
        return Set.of("compiler.err.cant.resolve", "compiler.err.cant.resolve.location", "compiler.err.doesnt.exist");
    }

    @Override public List<Fix> run(CompilationInfo info, String key, int offset, TreePath path, Data<Void> data) {
        var project = FileOwnerQuery.getOwner(info.getFileObject());
        var maven = project == null ? null : project.getLookup().lookup(NbMavenProject.class);
        if (maven == null || !LombokConfiguration.needsSetup(maven.getMavenProject())) {
            return List.of();
        }
        TreePath actual = path != null ? path : info.getTreeUtilities().pathFor(offset + 1);
        return LombokConfiguration.isLombokReference(info.getCompilationUnit(), actual)
                ? List.of(LombokConfiguration.fix(project)) : List.of();
    }

    @Override public String getId() { return SetupLombokError.class.getName(); }
    @Override public String getDisplayName() { return Bundle.FIX_SetupLombok(); }
    @Override public void cancel() {}
}
