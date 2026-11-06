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
package org.netbeans.modules.spring.api.beans.model;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.netbeans.modules.j2ee.metadata.model.support.TestUtilities;

/**
 * Isolated unit tests for {@code @Configuration} and {@code @Bean} method discovery.
 */
public class SpringModernAnnotationsTest extends CommonAnnotationTestCase {

    public SpringModernAnnotationsTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        URL location = org.springframework.stereotype.Component.class.getProtectionDomain().getCodeSource().getLocation();
        addCompileRoots(Collections.singletonList(new URL("jar:" + location.toString() + "!/")));
    }

    public void testSpringConfigurationAndBeanMethods() throws IOException, InterruptedException {
        TestUtilities.copyStringToFileObject(srcFO, "foo/AppConfig.java",
                "package foo; "
                + "import org.springframework.context.annotation.Bean; "
                + "import org.springframework.context.annotation.Configuration; "
                + "@Configuration "
                + "public class AppConfig {"
                + "    @Bean "
                + "    public String defaultNamedBean() { return \"hello\"; }"
                + "    @Bean(\"customBeanName\") "
                + "    public Integer customNamedBean() { return 42; }"
                + "}");

        List<SpringBean> beans = getAnnotatedBeans(createSpringModel());
        assertEquals(3, beans.size());
    }

    public void testSpringDataRepositoryBeanDiscovery() throws IOException, InterruptedException {
        TestUtilities.copyStringToFileObject(srcFO, "org/springframework/data/repository/Repository.java",
                "package org.springframework.data.repository; "
                + "public interface Repository<T, ID> {}");
        TestUtilities.copyStringToFileObject(srcFO, "org/springframework/data/repository/NoRepositoryBean.java",
                "package org.springframework.data.repository; "
                + "public @interface NoRepositoryBean {}");
        TestUtilities.copyStringToFileObject(srcFO, "org/springframework/data/repository/RepositoryDefinition.java",
                "package org.springframework.data.repository; "
                + "public @interface RepositoryDefinition { Class<?> domainClass(); Class<?> idClass(); }");
        TestUtilities.copyStringToFileObject(srcFO, "org/springframework/data/repository/CrudRepository.java",
                "package org.springframework.data.repository; "
                + "@NoRepositoryBean "
                + "public interface CrudRepository<T, ID> extends Repository<T, ID> {}");
        TestUtilities.copyStringToFileObject(srcFO, "foo/BaseAppRepository.java",
                "package foo; "
                + "import org.springframework.data.repository.CrudRepository; "
                + "import org.springframework.data.repository.NoRepositoryBean; "
                + "@NoRepositoryBean "
                + "public interface BaseAppRepository<T, ID> extends CrudRepository<T, ID> {}");
        TestUtilities.copyStringToFileObject(srcFO, "foo/UserRepository.java",
                "package foo; "
                + "public interface UserRepository extends BaseAppRepository<String, Long> {}");
        TestUtilities.copyStringToFileObject(srcFO, "foo/AuditRepository.java",
                "package foo; "
                + "import org.springframework.data.repository.RepositoryDefinition; "
                + "@RepositoryDefinition(domainClass = String.class, idClass = Long.class) "
                + "public interface AuditRepository {}");

        List<SpringBean> beans = getAnnotatedBeans(createSpringModel());
        assertEquals(2, beans.size());
    }
}
