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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.modules.j2ee.core.api.support.SourceGroups;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.EnableAnnotation;
import org.netbeans.modules.spring.beans.wizards.SpringModuleCatalog.SpringModule;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;

/**
 * Wizard iterator for creating a Spring Java {@code @Configuration} class and
 * importing/configuring Spring Framework or Spring Boot modules in the project.
 */
public final class NewSpringJavaConfigWizardIterator implements WizardDescriptor.AsynchronousInstantiatingIterator<WizardDescriptor> {

    private static final String DEFAULT_TARGET_NAME = "SpringConfig"; //NOI18N

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
                new SpringJavaConfigPanel()
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
    @SuppressWarnings("unchecked")
    public Set<FileObject> instantiate() throws IOException {
        Project project = Templates.getProject(wizard);
        FileObject targetFolder = Templates.getTargetFolder(wizard);
        String targetName = Templates.getTargetName(wizard);
        if (targetName == null || targetName.trim().isEmpty()) {
            targetName = DEFAULT_TARGET_NAME;
        }

        Set<SpringModule> selectedModules = (Set<SpringModule>) wizard.getProperty(SpringJavaConfigPanel.SELECTED_MODULES);
        Boolean springBootMode = (Boolean) wizard.getProperty(SpringJavaConfigPanel.SPRING_BOOT_MODE);
        String springVersion = (String) wizard.getProperty(SpringJavaConfigPanel.SPRING_VERSION);
        Boolean addAntLibrary = (Boolean) wizard.getProperty(SpringJavaConfigPanel.ADD_SPRING_LIBRARY);
        Library antLibrary = Boolean.TRUE.equals(addAntLibrary)
                ? (Library) wizard.getProperty(SpringJavaConfigPanel.SPRING_LIBRARY)
                : null;

        FileObject sourceArtifact = NewSpringXMLConfigWizardIterator.getSourceGroupArtifact(project, targetFolder);
        ProjectSpringDependencyModifier.addSpringModules(
                project,
                sourceArtifact,
                selectedModules != null ? selectedModules : Collections.emptySet(),
                springBootMode == null || springBootMode,
                springVersion,
                antLibrary);

        String packageName = resolvePackageName(project, targetFolder);
        boolean componentScan = Boolean.TRUE.equals(wizard.getProperty(SpringJavaConfigPanel.COMPONENT_SCAN));
        String componentScanPackages = (String) wizard.getProperty(SpringJavaConfigPanel.COMPONENT_SCAN_PACKAGES);
        boolean propertySourceEnabled = Boolean.TRUE.equals(wizard.getProperty(SpringJavaConfigPanel.PROPERTY_SOURCE_ENABLED));
        String propertySource = propertySourceEnabled ? (String) wizard.getProperty(SpringJavaConfigPanel.PROPERTY_SOURCE) : null;
        boolean importConfigsEnabled = Boolean.TRUE.equals(wizard.getProperty(SpringJavaConfigPanel.IMPORT_CONFIGS_ENABLED));
        String importConfigs = importConfigsEnabled ? (String) wizard.getProperty(SpringJavaConfigPanel.IMPORT_CONFIGS) : null;
        Set<EnableAnnotation> enableAnnotations = (Set<EnableAnnotation>) wizard.getProperty(SpringJavaConfigPanel.ENABLE_ANNOTATIONS);
        boolean generateSampleBean = Boolean.TRUE.equals(wizard.getProperty(SpringJavaConfigPanel.GENERATE_SAMPLE_BEAN));

        Map<String, Object> params = buildTemplateParameters(
                packageName,
                componentScan,
                componentScanPackages,
                propertySource,
                importConfigs,
                enableAnnotations,
                generateSampleBean);

        DataFolder dataFolder = DataFolder.findFolder(targetFolder);
        FileObject templateFO = Templates.getTemplate(wizard);
        DataObject templateDO = DataObject.find(templateFO);
        FileObject createdFile = templateDO.createFromTemplate(dataFolder, targetName, params).getPrimaryFile();
        return Collections.singleton(createdFile);
    }

    static Map<String, Object> buildTemplateParameters(
            String packageName,
            boolean componentScan,
            String componentScanPackages,
            String propertySource,
            String importConfigs,
            Collection<EnableAnnotation> enableAnnotations,
            boolean generateSampleBean) {
        Map<String, Object> params = new HashMap<>();
        Set<String> imports = new TreeSet<>();
        imports.add("org.springframework.context.annotation.Configuration"); //NOI18N

        if (packageName != null && !packageName.isEmpty()) {
            params.put("package", packageName); //NOI18N
        }

        params.put("componentScan", componentScan); //NOI18N
        if (componentScan) {
            imports.add("org.springframework.context.annotation.ComponentScan"); //NOI18N
            String formattedPkgs = formatStringArrayAttribute(componentScanPackages, packageName);
            if (!formattedPkgs.isEmpty()) {
                params.put("componentScanValue", formattedPkgs); //NOI18N
            }
        }

        if (propertySource != null && !propertySource.trim().isEmpty()) {
            imports.add("org.springframework.context.annotation.PropertySource"); //NOI18N
            String ps = propertySource.trim();
            if (!ps.startsWith("\"")) { //NOI18N
                ps = "\"" + ps + "\""; //NOI18N
            }
            params.put("propertySourceValue", ps); //NOI18N
        }

        List<String> importedClassLiterals = parseClassList(importConfigs, packageName, imports);
        if (!importedClassLiterals.isEmpty()) {
            imports.add("org.springframework.context.annotation.Import"); //NOI18N
            if (importedClassLiterals.size() == 1) {
                params.put("importClassesValue", importedClassLiterals.get(0)); //NOI18N
            } else {
                params.put("importClassesValue", "{" + String.join(", ", importedClassLiterals) + "}"); //NOI18N
            }
        }

        List<String> enableNames = new ArrayList<>();
        if (enableAnnotations != null) {
            for (EnableAnnotation ea : enableAnnotations) {
                imports.add(ea.getFqn());
                enableNames.add(ea.getSimpleName());
            }
        }
        if (!enableNames.isEmpty()) {
            params.put("enableAnnotations", enableNames); //NOI18N
        }

        params.put("generateSampleBean", generateSampleBean); //NOI18N
        params.put("imports", new ArrayList<>(imports)); //NOI18N
        return params;
    }

    private static String formatStringArrayAttribute(String rawPackages, String currentPackage) {
        if (rawPackages == null || rawPackages.trim().isEmpty()) {
            return ""; //NOI18N
        }
        String[] parts = rawPackages.split("[,;\\s]+"); //NOI18N
        List<String> valid = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                valid.add(trimmed);
            }
        }
        if (valid.isEmpty()) {
            return ""; //NOI18N
        }
        if (valid.size() == 1 && valid.get(0).equals(currentPackage)) {
            return ""; //NOI18N
        }
        if (valid.size() == 1) {
            return "\"" + valid.get(0) + "\""; //NOI18N
        }
        List<String> quoted = new ArrayList<>(valid.size());
        for (String v : valid) {
            quoted.add("\"" + v + "\""); //NOI18N
        }
        return "basePackages = {" + String.join(", ", quoted) + "}"; //NOI18N
    }

    private static List<String> parseClassList(String rawClasses, String currentPackage, Set<String> imports) {
        if (rawClasses == null || rawClasses.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] tokens = rawClasses.split("[,;\\s]+"); //NOI18N
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.endsWith(".class")) { //NOI18N
                t = t.substring(0, t.length() - ".class".length()).trim(); //NOI18N
            }
            int lastDot = t.lastIndexOf('.');
            if (lastDot > 0) {
                String pkg = t.substring(0, lastDot);
                String simple = t.substring(lastDot + 1);
                if (!pkg.equals(currentPackage)) {
                    imports.add(t);
                }
                result.add(simple + ".class"); //NOI18N
            } else {
                result.add(t + ".class"); //NOI18N
            }
        }
        return new ArrayList<>(result);
    }

    static String resolvePackageName(Project project, FileObject targetFolder) {
        if (project == null || targetFolder == null) {
            return ""; //NOI18N
        }
        SourceGroup[] groups = SourceGroups.getJavaSourceGroups(project);
        if (groups != null) {
            for (SourceGroup sg : groups) {
                FileObject root = sg.getRootFolder();
                if (root != null && (root.equals(targetFolder) || FileUtil.isParentOf(root, targetFolder))) {
                    String rel = FileUtil.getRelativePath(root, targetFolder);
                    return rel != null ? rel.replace('/', '.') : ""; //NOI18N
                }
            }
        }
        return ""; //NOI18N
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
