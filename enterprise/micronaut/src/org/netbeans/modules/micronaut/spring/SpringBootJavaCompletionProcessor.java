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

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.micronaut.MicronautConfigProperties;
import org.netbeans.modules.micronaut.completion.MicronautConfigDocumentation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataGroup;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;

/**
 * Isolated Spring Boot Java annotation completion processor for {@code @Value},
 * {@code @ConfigurationProperties}, and {@code @ConditionalOnProperty}.
 */
public class SpringBootJavaCompletionProcessor implements Processor {

    private static final Set<String> SUPPORTED_ANNOTATION_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.springframework.beans.factory.annotation.Value",
            "org.springframework.boot.context.properties.ConfigurationProperties",
            "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty"
    )));

    private Reference<ProcessingEnvironment> processingEnv;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        if (member != null && annotation != null && this.processingEnv != null) {
            ProcessingEnvironment env = this.processingEnv.get();
            if (env != null) {
                Name annotationTypeSimpleName = annotation.getAnnotationType().asElement().getSimpleName();
                String prefix = null;
                String postfix = null;
                boolean completeGroups = false;
                if ("Value".contentEquals(annotationTypeSimpleName)) {
                    prefix = "\"${";
                    postfix = "}\"";
                } else if ("ConfigurationProperties".contentEquals(annotationTypeSimpleName)) {
                    if ("prefix".contentEquals(member.getSimpleName()) || "value".contentEquals(member.getSimpleName())) {
                        prefix = "\"";
                        postfix = prefix;
                        completeGroups = true;
                    }
                } else if ("ConditionalOnProperty".contentEquals(annotationTypeSimpleName)) {
                    if ("name".contentEquals(member.getSimpleName()) || "value".contentEquals(member.getSimpleName())) {
                        prefix = "\"";
                        postfix = prefix;
                    } else if ("prefix".contentEquals(member.getSimpleName())) {
                        prefix = "\"";
                        postfix = prefix;
                        completeGroups = true;
                    }
                }
                if (prefix != null && postfix != null) {
                    Trees trees = Trees.instance(env);
                    TreePath path = trees.getPath(element);
                    if (path != null) {
                        FileObject fo;
                        try {
                            fo = URLMapper.findFileObject(path.getCompilationUnit().getSourceFile().toUri().toURL());
                        } catch (MalformedURLException ex) {
                            Exceptions.printStackTrace(ex);
                            return Collections.emptyList();
                        }
                        Project project = fo != null ? FileOwnerQuery.getOwner(fo) : null;
                        if (project != null) {
                            String format = prefix + "%s" + postfix;
                            if (completeGroups) {
                                Map<String, ConfigurationMetadataGroup> groups = MicronautConfigProperties.getGroups(project);
                                List<Completion> ret = new ArrayList<>(groups.size());
                                for (ConfigurationMetadataGroup group : groups.values()) {
                                    String groupId = group.getId();
                                    if (groupId != null && !groupId.isEmpty() && !"_ROOT_GROUP_".equals(groupId) && !groupId.contains("*")) {
                                        ret.add(new Completion() {
                                            @Override
                                            public String getValue() {
                                                return String.format(format, groupId);
                                            }
                                            @Override
                                            public String getMessage() {
                                                return null;
                                            }
                                        });
                                    }
                                }
                                return ret;
                            }
                            Map<String, ConfigurationMetadataProperty> properties = MicronautConfigProperties.getProperties(project);
                            List<Completion> ret = new ArrayList<>(properties.size());
                            for (ConfigurationMetadataProperty property : properties.values()) {
                                if (!property.getId().contains("*")) {
                                    ret.add(new Completion() {
                                        @Override
                                        public String getValue() {
                                            return String.format(format, property.getId());
                                        }
                                        @Override
                                        public String getMessage() {
                                            return new MicronautConfigDocumentation(property).getText();
                                        }
                                    });
                                }
                            }
                            return ret;
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATION_TYPES;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = new WeakReference<>(processingEnv);
    }
}
