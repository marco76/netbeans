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
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.TypeUtilities;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataGroup;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;

/**
 * Isolated helper for loading additional Spring Boot configuration metadata and scanning
 * {@code @ConfigurationProperties} classes from project sources.
 */
public final class SpringConfigPropertiesHelper {

    private static final String ADDITIONAL_CONFIG_METADATA_JSON = "META-INF/additional-spring-configuration-metadata.json";
    private static final ElementHandle<TypeElement> SPRING_CONFIG_PROPS_HANDLE =
            ElementHandle.createTypeElementHandle(ElementKind.ANNOTATION_TYPE, "org.springframework.boot.context.properties.ConfigurationProperties");

    private SpringConfigPropertiesHelper() {
    }

    public static boolean hasAdditionalConfigMetadata(ClassPath cp) {
        return cp != null && !cp.findAllResources(ADDITIONAL_CONFIG_METADATA_JSON).isEmpty();
    }

    public static void loadAdditionalProperties(Project project, ClassPath cp, Map<String, ConfigurationMetadataProperty> props) {
        if (cp != null) {
            for (FileObject fo : cp.findAllResources(ADDITIONAL_CONFIG_METADATA_JSON)) {
                try {
                    ConfigurationMetadataRepository repository = ConfigurationMetadataRepositoryJsonBuilder.create().withJsonResource(fo.getInputStream()).build();
                    props.putAll(repository.getAllProperties());
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        String customRepo = scanConfigurationPropertiesClasses(project);
        if (customRepo != null) {
            try {
                InputStream stream = new ByteArrayInputStream(customRepo.getBytes(StandardCharsets.UTF_8));
                ConfigurationMetadataRepository repository = ConfigurationMetadataRepositoryJsonBuilder.create().withJsonResource(stream).build();
                props.putAll(repository.getAllProperties());
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public static void loadAdditionalGroups(Project project, ClassPath cp, Map<String, ConfigurationMetadataGroup> groups) {
        if (cp != null) {
            for (FileObject fo : cp.findAllResources(ADDITIONAL_CONFIG_METADATA_JSON)) {
                try {
                    ConfigurationMetadataRepository repository = ConfigurationMetadataRepositoryJsonBuilder.create().withJsonResource(fo.getInputStream()).build();
                    groups.putAll(repository.getAllGroups());
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        String customRepo = scanConfigurationPropertiesClasses(project);
        if (customRepo != null) {
            try {
                InputStream stream = new ByteArrayInputStream(customRepo.getBytes(StandardCharsets.UTF_8));
                ConfigurationMetadataRepository repository = ConfigurationMetadataRepositoryJsonBuilder.create().withJsonResource(stream).build();
                groups.putAll(repository.getAllGroups());
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private static String scanConfigurationPropertiesClasses(Project project) {
        JSONArray groups = new JSONArray();
        JSONArray properties = new JSONArray();
        SourceGroup[] srcGroups = ProjectUtils.getSources(project).getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
        for (SourceGroup srcGroup : srcGroups) {
            ClassIndex ci = ClasspathInfo.create(srcGroup.getRootFolder()).getClassIndex();
            Set<FileObject> resources = ci.getResources(SPRING_CONFIG_PROPS_HANDLE,
                    EnumSet.of(ClassIndex.SearchKind.TYPE_REFERENCES), EnumSet.of(ClassIndex.SearchScope.SOURCE));
            if (resources.isEmpty()) {
                continue;
            }
            try {
                ParserManager.parse(resources.stream().map(resource -> Source.create(resource)).collect(Collectors.toList()), new UserTask() {
                    @Override
                    public void run(ResultIterator resultIterator) throws Exception {
                        CompilationController cc = CompilationController.get(resultIterator.getParserResult());
                        cc.toPhase(JavaSource.Phase.RESOLVED);
                        Trees trees = cc.getTrees();
                        TypeElement springPropsTe = SPRING_CONFIG_PROPS_HANDLE.resolve(cc);
                        if (springPropsTe == null) {
                            return;
                        }
                        new TreePathScanner<Void, Void>() {
                            @Override
                            public Void visitAnnotation(AnnotationTree node, Void p) {
                                Element annEl = trees.getElement(new TreePath(getCurrentPath(), node.getAnnotationType()));
                                if (springPropsTe == annEl) {
                                    String prefix = extractSpringConfigPrefix(node);
                                    if (prefix != null && !prefix.isEmpty()) {
                                        final TreePath grandParentPath = getCurrentPath().getParentPath().getParentPath();
                                        Element targetEl = trees.getElement(grandParentPath);
                                        if (targetEl instanceof TypeElement) {
                                            TypeElement configClass = (TypeElement) targetEl;
                                            try {
                                                String sourceTypeName = cc.getElementUtilities().getElementName(configClass, true).toString();
                                                JSONObject group = new JSONObject();
                                                group.put("name", prefix);
                                                group.put("type", sourceTypeName);
                                                groups.put(group);
                                                for (VariableElement field : ElementFilter.fieldsIn(configClass.getEnclosedElements())) {
                                                    if (field.getModifiers().contains(Modifier.STATIC)) {
                                                        continue;
                                                    }
                                                    String propName = prefix + "." + camelCaseToKebabCase(field.getSimpleName().toString());
                                                    String typeName = cc.getTypeUtilities().getTypeName(field.asType(), TypeUtilities.TypeNameOptions.PRINT_FQN).toString();
                                                    JSONObject property = new JSONObject();
                                                    property.put("name", propName);
                                                    property.put("type", typeName);
                                                    property.put("sourceType", sourceTypeName);
                                                    properties.put(property);
                                                }
                                            } catch (JSONException ex) {
                                                Exceptions.printStackTrace(ex);
                                            }
                                        }
                                    }
                                }
                                return super.visitAnnotation(node, p);
                            }
                        }.scan(new TreePath(cc.getCompilationUnit()), null);
                    }
                });
            } catch (ParseException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        if (groups.length() > 0 || properties.length() > 0) {
            try {
                JSONObject data = new JSONObject();
                data.put("groups", groups);
                data.put("properties", properties);
                return data.toString();
            } catch (JSONException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return null;
    }

    private static String extractSpringConfigPrefix(AnnotationTree node) {
        for (ExpressionTree argument : node.getArguments()) {
            if (argument.getKind() == Tree.Kind.STRING_LITERAL) {
                Object val = ((LiteralTree) argument).getValue();
                if (val instanceof String) {
                    return (String) val;
                }
            } else if (argument.getKind() == Tree.Kind.ASSIGNMENT) {
                ExpressionTree variable = ((AssignmentTree) argument).getVariable();
                ExpressionTree expression = ((AssignmentTree) argument).getExpression();
                if (expression.getKind() == Tree.Kind.STRING_LITERAL && variable.getKind() == Tree.Kind.IDENTIFIER) {
                    CharSequence varName = ((IdentifierTree) variable).getName();
                    if ("prefix".contentEquals(varName) || "value".contentEquals(varName)) {
                        Object val = ((LiteralTree) expression).getValue();
                        if (val instanceof String) {
                            return (String) val;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String camelCaseToKebabCase(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && !Character.isUpperCase(name.charAt(i - 1))) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
