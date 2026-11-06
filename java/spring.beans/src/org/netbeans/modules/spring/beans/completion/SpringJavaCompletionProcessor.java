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
package org.netbeans.modules.spring.beans.completion;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.modules.j2ee.metadata.model.api.MetadataModel;
import org.netbeans.modules.j2ee.metadata.model.api.MetadataModelAction;
import org.netbeans.modules.spring.api.Action;
import org.netbeans.modules.spring.api.beans.SpringScope;
import org.netbeans.modules.spring.api.beans.model.FileSpringBeans;
import org.netbeans.modules.spring.api.beans.model.ModelUnit;
import org.netbeans.modules.spring.api.beans.model.SpringBean;
import org.netbeans.modules.spring.api.beans.model.SpringConfigModel;
import org.netbeans.modules.spring.api.beans.model.SpringConfigModel.DocumentAccess;
import org.netbeans.modules.spring.api.beans.model.SpringModel;
import org.netbeans.modules.spring.api.beans.model.SpringModelFactory;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;

/**
 * Provides Spring code completion inside Java annotations:
 * <ul>
 *   <li>Bean names in {@code @Qualifier}, {@code @DependsOn}, {@code @Resource(name = ...)}, {@code @Named}</li>
 *   <li>Property keys in {@code @Value("${...}")} from project {@code .properties} files</li>
 *   <li>Profile names in {@code @Profile} and {@code @ActiveProfiles}</li>
 * </ul>
 */
public class SpringJavaCompletionProcessor implements Processor {

    private static final String QUALIFIER_ANNOTATION = "org.springframework.beans.factory.annotation.Qualifier"; //NOI18N
    private static final String DEPENDS_ON_ANNOTATION = "org.springframework.context.annotation.DependsOn"; //NOI18N
    private static final String JAVAX_RESOURCE_ANNOTATION = "javax.annotation.Resource"; //NOI18N
    private static final String JAKARTA_RESOURCE_ANNOTATION = "jakarta.annotation.Resource"; //NOI18N
    private static final String JAVAX_NAMED_ANNOTATION = "javax.inject.Named"; //NOI18N
    private static final String JAKARTA_NAMED_ANNOTATION = "jakarta.inject.Named"; //NOI18N
    private static final String VALUE_ANNOTATION = "org.springframework.beans.factory.annotation.Value"; //NOI18N
    private static final String PROFILE_ANNOTATION = "org.springframework.context.annotation.Profile"; //NOI18N
    private static final String ACTIVE_PROFILES_ANNOTATION = "org.springframework.test.context.ActiveProfiles"; //NOI18N

    private static final Set<String> SUPPORTED_ANNOTATIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            QUALIFIER_ANNOTATION,
            DEPENDS_ON_ANNOTATION,
            JAVAX_RESOURCE_ANNOTATION,
            JAKARTA_RESOURCE_ANNOTATION,
            JAVAX_NAMED_ANNOTATION,
            JAKARTA_NAMED_ANNOTATION,
            VALUE_ANNOTATION,
            PROFILE_ANNOTATION,
            ACTIVE_PROFILES_ANNOTATION
    )));

    private static final Pattern PROFILE_FILE_PATTERN = Pattern.compile(
            "^(?:application|bootstrap)-([a-zA-Z0-9_.-]+)\\.(?:properties|yml|yaml)$", Pattern.CASE_INSENSITIVE); //NOI18N

    private Reference<ProcessingEnvironment> processingEnv;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = new WeakReference<ProcessingEnvironment>(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        if (annotation == null || member == null || processingEnv == null) {
            return Collections.emptyList();
        }
        ProcessingEnvironment env = processingEnv.get();
        if (env == null) {
            return Collections.emptyList();
        }
        Element annEl = annotation.getAnnotationType().asElement();
        if (!(annEl instanceof TypeElement)) {
            return Collections.emptyList();
        }
        String annotationFqn = ((TypeElement) annEl).getQualifiedName().toString();
        String memberName = member.getSimpleName().toString();

        FileObject sourceFo = resolveSourceFileObject(env, element);
        if (sourceFo == null) {
            return Collections.emptyList();
        }

        if (QUALIFIER_ANNOTATION.equals(annotationFqn) || DEPENDS_ON_ANNOTATION.equals(annotationFqn)) {
            if ("value".equals(memberName)) { //NOI18N
                return completeBeanNames(sourceFo);
            }
        } else if (JAVAX_RESOURCE_ANNOTATION.equals(annotationFqn) || JAKARTA_RESOURCE_ANNOTATION.equals(annotationFqn)) {
            if ("name".equals(memberName)) { //NOI18N
                return completeBeanNames(sourceFo);
            }
        } else if (JAVAX_NAMED_ANNOTATION.equals(annotationFqn) || JAKARTA_NAMED_ANNOTATION.equals(annotationFqn)) {
            if ("value".equals(memberName) && element != null && element.getKind() != ElementKind.CLASS) { //NOI18N
                return completeBeanNames(sourceFo);
            }
        } else if (VALUE_ANNOTATION.equals(annotationFqn)) {
            if ("value".equals(memberName)) { //NOI18N
                return completePropertyKeys(sourceFo);
            }
        } else if (PROFILE_ANNOTATION.equals(annotationFqn)) {
            if ("value".equals(memberName)) { //NOI18N
                return completeProfiles(sourceFo);
            }
        } else if (ACTIVE_PROFILES_ANNOTATION.equals(annotationFqn)) {
            if ("value".equals(memberName) || "profiles".equals(memberName)) { //NOI18N
                return completeProfiles(sourceFo);
            }
        }
        return Collections.emptyList();
    }

    private static FileObject resolveSourceFileObject(ProcessingEnvironment env, Element element) {
        if (element == null) {
            return null;
        }
        Trees trees = Trees.instance(env);
        TreePath path = trees.getPath(element);
        if (path == null || path.getCompilationUnit() == null || path.getCompilationUnit().getSourceFile() == null) {
            return null;
        }
        try {
            return URLMapper.findFileObject(path.getCompilationUnit().getSourceFile().toUri().toURL());
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    static List<Completion> completeBeanNames(FileObject sourceFo) {
        final Map<String, String> beanToClass = new LinkedHashMap<String, String>();
        SpringScope scope = SpringScope.getSpringScope(sourceFo);
        if (scope != null) {
            for (SpringConfigModel configModel : scope.getAllConfigModels()) {
                try {
                    configModel.runDocumentAction(new Action<DocumentAccess>() {
                        @Override
                        public void run(DocumentAccess docAccess) {
                            FileObject fo = docAccess.getFileObject();
                            if (fo == null) {
                                return;
                            }
                            FileSpringBeans fileBeans = docAccess.getSpringBeans().getFileBeans(fo);
                            if (fileBeans == null) {
                                return;
                            }
                            for (SpringBean bean : fileBeans.getBeans()) {
                                recordBean(bean, beanToClass);
                            }
                            for (String alias : fileBeans.getAliases()) {
                                if (alias != null && !alias.isEmpty() && !beanToClass.containsKey(alias)) {
                                    beanToClass.put(alias, null);
                                }
                            }
                        }
                    });
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        MetadataModel<SpringModel> annotationModel = null;
        if (scope != null) {
            annotationModel = scope.getSpringAnnotationModel(sourceFo);
        } else {
            ClassPath bootCp = ClassPath.getClassPath(sourceFo, ClassPath.BOOT);
            ClassPath compileCp = ClassPath.getClassPath(sourceFo, ClassPath.COMPILE);
            ClassPath srcCp = ClassPath.getClassPath(sourceFo, ClassPath.SOURCE);
            if (srcCp != null) {
                ModelUnit unit = ModelUnit.create(bootCp, compileCp, srcCp);
                annotationModel = SpringModelFactory.getMetaModel(unit);
            }
        }

        if (annotationModel != null) {
            try {
                annotationModel.runReadAction(new MetadataModelAction<SpringModel, Void>() {
                    @Override
                    public Void run(SpringModel metadata) throws Exception {
                        for (SpringBean bean : metadata.getBeans()) {
                            recordBean(bean, beanToClass);
                        }
                        return null;
                    }
                });
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        List<Completion> completions = new ArrayList<Completion>(beanToClass.size());
        for (Map.Entry<String, String> entry : beanToClass.entrySet()) {
            final String value = "\"" + entry.getKey() + "\""; //NOI18N
            final String className = entry.getValue();
            completions.add(new Completion() {
                @Override
                public String getValue() {
                    return value;
                }

                @Override
                public String getMessage() {
                    return className;
                }
            });
        }
        return completions;
    }

    private static void recordBean(SpringBean bean, Map<String, String> beanToClass) {
        String cls = bean.getClassName();
        String id = bean.getId();
        if (id != null && !id.isEmpty() && !beanToClass.containsKey(id)) {
            beanToClass.put(id, cls);
        }
        if (bean.getNames() != null) {
            for (String name : bean.getNames()) {
                if (name != null && !name.isEmpty() && !beanToClass.containsKey(name)) {
                    beanToClass.put(name, cls);
                }
            }
        }
    }

    static List<Completion> completePropertyKeys(FileObject sourceFo) {
        Map<String, String> propToValue = new LinkedHashMap<String, String>();
        for (FileObject root : collectProjectResourceRoots(sourceFo)) {
            collectPropertiesFromFolder(root, propToValue, 0);
        }
        List<Completion> completions = new ArrayList<Completion>(propToValue.size());
        for (Map.Entry<String, String> entry : propToValue.entrySet()) {
            final String formatted = "\"${" + entry.getKey() + "}\""; //NOI18N
            final String defaultVal = entry.getValue();
            completions.add(new Completion() {
                @Override
                public String getValue() {
                    return formatted;
                }

                @Override
                public String getMessage() {
                    return defaultVal;
                }
            });
        }
        return completions;
    }

    static List<Completion> completeProfiles(FileObject sourceFo) {
        Set<String> profiles = new LinkedHashSet<String>();
        for (FileObject root : collectProjectResourceRoots(sourceFo)) {
            collectProfilesFromFolder(root, profiles, 0);
        }
        List<Completion> completions = new ArrayList<Completion>(profiles.size());
        for (final String profile : profiles) {
            completions.add(new Completion() {
                @Override
                public String getValue() {
                    return "\"" + profile + "\""; //NOI18N
                }

                @Override
                public String getMessage() {
                    return null;
                }
            });
        }
        return completions;
    }

    private static Set<FileObject> collectProjectResourceRoots(FileObject sourceFo) {
        Set<FileObject> roots = new LinkedHashSet<FileObject>();
        Project project = FileOwnerQuery.getOwner(sourceFo);
        if (project != null) {
            for (SourceGroup sg : ProjectUtils.getSources(project).getSourceGroups(JavaProjectConstants.SOURCES_TYPE_RESOURCES)) {
                if (sg.getRootFolder() != null) {
                    roots.add(sg.getRootFolder());
                }
            }
            for (SourceGroup sg : ProjectUtils.getSources(project).getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
                if (sg.getRootFolder() != null) {
                    roots.add(sg.getRootFolder());
                }
            }
        }
        ClassPath srcCp = ClassPath.getClassPath(sourceFo, ClassPath.SOURCE);
        if (srcCp != null) {
            for (FileObject root : srcCp.getRoots()) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static void collectPropertiesFromFolder(FileObject folder, Map<String, String> target, int depth) {
        if (folder == null || depth > 4) {
            return;
        }
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                collectPropertiesFromFolder(child, target, depth + 1);
            } else if ("properties".equalsIgnoreCase(child.getExt())) { //NOI18N
                Properties props = new Properties();
                InputStream in = null;
                try {
                    in = child.getInputStream();
                    props.load(in);
                    Enumeration<?> names = props.propertyNames();
                    while (names.hasMoreElements()) {
                        Object keyObj = names.nextElement();
                        if (keyObj instanceof String) {
                            String key = (String) keyObj;
                            if (!key.isEmpty() && !target.containsKey(key)) {
                                target.put(key, props.getProperty(key));
                            }
                        }
                    }
                } catch (IOException ex) {
                    // ignore unreadable properties file
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ex) {
                        }
                    }
                }
            }
        }
    }

    private static void collectProfilesFromFolder(FileObject folder, Set<String> profiles, int depth) {
        if (folder == null || depth > 3) {
            return;
        }
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                collectProfilesFromFolder(child, profiles, depth + 1);
            } else {
                Matcher m = PROFILE_FILE_PATTERN.matcher(child.getNameExt());
                if (m.matches()) {
                    String profile = m.group(1);
                    if (profile != null && !profile.isEmpty()) {
                        profiles.add(profile);
                    }
                }
            }
        }
    }
}
