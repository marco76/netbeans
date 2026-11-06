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
package org.netbeans.modules.spring.beans.wizards;

import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TypeUtilities;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.j2ee.core.api.support.SourceGroups;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.SpringModule;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

/**
 * Wizard iterator for creating a Spring Data Repository interface
 * ({@code JpaRepository}, {@code CrudRepository}, {@code ListCrudRepository},
 * {@code PagingAndSortingRepository}, or {@code @RepositoryDefinition}) for an
 * entity class.
 */
public final class NewSpringDataRepositoryWizardIterator implements WizardDescriptor.AsynchronousInstantiatingIterator<WizardDescriptor> {

    private static final String DEFAULT_TARGET_NAME = "EntityRepository"; //NOI18N

    private static final List<String> ENTITY_ANNOTATIONS = Arrays.asList(
            "jakarta.persistence.Entity", //NOI18N
            "javax.persistence.Entity", //NOI18N
            "org.springframework.data.relational.core.mapping.Table", //NOI18N
            "org.springframework.data.mongodb.core.mapping.Document" //NOI18N
    );

    private static final List<String> ID_ANNOTATIONS = Arrays.asList(
            "jakarta.persistence.Id", //NOI18N
            "javax.persistence.Id", //NOI18N
            "jakarta.persistence.EmbeddedId", //NOI18N
            "javax.persistence.EmbeddedId", //NOI18N
            "org.springframework.data.annotation.Id" //NOI18N
    );

    public enum RepositoryKind {
        JPA_REPOSITORY("JpaRepository (Spring Data JPA)", "org.springframework.data.jpa.repository.JpaRepository", true), //NOI18N
        LIST_CRUD_REPOSITORY("ListCrudRepository (Spring Data 3+)", "org.springframework.data.repository.ListCrudRepository", false), //NOI18N
        CRUD_REPOSITORY("CrudRepository (Spring Data Commons)", "org.springframework.data.repository.CrudRepository", false), //NOI18N
        PAGING_AND_SORTING_REPOSITORY("PagingAndSortingRepository", "org.springframework.data.repository.PagingAndSortingRepository", false), //NOI18N
        LIST_PAGING_AND_SORTING_REPOSITORY("ListPagingAndSortingRepository (Spring Data 3+)", "org.springframework.data.repository.ListPagingAndSortingRepository", false), //NOI18N
        REPOSITORY_DEFINITION("@RepositoryDefinition (Standalone Interface)", "org.springframework.data.repository.RepositoryDefinition", false); //NOI18N

        private final String displayName;
        private final String fqn;
        private final boolean jpaSpecific;

        RepositoryKind(String displayName, String fqn, boolean jpaSpecific) {
            this.displayName = displayName;
            this.fqn = fqn;
            this.jpaSpecific = jpaSpecific;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getFqn() {
            return fqn;
        }

        public String getSimpleName() {
            int dot = fqn.lastIndexOf('.');
            return dot >= 0 ? fqn.substring(dot + 1) : fqn;
        }

        public boolean isJpaSpecific() {
            return jpaSpecific;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final class EntityInfo {
        private final String entityFqn;
        private final String idTypeFqn;
        private final String samplePropertyName;
        private final String samplePropertyType;

        public EntityInfo(String entityFqn, String idTypeFqn, String samplePropertyName, String samplePropertyType) {
            this.entityFqn = entityFqn;
            this.idTypeFqn = idTypeFqn;
            this.samplePropertyName = samplePropertyName;
            this.samplePropertyType = samplePropertyType;
        }

        public String getEntityFqn() {
            return entityFqn;
        }

        public String getIdTypeFqn() {
            return idTypeFqn;
        }

        public String getSamplePropertyName() {
            return samplePropertyName;
        }

        public String getSamplePropertyType() {
            return samplePropertyType;
        }
    }

    private int index;
    private WizardDescriptor wizard;
    private WizardDescriptor.Panel<WizardDescriptor>[] panels;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private WizardDescriptor.Panel<WizardDescriptor>[] getPanels() {
        if (panels == null) {
            Project p = Templates.getProject(wizard);
            SourceGroup[] groups = SourceGroups.getJavaSourceGroups(p);
            if (groups == null || groups.length == 0) {
                groups = ProjectUtils.getSources(p).getSourceGroups(Sources.TYPE_GENERIC);
            }
            WizardDescriptor.Panel targetChooser = Templates.createSimpleTargetChooser(p, groups);
            panels = new WizardDescriptor.Panel[]{
                targetChooser,
                new SpringDataRepositoryPanel()
            };
            String[] steps = createSteps();
            for (int i = 0; i < panels.length; i++) {
                Component c = panels[i].getComponent();
                if (steps[i] == null) {
                    steps[i] = c.getName();
                }
                if (c instanceof JComponent) {
                    JComponent jc = (JComponent) c;
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                    jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, Boolean.TRUE);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, Boolean.TRUE);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, Boolean.TRUE);
                }
            }
        }
        return panels;
    }

    @Override
    public Set<FileObject> instantiate() throws IOException {
        Project project = Templates.getProject(wizard);
        FileObject targetFolder = Templates.getTargetFolder(wizard);
        String targetName = Templates.getTargetName(wizard);
        if (targetName == null || targetName.trim().isEmpty()) {
            targetName = DEFAULT_TARGET_NAME;
        }

        String entityClass = (String) wizard.getProperty(SpringDataRepositoryPanel.ENTITY_CLASS);
        String idType = (String) wizard.getProperty(SpringDataRepositoryPanel.ID_TYPE);
        RepositoryKind repoKind = (RepositoryKind) wizard.getProperty(SpringDataRepositoryPanel.REPOSITORY_TYPE);
        if (repoKind == null) {
            repoKind = RepositoryKind.JPA_REPOSITORY;
        }
        boolean specExecutor = Boolean.TRUE.equals(wizard.getProperty(SpringDataRepositoryPanel.SPECIFICATION_EXECUTOR));
        boolean addRepoAnnotation = Boolean.TRUE.equals(wizard.getProperty(SpringDataRepositoryPanel.ADD_REPOSITORY_ANNOTATION));
        boolean generateSamples = Boolean.TRUE.equals(wizard.getProperty(SpringDataRepositoryPanel.GENERATE_SAMPLE_METHODS));
        boolean ensureDependency = Boolean.TRUE.equals(wizard.getProperty(SpringDataRepositoryPanel.ENSURE_DATA_DEPENDENCY));
        String samplePropName = (String) wizard.getProperty(SpringDataRepositoryPanel.ENTITY_SAMPLE_PROP_NAME);
        String samplePropType = (String) wizard.getProperty(SpringDataRepositoryPanel.ENTITY_SAMPLE_PROP_TYPE);

        if (ensureDependency && project != null) {
            SpringModule dataModule = SpringModuleCatalog.findModuleById("data-jpa"); //NOI18N
            if (dataModule != null) {
                FileObject sourceArtifact = NewSpringXMLConfigWizardIterator.getSourceGroupArtifact(project, targetFolder);
                ProjectSpringDependencyModifier.addSpringModules(
                        project,
                        sourceArtifact,
                        Collections.singleton(dataModule),
                        true,
                        null,
                        null);
            }
        }

        String packageName = NewSpringJavaConfigWizardIterator.resolvePackageName(project, targetFolder);
        Map<String, Object> params = buildTemplateParameters(
                packageName,
                entityClass,
                idType,
                repoKind,
                specExecutor,
                addRepoAnnotation,
                generateSamples,
                samplePropName,
                samplePropType);

        DataFolder dataFolder = DataFolder.findFolder(targetFolder);
        FileObject templateFO = Templates.getTemplate(wizard);
        DataObject templateDO = DataObject.find(templateFO);
        FileObject createdFile = templateDO.createFromTemplate(dataFolder, targetName, params).getPrimaryFile();
        return Collections.singleton(createdFile);
    }

    static Map<String, Object> buildTemplateParameters(
            String packageName,
            String rawEntityClass,
            String rawIdType,
            RepositoryKind repositoryKind,
            boolean specificationExecutor,
            boolean addRepositoryAnnotation,
            boolean generateSampleMethods,
            String samplePropertyName,
            String samplePropertyType) {
        Map<String, Object> params = new HashMap<String, Object>();
        Set<String> imports = new TreeSet<String>();

        if (packageName != null && !packageName.isEmpty()) {
            params.put("package", packageName); //NOI18N
        }

        RepositoryKind kind = repositoryKind != null ? repositoryKind : RepositoryKind.JPA_REPOSITORY;
        String entityFqn = (rawEntityClass == null || rawEntityClass.trim().isEmpty()) ? "Object" : rawEntityClass.trim(); //NOI18N
        String idFqn = boxPrimitiveTypeName((rawIdType == null || rawIdType.trim().isEmpty()) ? "java.lang.Long" : rawIdType.trim()); //NOI18N

        String entitySimple = extractSimpleNameAndRecordImport(entityFqn, packageName, imports);
        String idSimple = extractSimpleNameAndRecordImport(idFqn, packageName, imports);

        params.put("entitySimpleName", entitySimple); //NOI18N
        params.put("idSimpleName", idSimple); //NOI18N
        params.put("addRepositoryAnnotation", addRepositoryAnnotation); //NOI18N
        if (addRepositoryAnnotation) {
            imports.add("org.springframework.stereotype.Repository"); //NOI18N
        }

        imports.add(kind.getFqn());
        List<String> extendsParts = new ArrayList<String>();
        if (kind == RepositoryKind.REPOSITORY_DEFINITION) {
            params.put("useRepositoryDefinition", Boolean.TRUE); //NOI18N
        } else {
            params.put("useRepositoryDefinition", Boolean.FALSE); //NOI18N
            extendsParts.add(kind.getSimpleName() + "<" + entitySimple + ", " + idSimple + ">"); //NOI18N
        }

        if (specificationExecutor) {
            imports.add("org.springframework.data.jpa.repository.JpaSpecificationExecutor"); //NOI18N
            extendsParts.add("JpaSpecificationExecutor<" + entitySimple + ">"); //NOI18N
        }
        params.put("extendsClause", String.join(", ", extendsParts)); //NOI18N

        if (generateSampleMethods) {
            List<String> sampleMethods = new ArrayList<String>();
            imports.add("java.util.List"); //NOI18N
            imports.add("java.util.Optional"); //NOI18N
            String propName = (samplePropertyName != null && !samplePropertyName.trim().isEmpty())
                    ? samplePropertyName.trim()
                    : null;
            String propTypeFqn = (samplePropertyType != null && !samplePropertyType.trim().isEmpty())
                    ? boxPrimitiveTypeName(samplePropertyType.trim())
                    : "java.lang.String"; //NOI18N
            String propTypeSimple = extractSimpleNameAndRecordImport(propTypeFqn, packageName, imports);

            if (propName != null) {
                String capitalizedProp = propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
                sampleMethods.add(""); //NOI18N
                sampleMethods.add("    List<" + entitySimple + "> findBy" + capitalizedProp + "(" + propTypeSimple + " " + propName + ");"); //NOI18N
                sampleMethods.add(""); //NOI18N
                sampleMethods.add("    boolean existsBy" + capitalizedProp + "(" + propTypeSimple + " " + propName + ");"); //NOI18N
                if (kind.isJpaSpecific()) {
                    imports.add("org.springframework.data.jpa.repository.Query"); //NOI18N
                    imports.add("org.springframework.data.repository.query.Param"); //NOI18N
                    sampleMethods.add(""); //NOI18N
                    sampleMethods.add("    @Query(\"SELECT e FROM " + entitySimple + " e WHERE e." + propName + " = :" + propName + "\")"); //NOI18N
                    sampleMethods.add("    Optional<" + entitySimple + "> findOneBy" + capitalizedProp + "(@Param(\"" + propName + "\") " + propTypeSimple + " " + propName + ");"); //NOI18N
                }
            } else {
                sampleMethods.add(""); //NOI18N
                sampleMethods.add("    // Optional<" + entitySimple + "> findByName(String name);"); //NOI18N
                sampleMethods.add("    // List<" + entitySimple + "> findAllByOrderByIdAsc();"); //NOI18N
            }
            params.put("sampleMethods", sampleMethods); //NOI18N
        }

        params.put("imports", new ArrayList<String>(imports)); //NOI18N
        return params;
    }

    static String boxPrimitiveTypeName(String typeName) {
        if (typeName == null) {
            return "java.lang.Long"; //NOI18N
        }
        String trimmed = typeName.trim();
        if ("long".equals(trimmed)) { //NOI18N
            return "java.lang.Long"; //NOI18N
        } else if ("int".equals(trimmed)) { //NOI18N
            return "java.lang.Integer"; //NOI18N
        } else if ("short".equals(trimmed)) { //NOI18N
            return "java.lang.Short"; //NOI18N
        } else if ("byte".equals(trimmed)) { //NOI18N
            return "java.lang.Byte"; //NOI18N
        } else if ("double".equals(trimmed)) { //NOI18N
            return "java.lang.Double"; //NOI18N
        } else if ("float".equals(trimmed)) { //NOI18N
            return "java.lang.Float"; //NOI18N
        } else if ("boolean".equals(trimmed)) { //NOI18N
            return "java.lang.Boolean"; //NOI18N
        } else if ("char".equals(trimmed)) { //NOI18N
            return "java.lang.Character"; //NOI18N
        }
        return trimmed;
    }

    private static String extractSimpleNameAndRecordImport(String typeFqn, String currentPackage, Set<String> imports) {
        int lastDot = typeFqn.lastIndexOf('.');
        if (lastDot > 0) {
            String pkg = typeFqn.substring(0, lastDot);
            String simple = typeFqn.substring(lastDot + 1);
            if (!"java.lang".equals(pkg) && !pkg.equals(currentPackage)) { //NOI18N
                imports.add(typeFqn);
            }
            return simple;
        }
        return typeFqn;
    }

    static List<EntityInfo> discoverProjectEntities(Project project) {
        if (project == null) {
            return Collections.emptyList();
        }
        final Map<String, EntityInfo> discovered = new LinkedHashMap<String, EntityInfo>();
        SourceGroup[] groups = SourceGroups.getJavaSourceGroups(project);
        if (groups == null) {
            return Collections.emptyList();
        }
        for (SourceGroup sg : groups) {
            FileObject root = sg.getRootFolder();
            if (root == null) {
                continue;
            }
            JavaSource js = JavaSource.create(ClasspathInfo.create(root));
            if (js == null) {
                continue;
            }
            try {
                js.runUserActionTask(new Task<CompilationController>() {
                    @Override
                    public void run(CompilationController cc) throws Exception {
                        cc.toPhase(JavaSource.Phase.ELEMENTS_RESOLVED);
                        for (String entityAnnName : ENTITY_ANNOTATIONS) {
                            TypeElement entityAnnType = cc.getElements().getTypeElement(entityAnnName);
                            if (entityAnnType == null) {
                                continue;
                            }
                            Set<ElementHandle<TypeElement>> handles = cc.getClasspathInfo().getClassIndex().getElements(
                                    ElementHandle.create(entityAnnType),
                                    EnumSet.of(ClassIndex.SearchKind.TYPE_REFERENCES),
                                    EnumSet.of(ClassIndex.SearchScope.SOURCE));
                            if (handles == null) {
                                continue;
                            }
                            for (ElementHandle<TypeElement> handle : handles) {
                                TypeElement te = handle.resolve(cc);
                                if (te != null && hasAnnotation(te, entityAnnType)) {
                                    String fqn = te.getQualifiedName().toString();
                                    if (!discovered.containsKey(fqn)) {
                                        discovered.put(fqn, inspectEntityElement(cc, te));
                                    }
                                }
                            }
                        }
                    }
                }, true);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return new ArrayList<EntityInfo>(discovered.values());
    }

    private static boolean hasAnnotation(Element el, TypeElement annType) {
        for (AnnotationMirror am : el.getAnnotationMirrors()) {
            if (annType.equals(am.getAnnotationType().asElement())) {
                return true;
            }
        }
        return false;
    }

    private static EntityInfo inspectEntityElement(CompilationController cc, TypeElement entity) {
        String idType = null;
        String samplePropName = null;
        String samplePropType = null;

        List<TypeElement> idAnnTypes = new ArrayList<TypeElement>();
        for (String idAnnName : ID_ANNOTATIONS) {
            TypeElement te = cc.getElements().getTypeElement(idAnnName);
            if (te != null) {
                idAnnTypes.add(te);
            }
        }

        for (VariableElement field : ElementFilter.fieldsIn(cc.getElements().getAllMembers(entity))) {
            Set<Modifier> mods = field.getModifiers();
            if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.TRANSIENT)) {
                continue;
            }
            if (field.asType().getKind() == TypeKind.ERROR) {
                continue;
            }
            boolean isIdField = false;
            for (TypeElement idAnnType : idAnnTypes) {
                if (hasAnnotation(field, idAnnType)) {
                    isIdField = true;
                    break;
                }
            }
            String fieldTypeFqn = boxPrimitiveTypeName(
                    cc.getTypeUtilities().getTypeName(field.asType(), TypeUtilities.TypeNameOptions.PRINT_FQN).toString());
            if (isIdField && idType == null) {
                idType = fieldTypeFqn;
            } else if (!isIdField && samplePropName == null && !field.asType().getKind().isPrimitive()
                    && !fieldTypeFqn.startsWith("java.util.")) { //NOI18N
                samplePropName = field.getSimpleName().toString();
                samplePropType = fieldTypeFqn;
            }
        }
        if (idType == null) {
            idType = "java.lang.Long"; //NOI18N
        }
        return new EntityInfo(entity.getQualifiedName().toString(), idType, samplePropName, samplePropType);
    }

    @Override
    public void initialize(WizardDescriptor wizard) {
        this.wizard = wizard;
        if (Templates.getTargetName(wizard) == null) {
            Templates.setTargetName(wizard, DEFAULT_TARGET_NAME);
        }
        if (Templates.getTargetFolder(wizard) == null) {
            Project project = Templates.getProject(wizard);
            if (project != null) {
                SourceGroup[] groups = SourceGroups.getJavaSourceGroups(project);
                if (groups != null && groups.length > 0) {
                    Templates.setTargetFolder(wizard, groups[0].getRootFolder());
                }
            }
        }
    }

    @Override
    public void uninitialize(WizardDescriptor wizard) {
        panels = null;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return getPanels()[index];
    }

    @Override
    public String name() {
        return (index + 1) + ". from " + getPanels().length; //NOI18N
    }

    @Override
    public boolean hasNext() {
        return index < getPanels().length - 1;
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    private String[] createSteps() {
        String[] beforeSteps = null;
        Object prop = wizard.getProperty(WizardDescriptor.PROP_CONTENT_DATA);
        if (prop instanceof String[]) {
            beforeSteps = (String[]) prop;
        }
        if (beforeSteps == null) {
            beforeSteps = new String[0];
        }
        String[] res = new String[(beforeSteps.length - 1) + panels.length];
        for (int i = 0; i < res.length; i++) {
            if (i < (beforeSteps.length - 1)) {
                res[i] = beforeSteps[i];
            } else {
                res[i] = panels[i - beforeSteps.length + 1].getComponent().getName();
            }
        }
        return res;
    }
}
