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
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import org.netbeans.api.java.source.CompilationInfo;

/**
 * Isolated helper for Spring Data repository entity resolution, persistent field
 * discovery, and {@code @Query} detection.
 */
public final class SpringDataCompletionHelper {

    public static final String SPRING_REPOSITORY_TYPE_NAME = "org.springframework.data.repository.Repository"; //NOI18N
    public static final String SPRING_REPOSITORY_DEFINITION_ANNOTATION_NAME = "org.springframework.data.repository.RepositoryDefinition"; //NOI18N
    public static final String SPRING_JDBC_QUERY_ANNOTATION_TYPE_NAME = "org.springframework.data.jdbc.repository.query.Query"; //NOI18N
    public static final String SPRING_JPA_QUERY_ANNOTATION_TYPE_NAME = "org.springframework.data.jpa.repository.Query"; //NOI18N

    private static final Set<String> TRANSIENT_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "javax.persistence.Transient", //NOI18N
            "jakarta.persistence.Transient", //NOI18N
            "org.springframework.data.annotation.Transient" //NOI18N
    ));

    private SpringDataCompletionHelper() {
    }

    public static boolean isSpringJdbcQuery(TypeElement te) {
        return te != null && SPRING_JDBC_QUERY_ANNOTATION_TYPE_NAME.contentEquals(te.getQualifiedName());
    }

    public static boolean isSpringSqlQuery(CompilationInfo info, TreePath annotationPath, TypeElement te) {
        if (te == null) {
            return false;
        }
        if (isSpringJdbcQuery(te)) {
            return true;
        }
        if (SPRING_JPA_QUERY_ANNOTATION_TYPE_NAME.contentEquals(te.getQualifiedName()) && annotationPath != null
                && annotationPath.getLeaf().getKind() == Tree.Kind.ANNOTATION) {
            AnnotationTree annTree = (AnnotationTree) annotationPath.getLeaf();
            for (ExpressionTree arg : annTree.getArguments()) {
                if (arg.getKind() == Tree.Kind.ASSIGNMENT) {
                    AssignmentTree assign = (AssignmentTree) arg;
                    ExpressionTree var = assign.getVariable();
                    ExpressionTree expr = assign.getExpression();
                    if (var.getKind() == Tree.Kind.IDENTIFIER
                            && "nativeQuery".contentEquals(((IdentifierTree) var).getName()) //NOI18N
                            && expr.getKind() == Tree.Kind.BOOLEAN_LITERAL
                            && Boolean.TRUE.equals(((LiteralTree) expr).getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static TypeElement getSpringEntityFor(CompilationInfo info, TypeElement te) {
        if (te == null || !te.getModifiers().contains(Modifier.ABSTRACT)) {
            return null;
        }
        TypeElement fromRepoDef = resolveRepositoryDefinitionDomainClass(te);
        if (fromRepoDef != null) {
            return fromRepoDef;
        }
        TypeElement springRepoElem = info.getElements().getTypeElement(SPRING_REPOSITORY_TYPE_NAME);
        if (springRepoElem != null) {
            Types types = info.getTypes();
            TypeMirror springRepoType = types.erasure(springRepoElem.asType());
            Queue<TypeMirror> queue = new ArrayDeque<>(te.getInterfaces());
            Set<Element> visited = new HashSet<>();
            TypeElement fallbackCandidate = null;
            while (!queue.isEmpty()) {
                TypeMirror current = queue.poll();
                if (current == null || current.getKind() != TypeKind.DECLARED) {
                    continue;
                }
                DeclaredType declared = (DeclaredType) current;
                Element currentElem = declared.asElement();
                if (currentElem == null || !visited.add(currentElem)) {
                    continue;
                }
                TypeMirror erasedCurrent = types.erasure(current);
                if (!types.isSubtype(erasedCurrent, springRepoType)) {
                    continue;
                }
                List<? extends TypeMirror> typeArguments = declared.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    TypeMirror entityType = typeArguments.get(0);
                    if (entityType != null && entityType.getKind() == TypeKind.DECLARED) {
                        Element el = ((DeclaredType) entityType).asElement();
                        if (el instanceof TypeElement) {
                            if (types.isSameType(erasedCurrent, springRepoType)) {
                                return (TypeElement) el;
                            }
                            if (fallbackCandidate == null) {
                                fallbackCandidate = (TypeElement) el;
                            }
                        }
                    }
                }
                for (TypeMirror superType : types.directSupertypes(current)) {
                    if (superType.getKind() == TypeKind.DECLARED) {
                        queue.offer(superType);
                    }
                }
            }
            if (fallbackCandidate != null) {
                return fallbackCandidate;
            }
        }
        return null;
    }

    private static TypeElement resolveRepositoryDefinitionDomainClass(TypeElement te) {
        for (AnnotationMirror am : te.getAnnotationMirrors()) {
            Element annEl = am.getAnnotationType().asElement();
            if (annEl instanceof TypeElement
                    && SPRING_REPOSITORY_DEFINITION_ANNOTATION_NAME.contentEquals(((TypeElement) annEl).getQualifiedName())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
                    if ("domainClass".contentEquals(entry.getKey().getSimpleName())) { //NOI18N
                        Object val = entry.getValue().getValue();
                        if (val instanceof TypeMirror && ((TypeMirror) val).getKind() == TypeKind.DECLARED) {
                            Element domainEl = ((DeclaredType) val).asElement();
                            if (domainEl instanceof TypeElement) {
                                return (TypeElement) domainEl;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Collects persistent fields from the entity and its superclasses (including {@code @MappedSuperclass}),
     * excluding {@code static}, {@code transient}, and {@code @Transient} fields.
     */
    public static List<VariableElement> getEntityFields(TypeElement entity) {
        if (entity == null) {
            return List.of();
        }
        Map<String, VariableElement> fieldsByName = new LinkedHashMap<>();
        TypeElement current = entity;
        Set<TypeElement> visited = new HashSet<>();
        while (current != null && visited.add(current) && !"java.lang.Object".contentEquals(current.getQualifiedName())) { //NOI18N
            for (VariableElement field : ElementFilter.fieldsIn(current.getEnclosedElements())) {
                if (isPersistentField(field)) {
                    String name = field.getSimpleName().toString();
                    fieldsByName.putIfAbsent(name, field);
                }
            }
            TypeMirror superclass = current.getSuperclass();
            if (superclass != null && superclass.getKind() == TypeKind.DECLARED) {
                Element superEl = ((DeclaredType) superclass).asElement();
                current = superEl instanceof TypeElement ? (TypeElement) superEl : null;
            } else {
                current = null;
            }
        }
        return new ArrayList<>(fieldsByName.values());
    }

    private static boolean isPersistentField(VariableElement field) {
        Set<Modifier> mods = field.getModifiers();
        if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.TRANSIENT)) {
            return false;
        }
        for (AnnotationMirror am : field.getAnnotationMirrors()) {
            Element annEl = am.getAnnotationType().asElement();
            if (annEl instanceof TypeElement && TRANSIENT_ANNOTATIONS.contains(((TypeElement) annEl).getQualifiedName().toString())) {
                return false;
            }
        }
        return true;
    }
}
