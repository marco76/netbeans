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

import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Set;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.modules.java.hints.spi.AbstractHint;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.openide.util.NbBundle.Messages;

/** Also detects missing processor setup when Lombok annotations already resolve. */
@Messages({"HINT_LombokProcessing=Lombok annotation processing is not configured",
    "DESC_LombokProcessing=Configure Lombok for Maven projects using Lombok annotations."})
public final class LombokProcessingHint extends AbstractHint {
    public LombokProcessingHint() { super(true, false, HintSeverity.WARNING); }

    @Override public Set<Kind> getTreeKinds() { return Set.of(Kind.ANNOTATION); }

    @Override public List<ErrorDescription> run(CompilationInfo info, TreePath path) {
        var project = FileOwnerQuery.getOwner(info.getFileObject());
        var maven = project == null ? null : project.getLookup().lookup(NbMavenProject.class);
        if (maven == null || !LombokConfiguration.isLombokReference(info.getCompilationUnit(), path)) {
            return List.of();
        }
        // Unresolved annotations already receive the compiler-error quick fix.
        var annotationPath = new TreePath(path, ((com.sun.source.tree.AnnotationTree) path.getLeaf()).getAnnotationType());
        var type = info.getTrees().getTypeMirror(annotationPath);
        var element = info.getTrees().getElement(annotationPath);
        if (!(element instanceof javax.lang.model.element.TypeElement annotationType)
                || !annotationType.getQualifiedName().toString().startsWith("lombok.")
                || type == null || type.getKind() == javax.lang.model.type.TypeKind.ERROR
                || !LombokConfiguration.needsSetup(maven.getMavenProject())) {
            return List.of();
        }
        int start = (int) info.getTrees().getSourcePositions().getStartPosition(info.getCompilationUnit(), path.getLeaf());
        int end = (int) info.getTrees().getSourcePositions().getEndPosition(info.getCompilationUnit(), path.getLeaf());
        if (start < 0 || end < start) {
            return List.of();
        }
        return List.of(ErrorDescriptionFactory.createErrorDescription(getSeverity().toEditorSeverity(),
                Bundle.HINT_LombokProcessing(), List.of(LombokConfiguration.fix(project)), info.getFileObject(), start, end));
    }

    @Override public String getId() { return LombokProcessingHint.class.getName(); }
    @Override public String getDisplayName() { return Bundle.HINT_LombokProcessing(); }
    @Override public String getDescription() { return Bundle.DESC_LombokProcessing(); }
    @Override public void cancel() {}
}
