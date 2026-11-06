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
package org.netbeans.modules.spring.beans.model.impl;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import org.netbeans.api.java.source.ClassIndex.SearchKind;
import org.netbeans.api.java.source.ClassIndex.SearchScope;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.ElementUtilities;
import org.netbeans.modules.j2ee.metadata.model.api.support.annotation.AnnotationHandler;
import org.netbeans.modules.j2ee.metadata.model.api.support.annotation.AnnotationModelHelper;
import org.netbeans.modules.j2ee.metadata.model.api.support.annotation.parser.ParseResult;
import org.netbeans.modules.spring.api.beans.SpringAnnotations;

/**
 * Isolated support for {@code @Bean} factory methods, Spring Data repositories,
 * and modern Spring annotation attributes.
 */
final class SpringBeanMethodSupport {

    static final String BEAN_ANNOTATION = "org.springframework.context.annotation.Bean"; //NOI18N
    static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository"; //NOI18N
    static final String SPRING_DATA_REPOSITORY_DEFINITION = "org.springframework.data.repository.RepositoryDefinition"; //NOI18N
    static final String SPRING_DATA_NO_REPOSITORY_BEAN = "org.springframework.data.repository.NoRepositoryBean"; //NOI18N

    private SpringBeanMethodSupport() {
    }

    static List<SpringBeanImpl> createInitialObjects(AnnotationModelHelper helper, ObjectProviders.SpringBeanProvider provider) throws InterruptedException {
        final List<SpringBeanImpl> result = new LinkedList<SpringBeanImpl>();
        final Set<TypeElement> scannedTypes = new LinkedHashSet<TypeElement>();
        for (String annotationName : SpringAnnotations.SPRING_COMPONENTS) {
            helper.getAnnotationScanner().findAnnotations(annotationName, EnumSet.of(ElementKind.CLASS, ElementKind.INTERFACE),
                    new AnnotationHandler() {
                        @Override
                        public void handleAnnotation(TypeElement type, Element element, AnnotationMirror annotation) {
                            scannedTypes.add(type);
                        }
                    });
        }
        helper.getAnnotationScanner().findAnnotations(BEAN_ANNOTATION, EnumSet.of(ElementKind.METHOD),
                new AnnotationHandler() {
                    @Override
                    public void handleAnnotation(TypeElement type, Element element, AnnotationMirror annotation) {
                        scannedTypes.add(type);
                    }
                });
        collectSpringDataRepositories(helper, scannedTypes);
        for (TypeElement type : scannedTypes) {
            result.addAll(createObjects(helper, provider, type));
        }
        return result;
    }

    static List<SpringBeanImpl> createObjects(AnnotationModelHelper helper, ObjectProviders.SpringBeanProvider provider, TypeElement type) {
        final List<SpringBeanImpl> result = new ArrayList<SpringBeanImpl>();
        if (type.getKind() == ElementKind.CLASS || type.getKind() == ElementKind.INTERFACE) {
            if (!helper.hasAnnotation(type.getAnnotationMirrors(), SPRING_DATA_NO_REPOSITORY_BEAN)
                    && (helper.hasAnyAnnotation(type.getAnnotationMirrors(), SpringAnnotations.SPRING_COMPONENTS)
                    || isSpringDataRepository(helper, type))) {
                result.add(provider.createObject(helper, type));
            }
            Set<String> beanSet = Collections.singleton(BEAN_ANNOTATION);
            for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
                if (helper.hasAnyAnnotation(method.getAnnotationMirrors(), beanSet)) {
                    result.add(new SpringBeanImpl(helper, type, method));
                }
            }
        }
        return result;
    }

    static boolean modifyObjects(AnnotationModelHelper helper, ObjectProviders.SpringBeanProvider provider, TypeElement type, List<SpringBeanImpl> objects) {
        List<SpringBeanImpl> updated = createObjects(helper, provider, type);
        objects.clear();
        objects.addAll(updated);
        return true;
    }

    static boolean isSpringDataRepository(AnnotationModelHelper helper, TypeElement type) {
        if (type == null || type.getKind() != ElementKind.INTERFACE) {
            return false;
        }
        String fqn = type.getQualifiedName().toString();
        if (fqn.startsWith("org.springframework.data.")) { //NOI18N
            return false;
        }
        if (helper.hasAnnotation(type.getAnnotationMirrors(), SPRING_DATA_NO_REPOSITORY_BEAN)) {
            return false;
        }
        if (helper.hasAnnotation(type.getAnnotationMirrors(), SPRING_DATA_REPOSITORY_DEFINITION)) {
            return true;
        }
        CompilationInfo info = helper.getCompilationController();
        if (info == null) {
            return false;
        }
        TypeElement repoElem = info.getElements().getTypeElement(SPRING_DATA_REPOSITORY);
        if (repoElem == null) {
            return false;
        }
        Types types = info.getTypes();
        return types.isSubtype(types.erasure(type.asType()), types.erasure(repoElem.asType()));
    }

    private static void collectSpringDataRepositories(AnnotationModelHelper helper, Set<TypeElement> target) throws InterruptedException {
        CompilationInfo info = helper.getCompilationController();
        if (info == null) {
            return;
        }
        TypeElement repoRoot = info.getElements().getTypeElement(SPRING_DATA_REPOSITORY);
        if (repoRoot == null) {
            return;
        }
        Queue<ElementHandle<TypeElement>> queue = new LinkedList<ElementHandle<TypeElement>>();
        Set<ElementHandle<TypeElement>> visited = new HashSet<ElementHandle<TypeElement>>();
        ElementHandle<TypeElement> rootHandle = ElementHandle.create(repoRoot);
        queue.add(rootHandle);
        visited.add(rootHandle);

        while (!queue.isEmpty()) {
            ElementHandle<TypeElement> current = queue.poll();
            Set<ElementHandle<TypeElement>> implementors = info.getClasspathInfo().getClassIndex().getElements(
                    current,
                    EnumSet.of(SearchKind.IMPLEMENTORS),
                    EnumSet.of(SearchScope.SOURCE, SearchScope.DEPENDENCIES));
            if (implementors == null) {
                throw new InterruptedException("ClassIndex.getElements() was interrupted"); //NOI18N
            }
            for (ElementHandle<TypeElement> implHandle : implementors) {
                if (visited.add(implHandle)) {
                    TypeElement resolved = implHandle.resolve(info);
                    if (resolved != null && resolved.getKind() == ElementKind.INTERFACE) {
                        queue.add(implHandle);
                        if (isSpringDataRepository(helper, resolved)) {
                            target.add(resolved);
                        }
                    }
                }
            }
        }
    }

    static String resolveExplicitName(ParseResult parseResult) {
        String explicitName = parseResult.get("value", String.class); //NOI18N
        if (explicitName == null || explicitName.isEmpty()) {
            explicitName = parseResult.get("name", String.class); //NOI18N
        }
        return (explicitName == null || explicitName.isEmpty()) ? null : explicitName;
    }

    static String resolveReturnClassName(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        if (returnType.getKind() == TypeKind.DECLARED) {
            Element returnEl = ((DeclaredType) returnType).asElement();
            if (returnEl instanceof TypeElement) {
                return ElementUtilities.getBinaryName((TypeElement) returnEl);
            }
        }
        return returnType.toString();
    }

    static void extractBeanMethodNames(AnnotationMirror beanMirror, List<String> target) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : beanMirror.getElementValues().entrySet()) {
            String attrName = entry.getKey().getSimpleName().toString();
            if ("value".equals(attrName) || "name".equals(attrName)) { //NOI18N
                Object val = entry.getValue().getValue();
                if (val instanceof String) {
                    String s = (String) val;
                    if (!s.isEmpty() && !target.contains(s)) {
                        target.add(s);
                    }
                } else if (val instanceof List<?>) {
                    for (Object item : (List<?>) val) {
                        if (item instanceof AnnotationValue) {
                            Object inner = ((AnnotationValue) item).getValue();
                            if (inner instanceof String) {
                                String s = (String) inner;
                                if (!s.isEmpty() && !target.contains(s)) {
                                    target.add(s);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static int resolveElementOffset(AnnotationModelHelper helper, Element element) {
        if (element != null && helper.getCompilationController() != null) {
            Trees trees = helper.getCompilationController().getTrees();
            if (trees != null) {
                TreePath path = trees.getPath(element);
                if (path != null && path.getCompilationUnit() != null) {
                    long pos = trees.getSourcePositions().getStartPosition(path.getCompilationUnit(), path.getLeaf());
                    if (pos > 0) {
                        return (int) pos;
                    }
                }
            }
        }
        return 0;
    }
}
