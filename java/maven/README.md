<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

# Lombok setup in AlpLibre IDE

AlpLibre IDE provides Lombok setup for Maven projects from the project menu and
Java editor. No separate Lombok installer or manual JAR download is required.

## Set up a project

1. Open a Maven Java project.
2. Right-click the project in the Projects window and choose **Set up Lombok**.
3. The IDE shows setup progress, updates `pom.xml`, reloads the project, and requests dependency download.
   A completion dialog identifies the POM that was configured; failures show an error dialog.
4. Build the project to verify compilation and resolve compiler annotation processors.

The action adds the `org.projectlombok:lombok` dependency with `provided` scope
when it is missing, and adds Lombok to the Maven compiler annotation processor
paths. Existing dependency versions, Spring Boot/BOM version management, compiler
settings, and other annotation processors are retained. Repeating setup does not
add duplicate Lombok dependencies or processor entries.

## Editor suggestions

When an unresolved import such as `import lombok.Data;` or a Lombok annotation
has an editor error, press **Alt+Enter** on the error and choose **Set up Lombok**.
Fully qualified annotations such as `@lombok.Builder` are also recognized.

If the Lombok dependency already exists, the fix is named **Repair Lombok
configuration**. Resolved Lombok annotations also receive a warning when the
Maven annotation processor setup is missing or processing is disabled by a
recognized compiler setting. Apply the repair from that warning with
**Alt+Enter**.

For simple annotation names such as `@Data`, detection requires a Lombok import.
Supported common annotation names are recognized with Lombok wildcard imports.
An annotation imported explicitly from another library is ignored; a bare
`@Data` without an import does not trigger Lombok setup. Comments and strings
containing Lombok references do not trigger suggestions.

Both editor fixes use the same setup action as the project menu. Suggestions do
not modify the project until the fix is selected.

## Dependency download

Setup writes Maven configuration and requests dependency download after the
project reload finishes. If the selected version is
already in the local Maven repository, Maven reuses it. Otherwise, dependency
resolution needs access to the project's configured Maven repositories, normally
Maven Central. Offline mode cannot download an uncached dependency.

Reloading is not a guarantee that every artifact has already downloaded. Run a
Maven build if dependencies remain unresolved. The annotation processor artifact
is resolved by Maven as part of compilation.

## Current scope

- Maven projects using Maven Compiler Plugin 3.x are supported.
- Maven Compiler Plugin 4.x automatic setup is not supported; the project action
  reports this limitation and the editor does not suggest an unusable fix.
- Gradle and Ant projects do not receive these setup suggestions.
- Setup preserves existing Lombok versions; it does not automatically upgrade an
  existing version for a newer JDK.
- Named Java modules may need module declarations configured separately.

## Build the IDE modules

From the repository root, after the initial IDE build:

```sh
ant -f java/maven/build.xml netbeans
ant -f java/maven.hints/build.xml netbeans
```

Restart the development IDE to load the updated modules. These commands build
the Maven setup action and editor hints without rebuilding the whole IDE.
