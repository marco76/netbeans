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
package org.netbeans.modules.micronaut.spring;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.awt.BorderLayout;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.modules.java.hints.spi.ErrorRule;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.HtmlBrowser;
import org.openide.util.NbBundle;

/** Adds inspection guidance to recognized unresolved Spring annotations. */
public final class SpringAnnotationExplanation implements ErrorRule<Void> {
    private static final Map<String, String> ANNOTATIONS = new LinkedHashMap<>();
    static {
        ANNOTATIONS.put("SpringBootApplication", "org.springframework.boot.autoconfigure.SpringBootApplication");
        ANNOTATIONS.put("Component", "org.springframework.stereotype.Component");
        ANNOTATIONS.put("Service", "org.springframework.stereotype.Service");
        ANNOTATIONS.put("Repository", "org.springframework.stereotype.Repository");
        ANNOTATIONS.put("Controller", "org.springframework.stereotype.Controller");
        ANNOTATIONS.put("RestController", "org.springframework.web.bind.annotation.RestController");
        ANNOTATIONS.put("Autowired", "org.springframework.beans.factory.annotation.Autowired");
        ANNOTATIONS.put("Configuration", "org.springframework.context.annotation.Configuration");
        ANNOTATIONS.put("Bean", "org.springframework.context.annotation.Bean");
    }

    static String recognized(String name, boolean springContext) {
        if (ANNOTATIONS.containsValue(name)) return name;
        return springContext ? ANNOTATIONS.get(name) : null;
    }

    @Override public Set<String> getCodes() {
        return new HashSet<>(Arrays.asList("compiler.err.cant.resolve", "compiler.err.cant.resolve.location",
                "compiler.err.doesnt.exist"));
    }

    @Override public List<Fix> run(CompilationInfo info, String key, int offset, TreePath path, Data<Void> data) {
        while (path != null && path.getLeaf().getKind() != Tree.Kind.ANNOTATION) path = path.getParentPath();
        if (path == null) return Collections.emptyList();
        AnnotationTree annotation = (AnnotationTree) path.getLeaf();
        long start = info.getTrees().getSourcePositions().getStartPosition(info.getCompilationUnit(), annotation.getAnnotationType());
        long end = info.getTrees().getSourcePositions().getEndPosition(info.getCompilationUnit(), annotation.getAnnotationType());
        if (offset < start || offset > end) return Collections.emptyList();
        javax.lang.model.type.TypeMirror type = info.getTrees().getTypeMirror(new TreePath(path, annotation.getAnnotationType()));
        if (type != null && type.getKind() != javax.lang.model.type.TypeKind.ERROR) return Collections.emptyList();
        boolean springContext = info.getElements().getTypeElement("org.springframework.stereotype.Component") != null
                || info.getElements().getTypeElement("org.springframework.boot.SpringApplication") != null;
        String name = annotation.getAnnotationType().toString();
        for (ImportTree imported : info.getCompilationUnit().getImports()) {
            String importedName = imported.getQualifiedIdentifier().toString();
            if (!imported.isStatic() && importedName.startsWith("org.springframework.")) springContext = true;
            // Never interpret an explicitly imported annotation from another framework as Spring.
            if (!imported.isStatic() && importedName.endsWith("." + name) && !importedName.startsWith("org.springframework.")) {
                return Collections.emptyList();
            }
        }
        String fqn = recognized(name, springContext);
        if (fqn == null) return Collections.emptyList();
        boolean available = info.getElements().getTypeElement(fqn) != null;
        return Collections.singletonList(new ExplanationFix(fqn, available));
    }

    @Override public String getId() { return getClass().getName(); }
    @Override public String getDisplayName() { return message("title"); }
    @Override public void cancel() { }

    static String details(String fqn, boolean available) {
        return message("details", fqn, message(available ? "import" : "dependency"));
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(SpringAnnotationExplanation.class, "AnnotationExplain." + key, args);
    }

    private static final class ExplanationFix implements Fix {
        private final String fqn;
        private final boolean available;
        ExplanationFix(String fqn, boolean available) { this.fqn = fqn; this.available = available; }
        @Override public String getText() { return message("title"); }
        @Override public ChangeInfo implement() throws Exception {
            JTextArea text = new JTextArea(details(fqn, available), 13, 60);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setCaretPosition(0);
            text.getAccessibleContext().setAccessibleName(message("title"));
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(new JScrollPane(text), BorderLayout.CENTER);
            JButton docs = new JButton(message("docs"));
            docs.addActionListener(event -> {
                try {
                    HtmlBrowser.URLDisplayer.getDefault().showURL(new URL(
                            "https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html"));
                } catch (java.net.MalformedURLException ex) { org.openide.util.Exceptions.printStackTrace(ex); }
            });
            panel.add(docs, BorderLayout.SOUTH);
            DialogDisplayer.getDefault().notify(new NotifyDescriptor(panel, message("title"),
                    NotifyDescriptor.DEFAULT_OPTION, NotifyDescriptor.INFORMATION_MESSAGE,
                    new Object[]{NotifyDescriptor.CLOSED_OPTION}, NotifyDescriptor.CLOSED_OPTION));
            return null;
        }
    }
}
