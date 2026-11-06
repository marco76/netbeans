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
package org.netbeans.modules.spring.beans.hyperlink;

import java.awt.Toolkit;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.netbeans.modules.editor.NbEditorUtilities;
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
import org.netbeans.modules.spring.util.SpringBeansUIs;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 * Provides Ctrl+Click (Go To Declaration) navigation from Java bean reference annotations
 * ({@code @Qualifier}, {@code @Resource(name = ...)}, {@code @DependsOn}, {@code @Named})
 * to the corresponding Spring bean definition (Java class, {@code @Bean} method, or XML configuration).
 */
@MimeRegistration(mimeType = "text/x-java", service = HyperlinkProviderExt.class, position = 1260)
public class SpringJavaBeanHyperlinkProvider implements HyperlinkProviderExt {

    private static final Set<String> BEAN_ANNOTATIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "Qualifier",
            "org.springframework.beans.factory.annotation.Qualifier",
            "DependsOn",
            "org.springframework.context.annotation.DependsOn",
            "Resource",
            "javax.annotation.Resource",
            "jakarta.annotation.Resource",
            "Named",
            "javax.inject.Named",
            "jakarta.inject.Named"
    )));

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        return getHyperlinkSpan(doc, offset, type) != null;
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        BeanRefToken ref = findBeanRefAt(doc, offset);
        return ref != null ? new int[]{ref.startOffset, ref.endOffset} : null;
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        BeanRefToken ref = findBeanRefAt(doc, offset);
        if (ref == null) {
            return;
        }
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo == null) {
            return;
        }
        SpringBean bean = findSpringBean(fo, ref.beanName);
        if (bean != null) {
            SpringBeansUIs.GoToBeanAction action = SpringBeansUIs.createGoToBeanAction(bean);
            if (action != null) {
                action.invoke();
                return;
            }
        }
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        BeanRefToken ref = findBeanRefAt(doc, offset);
        if (ref == null) {
            return null;
        }
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo == null) {
            return null;
        }
        SpringBean bean = findSpringBean(fo, ref.beanName);
        if (bean != null && bean.getClassName() != null) {
            return bean.getClassName();
        }
        return null;
    }

    private static BeanRefToken findBeanRefAt(final Document doc, final int offset) {
        final AtomicReference<BeanRefToken> result = new AtomicReference<BeanRefToken>();
        doc.render(new Runnable() {
            @Override
            public void run() {
                TokenHierarchy<?> th = TokenHierarchy.get(doc);
                if (th == null) {
                    return;
                }
                TokenSequence<JavaTokenId> ts = th.tokenSequence(JavaTokenId.language());
                if (ts == null) {
                    return;
                }
                ts.move(offset);
                if (!ts.moveNext()) {
                    return;
                }
                Token<JavaTokenId> token = ts.token();
                if (token.id() != JavaTokenId.STRING_LITERAL) {
                    return;
                }
                String raw = token.text().toString();
                if (raw.length() <= 2 || !raw.startsWith("\"") || !raw.endsWith("\"")) { //NOI18N
                    return;
                }
                int start = ts.offset() + 1;
                int end = ts.offset() + token.length() - 1;
                if (offset < start || offset > end) {
                    return;
                }
                String beanName = raw.substring(1, raw.length() - 1);
                if (beanName.isEmpty()) {
                    return;
                }

                // Walk backwards inside the annotation to verify this string literal is inside a supported bean annotation
                String attrName = null;
                boolean seenLParen = false;
                StringBuilder annNameBuilder = new StringBuilder();
                int steps = 0;
                while (ts.movePrevious() && steps++ < 25) {
                    JavaTokenId id = ts.token().id();
                    if (id == JavaTokenId.WHITESPACE || id == JavaTokenId.BLOCK_COMMENT || id == JavaTokenId.LINE_COMMENT) {
                        continue;
                    }
                    if (!seenLParen) {
                        if (id == JavaTokenId.EQ) {
                            while (ts.movePrevious() && steps++ < 25) {
                                JavaTokenId prevId = ts.token().id();
                                if (prevId == JavaTokenId.WHITESPACE) {
                                    continue;
                                }
                                if (prevId == JavaTokenId.IDENTIFIER) {
                                    attrName = ts.token().text().toString();
                                }
                                break;
                            }
                        } else if (id == JavaTokenId.LPAREN) {
                            seenLParen = true;
                        } else if (id == JavaTokenId.LBRACE || id == JavaTokenId.COMMA || id == JavaTokenId.STRING_LITERAL) {
                            // array initializer inside annotation, e.g. @DependsOn({"a", "b"})
                            continue;
                        } else {
                            return;
                        }
                    } else {
                        if (id == JavaTokenId.IDENTIFIER || id == JavaTokenId.DOT) {
                            annNameBuilder.insert(0, ts.token().text().toString());
                        } else if (id == JavaTokenId.AT) {
                            String annName = annNameBuilder.toString();
                            if (BEAN_ANNOTATIONS.contains(annName)) {
                                if (annName.endsWith("Resource") && attrName != null && !"name".equals(attrName)) { //NOI18N
                                    return;
                                }
                                if (!annName.endsWith("Resource") && attrName != null && !"value".equals(attrName)) { //NOI18N
                                    return;
                                }
                                result.set(new BeanRefToken(beanName, start, end));
                            }
                            return;
                        } else {
                            return;
                        }
                    }
                }
            }
        });
        return result.get();
    }

    private static SpringBean findSpringBean(FileObject sourceFo, final String beanName) {
        final AtomicReference<SpringBean> found = new AtomicReference<SpringBean>();
        SpringScope scope = SpringScope.getSpringScope(sourceFo);
        if (scope != null) {
            for (SpringConfigModel configModel : scope.getAllConfigModels()) {
                try {
                    configModel.runDocumentAction(new Action<DocumentAccess>() {
                        @Override
                        public void run(DocumentAccess docAccess) {
                            if (found.get() != null) {
                                return;
                            }
                            FileObject fo = docAccess.getFileObject();
                            if (fo == null) {
                                return;
                            }
                            FileSpringBeans fileBeans = docAccess.getSpringBeans().getFileBeans(fo);
                            if (fileBeans != null) {
                                SpringBean bean = fileBeans.findBean(beanName);
                                if (bean != null) {
                                    found.set(bean);
                                }
                            }
                        }
                    });
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                if (found.get() != null) {
                    return found.get();
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
                            if (beanName.equals(bean.getId()) || (bean.getNames() != null && bean.getNames().contains(beanName))) {
                                found.set(bean);
                                break;
                            }
                        }
                        return null;
                    }
                });
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return found.get();
    }

    private static final class BeanRefToken {
        final String beanName;
        final int startOffset;
        final int endOffset;

        BeanRefToken(String beanName, int startOffset, int endOffset) {
            this.beanName = beanName;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }
}
