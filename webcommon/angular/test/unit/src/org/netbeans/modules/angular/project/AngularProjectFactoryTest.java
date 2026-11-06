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
package org.netbeans.modules.angular.project;

import java.nio.charset.StandardCharsets;
import org.netbeans.api.project.ProjectManager;
import junit.framework.TestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class AngularProjectFactoryTest extends TestCase {
    public AngularProjectFactoryTest(String name) {
        super(name);
    }

    public void testRegistrationPrecedesAntProjects() throws Exception {
        String service = "META-INF/services/org.netbeans.spi.project.ProjectFactory";
        boolean found = false;
        var resources = AngularProjectFactory.class.getClassLoader().getResources(service);
        while (resources.hasMoreElements()) {
            try (var stream = resources.nextElement().openStream()) {
                String registration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                if (registration.contains(AngularProjectFactory.class.getName() + "\n#position=50")) {
                    found = true;
                }
            }
        }
        assertTrue("Angular must be registered before the Ant factory at position 100", found);
    }

    public void testExistingHtml5MetadataLoadsAsAngular() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        write(root, "angular.json", "{\"version\":1,\"projects\":{}}");
        String metadata = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://www.netbeans.org/ns/project/1">
                    <type>org.netbeans.modules.web.clientproject</type>
                    <configuration>
                        <data xmlns="http://www.netbeans.org/ns/clientside-project/1">
                            <name>existing-angular-app</name>
                        </data>
                    </configuration>
                </project>
                """;
        write(root, "nbproject/project.xml", metadata);
        var project = ProjectManager.getDefault().findProject(root);
        assertNotNull(project);
        assertTrue("Existing HTML5 metadata must not override Angular detection",
                project instanceof AngularProject);
        assertEquals("Existing metadata must be preserved", metadata,
                root.getFileObject("nbproject/project.xml").asText());
    }

    public void testPlainHtml5ProjectIsNotClaimed() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        write(root, "nbproject/project.xml", "<project/>");
        write(root, "package.json", "{}");
        assertFalse(new AngularProjectFactory().isProject(root));
    }

    private static void write(FileObject root, String path, String content) throws Exception {
        try (var stream = FileUtil.createData(root, path).getOutputStream()) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
