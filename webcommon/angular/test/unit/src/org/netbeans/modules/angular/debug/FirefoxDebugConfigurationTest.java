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
package org.netbeans.modules.angular.debug;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import junit.framework.TestCase;

public class FirefoxDebugConfigurationTest extends TestCase {
    private Path work;
    public FirefoxDebugConfigurationTest(String name) { super(name); }
    @Override protected void setUp() throws Exception { work=Files.createTempDirectory("angular-firefox-test-"); }
    @Override protected void tearDown() throws Exception {
        try (var paths=Files.walk(work)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private File getWorkDir() { return work.toFile(); }
    public void testLaunchAndAttachWithSourceMappings() throws Exception {
        Path root=getWorkDir().toPath();
        var config=FirefoxDebugConfiguration.configuration(root,"http://localhost:4200/","stable",false,6000,
                "[{\"url\":\"webpack:///./\",\"path\":\"${workspaceFolder}/\"}]");
        assertEquals("launch",config.get("request")); assertEquals("stable",config.get("firefoxExecutable"));
        assertEquals(true,config.get("sourceMaps"));
        assertTrue(config.get("pathMappings").toString().contains(root.toString()));
        var attach=FirefoxDebugConfiguration.configuration(root,"http://localhost:4200/","stable",true,6001,"");
        assertEquals("attach",attach.get("request")); assertFalse(attach.containsKey("firefoxExecutable"));
        assertEquals("localhost",attach.get("host")); assertEquals(6001,attach.get("port"));
    }
    public void testManagedProfileDirectory() throws Exception {
        var launch=FirefoxDebugConfiguration.configuration(work,"http://localhost:4200/","stable",false,6000,"");
        Path parent=work.resolve("cache/profiles");
        FirefoxDebugConfiguration.prepareProfileDirectory(launch,parent);
        assertEquals(parent.toRealPath().toString(),launch.get("tmpDir"));
        assertEquals(false,launch.get("keepProfileChanges"));
        assertFalse(launch.containsKey("profileDir"));
        var attach=FirefoxDebugConfiguration.configuration(work,"http://localhost:4200/","",true,6000,"");
        Path unused=work.resolve("unused");
        FirefoxDebugConfiguration.prepareProfileDirectory(attach,unused);
        assertFalse(Files.exists(unused));
        assertFalse(attach.containsKey("tmpDir"));
    }
    public void testProfileDirectoryFailure() throws Exception {
        Path file=Files.writeString(work.resolve("file"),"occupied");
        var config=new HashMap<String,Object>();
        config.put("request","launch");
        try { FirefoxDebugConfiguration.prepareProfileDirectory(config,file.resolve("profiles")); fail(); }
        catch (IOException expected) { }
        assertFalse(config.containsKey("tmpDir"));
    }
    public void testLaunchIsolatesApplicationData() throws Exception {
        var config=FirefoxDebugConfiguration.configuration(work,"http://localhost:4200/","stable",false,6000,"");
        Path parent=work.resolve("cache with spaces/profiles");
        FirefoxDebugConfiguration.prepareProfileDirectory(config,parent);
        var environment=new HashMap<String,String>();
        environment.put("MOZ_APP_DATA","personal-data");
        environment.put("MOZ_LOCAL_APP_DATA","personal-cache");
        environment.put("PATH","preserved");
        FirefoxDebugConfiguration.prepareLaunchEnvironment(config,environment);
        assertEquals(parent.resolve("app-data").toRealPath().toString(),environment.get("MOZ_APP_DATA"));
        assertEquals(parent.resolve("local-app-data").toRealPath().toString(),environment.get("MOZ_LOCAL_APP_DATA"));
        assertEquals("preserved",environment.get("PATH"));
    }
    public void testAttachPreservesEnvironment() throws Exception {
        var environment=new HashMap<String,String>(Map.of("MOZ_APP_DATA","personal-data"));
        FirefoxDebugConfiguration.prepareLaunchEnvironment(Map.of("request","attach"),environment);
        assertEquals(Map.of("MOZ_APP_DATA","personal-data"),environment);
    }
    public void testAttachPreservesPort() throws Exception {
        var config=new HashMap<String,Object>(Map.of("request","attach","port",6000));
        FirefoxDebugConfiguration.prepareLaunchPort(config);
        assertEquals(6000,config.get("port"));
    }
    public void testInvalidConfiguration() throws Exception {
        for (String url : List.of("file:///tmp/index.html","javascript:alert(1)","http://user:password@localhost/")) {
            try { FirefoxDebugConfiguration.configuration(getWorkDir().toPath(),url,"",false,6000,""); fail(url); }
            catch (IllegalArgumentException expected) {}
        }
        try { FirefoxDebugConfiguration.configuration(getWorkDir().toPath(),"http://localhost/","",false,6000,"{}"); fail(); }
        catch (IllegalArgumentException expected) {}
    }
    private Path zip(String name, Map<String,String> entries) throws Exception {
        Path archive=getWorkDir().toPath().resolve(name);
        try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        return archive;
    }
    public void testVsixPreservesDependencies() throws Exception {
        Path archive=zip("adapter.vsix",Map.of("extension/dist/adapter.bundle.js","adapter","extension/dist/helper.js","helper","extension/LICENSE","license"));
        Path destination=getWorkDir().toPath().resolve("installed");
        assertTrue(Files.isRegularFile(FirefoxDebugConfiguration.installVsix(archive,destination)));
        assertEquals("helper",Files.readString(destination.resolve("extension/dist/helper.js")));
        assertTrue(Files.isRegularFile(destination.resolve("extension/LICENSE")));
    }
    public void testVsixRejectsTraversalAndWrongExtension() throws Exception {
        Path archive=zip("bad.vsix",Map.of("extension/../../escaped","bad"));
        try { FirefoxDebugConfiguration.installVsix(archive,getWorkDir().toPath().resolve("bad")); fail(); }
        catch (IOException expected) {}
        assertFalse(Files.exists(getWorkDir().toPath().resolve("escaped")));
        Path empty=zip("wrong.vsix",Map.of("extension/dist/other.js","other"));
        try { FirefoxDebugConfiguration.installVsix(empty,getWorkDir().toPath().resolve("wrong")); fail(); }
        catch (IOException expected) {}
    }
}
