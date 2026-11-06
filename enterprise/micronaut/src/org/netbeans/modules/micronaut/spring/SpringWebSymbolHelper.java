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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Isolated helper for indexing Spring Web REST controller endpoints and stereotypes
 * in {@link org.netbeans.modules.micronaut.symbol.MicronautSymbolFinder}.
 */
public final class SpringWebSymbolHelper {

    public static final String SPRING_COMPONENT_ANNOTATION = "org.springframework.stereotype.Component";
    public static final String SPRING_CONTROLLER_ANNOTATION = "org.springframework.stereotype.Controller";
    public static final String SPRING_REST_CONTROLLER_ANNOTATION = "org.springframework.web.bind.annotation.RestController";
    public static final String SPRING_REQUEST_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping";
    public static final String SPRING_GET_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.GetMapping";
    public static final String SPRING_POST_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.PostMapping";
    public static final String SPRING_PUT_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.PutMapping";
    public static final String SPRING_DELETE_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.DeleteMapping";
    public static final String SPRING_PATCH_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.PatchMapping";

    private SpringWebSymbolHelper() {
    }

    /** Returns the declared HTTP methods, or ANY for an unrestricted mapping. */
    public static String getEndpointMethod(AnnotationMirror annotation) {
        TypeElement type = (TypeElement) annotation.getAnnotationType().asElement();
        if (!SPRING_REQUEST_MAPPING_ANNOTATION.contentEquals(type.getQualifiedName())) {
            return getEndpointMethod(type);
        }
        List<String> methods = new ArrayList<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
            if ("method".contentEquals(entry.getKey().getSimpleName())) {
                Object value = entry.getValue().getValue();
                if (value instanceof List<?>) {
                    for (Object item : (List<?>) value) {
                        Object method = ((AnnotationValue) item).getValue();
                        if (method instanceof Element) {
                            String name = ((Element) method).getSimpleName().toString();
                            if (!methods.contains(name)) {
                                methods.add(name);
                            }
                        }
                    }
                }
            }
        }
        return methods.isEmpty() ? "ANY" : String.join(", ", methods);
    }

    public static String getEndpointMethod(TypeElement te) {
        if (te == null) {
            return null;
        }
        switch (te.getQualifiedName().toString()) {
            case SPRING_GET_MAPPING_ANNOTATION:
                return "GET";
            case SPRING_POST_MAPPING_ANNOTATION:
                return "POST";
            case SPRING_PUT_MAPPING_ANNOTATION:
                return "PUT";
            case SPRING_DELETE_MAPPING_ANNOTATION:
                return "DELETE";
            case SPRING_PATCH_MAPPING_ANNOTATION:
                return "PATCH";
            case SPRING_REQUEST_MAPPING_ANNOTATION:
                return "ANY";
            default:
                return null;
        }
    }

    public static String getControllerPath(AnnotationMirror ann) {
        if (ann == null) {
            return null;
        }
        Element annEl = ann.getAnnotationType().asElement();
        if (!(annEl instanceof TypeElement)) {
            return null;
        }
        switch (((TypeElement) annEl).getQualifiedName().toString()) {
            case SPRING_CONTROLLER_ANNOTATION:
            case SPRING_REST_CONTROLLER_ANNOTATION:
                return "/";
            case SPRING_REQUEST_MAPPING_ANNOTATION:
                String path = "/";
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                    CharSequence keyName = entry.getKey().getSimpleName();
                    if ("value".contentEquals(keyName) || "path".contentEquals(keyName)) {
                        List<String> extracted = new ArrayList<>();
                        extractAnnotationStrings(entry.getValue(), extracted);
                        if (!extracted.isEmpty()) {
                            path = extracted.get(0);
                        }
                    }
                }
                return path.startsWith("/") ? path : "/" + path;
            default:
                return null;
        }
    }

    public static String getClassRequestMappingPath(TypeElement te) {
        if (te == null) {
            return null;
        }
        for (AnnotationMirror ann : te.getAnnotationMirrors()) {
            Element annEl = ann.getAnnotationType().asElement();
            if (annEl instanceof TypeElement && SPRING_REQUEST_MAPPING_ANNOTATION.contentEquals(((TypeElement) annEl).getQualifiedName())) {
                return getControllerPath(ann);
            }
        }
        return null;
    }

    public static void extractPathIds(AnnotationMirror ann, List<String> ids) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
            if ("path".contentEquals(entry.getKey().getSimpleName())
                    || "value".contentEquals(entry.getKey().getSimpleName())) {
                Object val = entry.getValue().getValue();
                if (val instanceof List<?>) {
                    extractAnnotationStrings(entry.getValue(), ids);
                } else if ("path".contentEquals(entry.getKey().getSimpleName()) && val instanceof String) {
                    ids.add((String) val);
                }
            }
        }
    }

    public static void extractAnnotationStrings(AnnotationValue av, List<String> target) {
        if (av == null) {
            return;
        }
        Object val = av.getValue();
        if (val instanceof String) {
            target.add((String) val);
        } else if (val instanceof List<?>) {
            for (Object item : (List<?>) val) {
                if (item instanceof AnnotationValue) {
                    Object inner = ((AnnotationValue) item).getValue();
                    if (inner instanceof String) {
                        target.add((String) inner);
                    }
                }
            }
        }
    }

    public static String joinPaths(String basePath, Object subPathObj) {
        String subPath = String.valueOf(subPathObj);
        if (basePath.endsWith("/") && subPath.startsWith("/")) {
            return basePath + subPath.substring(1);
        } else if (!basePath.endsWith("/") && !subPath.isEmpty() && !subPath.startsWith("/")) {
            return basePath + "/" + subPath;
        }
        return basePath + subPath;
    }
}
