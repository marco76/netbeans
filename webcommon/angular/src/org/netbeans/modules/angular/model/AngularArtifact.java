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
package org.netbeans.modules.angular.model;

import java.util.Collections;
import java.util.List;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.openide.filesystems.FileObject;

/**
 * Represents a discovered Angular artifact (@Component, @Directive, @Pipe, @Injectable, @NgModule)
 * or TypeScript DTO interface/class, along with companion files, bindings, and HTTP call sites.
 */
public final class AngularArtifact {

    public enum Kind {
        COMPONENT,
        DIRECTIVE,
        PIPE,
        SERVICE,
        MODULE,
        DTO_INTERFACE
    }

    private final Kind kind;
    private final String className;
    private final String selector;
    private final boolean standalone;
    private final FileObject sourceFile;
    private final int classNameStartOffset;
    private final int classNameEndOffset;
    private final int selectorStartOffset;
    private final int selectorEndOffset;
    private final String templateUrl;
    private final int templateUrlStartOffset;
    private final int templateUrlEndOffset;
    private final String inlineTemplate;
    private final List<String> styleUrls;
    private final FileObject templateFile;
    private final List<FileObject> styleFiles;
    private final FileObject specFile;
    private final List<PropertyBinding> inputs;
    private final List<PropertyBinding> outputs;
    private final List<HttpCallSite> httpCalls;
    private final List<TsDtoField> dtoFields;

    public AngularArtifact(
            Kind kind,
            String className,
            String selector,
            boolean standalone,
            FileObject sourceFile,
            int classNameStartOffset,
            int classNameEndOffset,
            int selectorStartOffset,
            int selectorEndOffset,
            String templateUrl,
            int templateUrlStartOffset,
            int templateUrlEndOffset,
            String inlineTemplate,
            List<String> styleUrls,
            FileObject templateFile,
            List<FileObject> styleFiles,
            FileObject specFile,
            List<PropertyBinding> inputs,
            List<PropertyBinding> outputs,
            List<HttpCallSite> httpCalls,
            List<TsDtoField> dtoFields) {
        this.kind = kind;
        this.className = className;
        this.selector = selector;
        this.standalone = standalone;
        this.sourceFile = sourceFile;
        this.classNameStartOffset = classNameStartOffset;
        this.classNameEndOffset = classNameEndOffset;
        this.selectorStartOffset = selectorStartOffset;
        this.selectorEndOffset = selectorEndOffset;
        this.templateUrl = templateUrl;
        this.templateUrlStartOffset = templateUrlStartOffset;
        this.templateUrlEndOffset = templateUrlEndOffset;
        this.inlineTemplate = inlineTemplate;
        this.styleUrls = styleUrls != null ? Collections.unmodifiableList(styleUrls) : Collections.emptyList();
        this.templateFile = templateFile;
        this.styleFiles = styleFiles != null ? Collections.unmodifiableList(styleFiles) : Collections.emptyList();
        this.specFile = specFile;
        this.inputs = inputs != null ? Collections.unmodifiableList(inputs) : Collections.emptyList();
        this.outputs = outputs != null ? Collections.unmodifiableList(outputs) : Collections.emptyList();
        this.httpCalls = httpCalls != null ? Collections.unmodifiableList(httpCalls) : Collections.emptyList();
        this.dtoFields = dtoFields != null ? Collections.unmodifiableList(dtoFields) : Collections.emptyList();
    }

    public @NonNull Kind getKind() {
        return kind;
    }

    public @NonNull String getClassName() {
        return className;
    }

    public @CheckForNull String getSelector() {
        return selector;
    }

    public boolean isStandalone() {
        return standalone;
    }

    public @NonNull FileObject getSourceFile() {
        return sourceFile;
    }

    public int getClassNameStartOffset() {
        return classNameStartOffset;
    }

    public int getClassNameEndOffset() {
        return classNameEndOffset;
    }

    public int getSelectorStartOffset() {
        return selectorStartOffset;
    }

    public int getSelectorEndOffset() {
        return selectorEndOffset;
    }

    public @CheckForNull String getTemplateUrl() {
        return templateUrl;
    }

    public int getTemplateUrlStartOffset() {
        return templateUrlStartOffset;
    }

    public int getTemplateUrlEndOffset() {
        return templateUrlEndOffset;
    }

    public @CheckForNull String getInlineTemplate() {
        return inlineTemplate;
    }

    public @NonNull List<String> getStyleUrls() {
        return styleUrls;
    }

    public @CheckForNull FileObject getTemplateFile() {
        return templateFile;
    }

    public @NonNull List<FileObject> getStyleFiles() {
        return styleFiles;
    }

    public @CheckForNull FileObject getSpecFile() {
        return specFile;
    }

    public @NonNull List<PropertyBinding> getInputs() {
        return inputs;
    }

    public @NonNull List<PropertyBinding> getOutputs() {
        return outputs;
    }

    public @NonNull List<HttpCallSite> getHttpCalls() {
        return httpCalls;
    }

    public @NonNull List<TsDtoField> getDtoFields() {
        return dtoFields;
    }

    public @NonNull String getDisplayName() {
        if (selector != null && !selector.isEmpty()) {
            if (kind == Kind.COMPONENT) {
                return className + " <" + selector + ">"; // NOI18N
            } else if (kind == Kind.PIPE) {
                return className + " (| " + selector + ")"; // NOI18N
            }
            return className + " [" + selector + "]"; // NOI18N
        }
        return className;
    }

    /**
     * Represents an @Input() or @Output() property on an Angular component/directive.
     */
    public static final class PropertyBinding {
        private final String propertyName;
        private final String alias;
        private final String type;
        private final boolean input;
        private final int startOffset;
        private final int endOffset;

        public PropertyBinding(String propertyName, String alias, String type, boolean input, int startOffset, int endOffset) {
            this.propertyName = propertyName;
            this.alias = alias != null && !alias.isEmpty() ? alias : propertyName;
            this.type = type;
            this.input = input;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getAlias() {
            return alias;
        }

        public String getType() {
            return type;
        }

        public boolean isInput() {
            return input;
        }

        public int getStartOffset() {
            return startOffset;
        }

        public int getEndOffset() {
            return endOffset;
        }
    }

    /**
     * Represents an Angular HttpClient or fetch REST call site in a TypeScript file.
     */
    public static final class HttpCallSite {
        private final FileObject file;
        private final String enclosingClassName;
        private final String enclosingMethodName;
        private final String httpMethod;
        private final String rawUrl;
        private final String normalizedPath;
        private final String responseDtoType;
        private final int urlStartOffset;
        private final int urlEndOffset;
        private final int lineNumber;

        public HttpCallSite(
                FileObject file,
                String enclosingClassName,
                String enclosingMethodName,
                String httpMethod,
                String rawUrl,
                String normalizedPath,
                String responseDtoType,
                int urlStartOffset,
                int urlEndOffset,
                int lineNumber) {
            this.file = file;
            this.enclosingClassName = enclosingClassName;
            this.enclosingMethodName = enclosingMethodName;
            this.httpMethod = httpMethod;
            this.rawUrl = rawUrl;
            this.normalizedPath = normalizedPath;
            this.responseDtoType = responseDtoType;
            this.urlStartOffset = urlStartOffset;
            this.urlEndOffset = urlEndOffset;
            this.lineNumber = lineNumber;
        }

        public FileObject getFile() {
            return file;
        }

        public String getEnclosingClassName() {
            return enclosingClassName;
        }

        public String getEnclosingMethodName() {
            return enclosingMethodName;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public String getRawUrl() {
            return rawUrl;
        }

        public String getNormalizedPath() {
            return normalizedPath;
        }

        public @CheckForNull String getResponseDtoType() {
            return responseDtoType;
        }

        public int getUrlStartOffset() {
            return urlStartOffset;
        }

        public int getUrlEndOffset() {
            return urlEndOffset;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }

    /**
     * Represents a field in a TypeScript DTO interface/class.
     */
    public static final class TsDtoField {
        private final String name;
        private final String type;
        private final int startOffset;
        private final int endOffset;

        public TsDtoField(String name, String type, int startOffset, int endOffset) {
            this.name = name;
            this.type = type;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public int getStartOffset() {
            return startOffset;
        }

        public int getEndOffset() {
            return endOffset;
        }
    }
}
