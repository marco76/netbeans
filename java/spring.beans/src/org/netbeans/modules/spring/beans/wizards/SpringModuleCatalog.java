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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Catalog of Spring Framework and Spring Boot modules/starters and {@code @Enable*}
 * configuration annotations used by the Spring configuration and project wizards.
 */
public final class SpringModuleCatalog {

    public static final String CAT_CORE = "Core & Context"; //NOI18N
    public static final String CAT_WEB = "Web & MVC"; //NOI18N
    public static final String CAT_DATA = "SQL & Data Persistence"; //NOI18N
    public static final String CAT_SECURITY = "Security"; //NOI18N
    public static final String CAT_MESSAGING = "Messaging & Integration"; //NOI18N
    public static final String CAT_OPS = "Ops, Cache & Testing"; //NOI18N

    private static final List<SpringModule> MODULES = Collections.unmodifiableList(Arrays.asList(
            // Core & Context
            new SpringModule(
                    "context",
                    "Spring Context & Core",
                    CAT_CORE,
                    "Core Spring IoC container, dependency injection, @Configuration, and bean lifecycle.",
                    "org.springframework.boot", "spring-boot-starter",
                    "org.springframework", "spring-context",
                    "org.springframework.context.ApplicationContext",
                    null, false),
            new SpringModule(
                    "aop",
                    "Spring AOP & Aspects",
                    CAT_CORE,
                    "Aspect-Oriented Programming with Spring AOP and AspectJ support.",
                    "org.springframework.boot", "spring-boot-starter-aop",
                    "org.springframework", "spring-aop",
                    "org.springframework.context.annotation.EnableAspectJAutoProxy",
                    "org.springframework.context.annotation.EnableAspectJAutoProxy", false),
            new SpringModule(
                    "configuration-processor",
                    "Spring Configuration Processor",
                    CAT_CORE,
                    "Generates metadata for @ConfigurationProperties classes to offer IDE code completion.",
                    "org.springframework.boot", "spring-boot-configuration-processor",
                    "org.springframework.boot", "spring-boot-configuration-processor",
                    "org.springframework.boot.configurationprocessor.ConfigurationMetadataAnnotationProcessor",
                    "org.springframework.boot.context.properties.ConfigurationPropertiesScan", false),
            new SpringModule(
                    "devtools",
                    "Spring Boot DevTools",
                    CAT_CORE,
                    "Provides fast application restarts, LiveReload, and configurations for enhanced development.",
                    "org.springframework.boot", "spring-boot-devtools",
                    "org.springframework.boot", "spring-boot-devtools",
                    "org.springframework.boot.devtools.RemoteSpringApplication",
                    null, false),

            // Web & MVC
            new SpringModule(
                    "web",
                    "Spring Web (MVC / REST)",
                    CAT_WEB,
                    "Build web, including RESTful, applications using Spring MVC.",
                    "org.springframework.boot", "spring-boot-starter-web",
                    "org.springframework", "spring-webmvc",
                    "org.springframework.web.servlet.DispatcherServlet",
                    "org.springframework.web.servlet.config.annotation.EnableWebMvc", false),
            new SpringModule(
                    "webflux",
                    "Spring Reactive Web (WebFlux)",
                    CAT_WEB,
                    "Build reactive web applications with Spring WebFlux and Netty.",
                    "org.springframework.boot", "spring-boot-starter-webflux",
                    "org.springframework", "spring-webflux",
                    "org.springframework.web.reactive.DispatcherHandler",
                    null, false),
            new SpringModule(
                    "validation",
                    "Bean Validation",
                    CAT_WEB,
                    "Bean Validation with Hibernate Validator (jakarta.validation / javax.validation).",
                    "org.springframework.boot", "spring-boot-starter-validation",
                    "org.springframework", "spring-context",
                    "jakarta.validation.Validation",
                    null, false),
            new SpringModule(
                    "websocket",
                    "WebSocket",
                    CAT_WEB,
                    "Build WebSocket applications with SockJS and STOMP messaging.",
                    "org.springframework.boot", "spring-boot-starter-websocket",
                    "org.springframework", "spring-websocket",
                    "org.springframework.web.socket.WebSocketHandler",
                    null, false),

            // SQL & Data Persistence
            new SpringModule(
                    "data-jpa",
                    "Spring Data JPA & ORM",
                    CAT_DATA,
                    "Persist data in SQL stores with Java Persistence API using Spring Data and Hibernate.",
                    "org.springframework.boot", "spring-boot-starter-data-jpa",
                    "org.springframework", "spring-orm",
                    "org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean",
                    "org.springframework.data.jpa.repository.config.EnableJpaRepositories", false),
            new SpringModule(
                    "jdbc",
                    "Spring JDBC",
                    CAT_DATA,
                    "Persist data in SQL stores with plain JDBC using JdbcTemplate and JdbcClient.",
                    "org.springframework.boot", "spring-boot-starter-jdbc",
                    "org.springframework", "spring-jdbc",
                    "org.springframework.jdbc.core.JdbcTemplate",
                    null, false),
            new SpringModule(
                    "tx",
                    "Spring Transactions (TX)",
                    CAT_DATA,
                    "Declarative and programmatic transaction management with @Transactional.",
                    "org.springframework.boot", "spring-boot-starter-jdbc",
                    "org.springframework", "spring-tx",
                    "org.springframework.transaction.PlatformTransactionManager",
                    "org.springframework.transaction.annotation.EnableTransactionManagement", false),
            new SpringModule(
                    "data-redis",
                    "Spring Data Redis",
                    CAT_DATA,
                    "Advanced and thread-safe Java Redis client for synchronous, asynchronous, and reactive usage.",
                    "org.springframework.boot", "spring-boot-starter-data-redis",
                    "org.springframework.data", "spring-data-redis",
                    "org.springframework.data.redis.core.RedisTemplate",
                    null, false),
            new SpringModule(
                    "data-mongodb",
                    "Spring Data MongoDB",
                    CAT_DATA,
                    "Store data in flexible, JSON-like MongoDB documents using Spring Data MongoDB.",
                    "org.springframework.boot", "spring-boot-starter-data-mongodb",
                    "org.springframework.data", "spring-data-mongodb",
                    "org.springframework.data.mongodb.core.MongoTemplate",
                    null, false),

            // Security
            new SpringModule(
                    "security",
                    "Spring Security",
                    CAT_SECURITY,
                    "Highly customizable authentication and access-control framework for Spring applications.",
                    "org.springframework.boot", "spring-boot-starter-security",
                    "org.springframework.security", "spring-security-config",
                    "org.springframework.security.config.annotation.web.configuration.EnableWebSecurity",
                    "org.springframework.security.config.annotation.web.configuration.EnableWebSecurity", false),
            new SpringModule(
                    "oauth2-client",
                    "OAuth2 Client",
                    CAT_SECURITY,
                    "Spring Boot integration for Spring Security's OAuth2/OpenID Connect client features.",
                    "org.springframework.boot", "spring-boot-starter-oauth2-client",
                    "org.springframework.security", "spring-security-oauth2-client",
                    "org.springframework.security.oauth2.client.registration.ClientRegistration",
                    null, false),
            new SpringModule(
                    "oauth2-resource-server",
                    "OAuth2 Resource Server",
                    CAT_SECURITY,
                    "Spring Boot integration for Spring Security's OAuth2 resource server features.",
                    "org.springframework.boot", "spring-boot-starter-oauth2-resource-server",
                    "org.springframework.security", "spring-security-oauth2-resource-server",
                    "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken",
                    null, false),

            // Messaging & Integration
            new SpringModule(
                    "amqp",
                    "Spring for RabbitMQ (AMQP)",
                    CAT_MESSAGING,
                    "Gives your applications a common platform to send and receive messages via AMQP / RabbitMQ.",
                    "org.springframework.boot", "spring-boot-starter-amqp",
                    "org.springframework.amqp", "spring-rabbit",
                    "org.springframework.amqp.rabbit.core.RabbitTemplate",
                    null, false),
            new SpringModule(
                    "kafka",
                    "Spring for Apache Kafka",
                    CAT_MESSAGING,
                    "Publish, subscribe, store, and process streams of records with Apache Kafka.",
                    "org.springframework.kafka", "spring-kafka",
                    "org.springframework.kafka", "spring-kafka",
                    "org.springframework.kafka.core.KafkaTemplate",
                    null, false),
            new SpringModule(
                    "jms",
                    "Spring JMS (ActiveMQ / Artemis)",
                    CAT_MESSAGING,
                    "Java Message Service support via JmsTemplate and @JmsListener.",
                    "org.springframework.boot", "spring-boot-starter-artemis",
                    "org.springframework", "spring-jms",
                    "org.springframework.jms.core.JmsTemplate",
                    null, false),
            new SpringModule(
                    "mail",
                    "Java Mail Sender",
                    CAT_MESSAGING,
                    "Send email using JavaMail and Spring Framework's JavaMailSender.",
                    "org.springframework.boot", "spring-boot-starter-mail",
                    "org.springframework", "spring-context-support",
                    "org.springframework.mail.javamail.JavaMailSender",
                    null, false),

            // Ops, Cache & Testing
            new SpringModule(
                    "actuator",
                    "Spring Boot Actuator",
                    CAT_OPS,
                    "Supports built-in (or custom) endpoints that let you monitor and manage your application.",
                    "org.springframework.boot", "spring-boot-starter-actuator",
                    "org.springframework.boot", "spring-boot-actuator",
                    "org.springframework.boot.actuate.health.HealthIndicator",
                    null, false),
            new SpringModule(
                    "cache",
                    "Spring Cache Abstraction",
                    CAT_OPS,
                    "Provides cache-related operations such as @Cacheable, @CachePut, and @CacheEvict.",
                    "org.springframework.boot", "spring-boot-starter-cache",
                    "org.springframework", "spring-context-support",
                    "org.springframework.cache.annotation.EnableCaching",
                    "org.springframework.cache.annotation.EnableCaching", false),
            new SpringModule(
                    "test",
                    "Spring Test & MockMvc",
                    CAT_OPS,
                    "Test Spring Boot and Spring Framework applications with JUnit Jupiter, Hamcrest, and Mockito.",
                    "org.springframework.boot", "spring-boot-starter-test",
                    "org.springframework", "spring-test",
                    "org.springframework.test.context.ContextConfiguration",
                    null, true)
    ));

    private static final List<EnableAnnotation> ENABLE_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(
            new EnableAnnotation(
                    "EnableWebMvc",
                    "org.springframework.web.servlet.config.annotation.EnableWebMvc",
                    "@EnableWebMvc - Import Spring Web MVC configuration",
                    "web"),
            new EnableAnnotation(
                    "EnableTransactionManagement",
                    "org.springframework.transaction.annotation.EnableTransactionManagement",
                    "@EnableTransactionManagement - Enable annotation-driven @Transactional management",
                    "tx"),
            new EnableAnnotation(
                    "EnableScheduling",
                    "org.springframework.scheduling.annotation.EnableScheduling",
                    "@EnableScheduling - Enable @Scheduled task execution capability",
                    "context"),
            new EnableAnnotation(
                    "EnableAsync",
                    "org.springframework.scheduling.annotation.EnableAsync",
                    "@EnableAsync - Enable @Async asynchronous method execution",
                    "context"),
            new EnableAnnotation(
                    "EnableCaching",
                    "org.springframework.cache.annotation.EnableCaching",
                    "@EnableCaching - Enable annotation-driven cache management (@Cacheable)",
                    "cache"),
            new EnableAnnotation(
                    "EnableAspectJAutoProxy",
                    "org.springframework.context.annotation.EnableAspectJAutoProxy",
                    "@EnableAspectJAutoProxy - Enable @AspectJ proxy support",
                    "aop"),
            new EnableAnnotation(
                    "EnableJpaRepositories",
                    "org.springframework.data.jpa.repository.config.EnableJpaRepositories",
                    "@EnableJpaRepositories - Enable Spring Data JPA repositories",
                    "data-jpa"),
            new EnableAnnotation(
                    "EnableWebSecurity",
                    "org.springframework.security.config.annotation.web.configuration.EnableWebSecurity",
                    "@EnableWebSecurity - Enable Spring Security web protection",
                    "security"),
            new EnableAnnotation(
                    "ConfigurationPropertiesScan",
                    "org.springframework.boot.context.properties.ConfigurationPropertiesScan",
                    "@ConfigurationPropertiesScan - Scan for @ConfigurationProperties beans",
                    "configuration-processor")
    ));

    private SpringModuleCatalog() {
    }

    public static List<SpringModule> getModules() {
        return MODULES;
    }

    public static Map<String, List<SpringModule>> getModulesByCategory() {
        Map<String, List<SpringModule>> byCategory = new LinkedHashMap<>();
        for (SpringModule module : MODULES) {
            byCategory.computeIfAbsent(module.getCategory(), k -> new ArrayList<>()).add(module);
        }
        return byCategory;
    }

    public static SpringModule findModuleById(String id) {
        if (id == null) {
            return null;
        }
        for (SpringModule module : MODULES) {
            if (module.getId().equals(id)) {
                return module;
            }
        }
        return null;
    }

    public static List<EnableAnnotation> getEnableAnnotations() {
        return ENABLE_ANNOTATIONS;
    }

    public static final class SpringModule {
        private final String id;
        private final String title;
        private final String category;
        private final String description;
        private final String bootGroupId;
        private final String bootArtifactId;
        private final String frameworkGroupId;
        private final String frameworkArtifactId;
        private final String markerClass;
        private final String enableAnnotationFqn;
        private final boolean testScope;

        public SpringModule(
                String id,
                String title,
                String category,
                String description,
                String bootGroupId,
                String bootArtifactId,
                String frameworkGroupId,
                String frameworkArtifactId,
                String markerClass,
                String enableAnnotationFqn,
                boolean testScope) {
            this.id = Objects.requireNonNull(id, "id");
            this.title = Objects.requireNonNull(title, "title");
            this.category = Objects.requireNonNull(category, "category");
            this.description = Objects.requireNonNull(description, "description");
            this.bootGroupId = bootGroupId;
            this.bootArtifactId = bootArtifactId;
            this.frameworkGroupId = frameworkGroupId;
            this.frameworkArtifactId = frameworkArtifactId;
            this.markerClass = markerClass;
            this.enableAnnotationFqn = enableAnnotationFqn;
            this.testScope = testScope;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        public String getBootGroupId() {
            return bootGroupId;
        }

        public String getBootArtifactId() {
            return bootArtifactId;
        }

        public String getFrameworkGroupId() {
            return frameworkGroupId;
        }

        public String getFrameworkArtifactId() {
            return frameworkArtifactId;
        }

        public String getMarkerClass() {
            return markerClass;
        }

        public String getEnableAnnotationFqn() {
            return enableAnnotationFqn;
        }

        public boolean isTestScope() {
            return testScope;
        }

        public String getCoordinates(boolean springBoot) {
            if (springBoot) {
                return bootGroupId + ":" + bootArtifactId;
            }
            return frameworkGroupId + ":" + frameworkArtifactId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SpringModule)) {
                return false;
            }
            SpringModule that = (SpringModule) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static final class EnableAnnotation {
        private final String simpleName;
        private final String fqn;
        private final String description;
        private final String associatedModuleId;

        public EnableAnnotation(String simpleName, String fqn, String description, String associatedModuleId) {
            this.simpleName = simpleName;
            this.fqn = fqn;
            this.description = description;
            this.associatedModuleId = associatedModuleId;
        }

        public String getSimpleName() {
            return simpleName;
        }

        public String getFqn() {
            return fqn;
        }

        public String getDescription() {
            return description;
        }

        public String getAssociatedModuleId() {
            return associatedModuleId;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
