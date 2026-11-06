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
package org.netbeans.modules.java.editor.semantic;

import java.util.List;
import org.netbeans.junit.NbTestCase;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.api.java.source.TestUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class MethodUsagesSideBarFactoryTest extends NbTestCase {
    public MethodUsagesSideBarFactoryTest(String name) { super(name); }

    public void testOverloadsReferencesAndCrossFileCalls() throws Exception {
        clearWorkDir();
        SourceUtilsTestUtil.prepareTest(new String[] {
            "org/netbeans/modules/java/editor/resources/layer.xml"
        }, new Object[0]);
        FileObject root = FileUtil.toFileObject(getWorkDir());
        FileObject src = root.createFolder("src");
        FileObject build = root.createFolder("build");
        FileObject cache = root.createFolder("cache");
        FileObject declaration = src.createData("Example.java");
        TestUtilities.copyStringToFile(declaration,
                "public class Example {\n"
                + " public static void target() {}\n"
                + " public static void target(int n) {}\n"
                + " public void unused() {}\n"
                + " public void caller() { target(); target(1); Runnable r = Example::target; }\n"
                + "}\n");
        TestUtilities.copyStringToFile(src.createData("Other.java"),
                "class Other { void call() { Example.target(); } }");
        SourceUtilsTestUtil.prepareTest(src, build, cache, new FileObject[0]);
        SourceUtilsTestUtil.compileRecursively(src);
        List<MethodUsagesSideBarFactory.Method> methods = MethodUsagesSideBarFactory.collect(
                JavaSource.forFileObject(declaration), () -> false);
        assertEquals(4, methods.size());
        assertEquals("No-arg calls and method reference", 3, methods.get(0).usages.size());
        assertEquals("Overload stays separate", 1, methods.get(1).usages.size());
        assertEquals(0, methods.get(2).usages.size());
        assertEquals(0, methods.get(3).usages.size());
        assertTrue(methods.get(0).usages.stream().anyMatch(u -> u.file.getName().equals("Other")));
        assertTrue(methods.get(0).usages.stream().anyMatch(u -> u.caller.equals("call()")
                && u.preview.contains("Example.target()") && u.reference.equals("Example.target")));
        assertTrue(MethodUsagesSideBarFactory.collect(JavaSource.forFileObject(declaration), () -> true).isEmpty());
    }
    public void testSearchAndKeyboardSelection() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject first = root.createData("First.java");
        FileObject second = root.createData("Second.java");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            MethodUsagesPopup popup = new MethodUsagesPopup(List.of(
                    new MethodUsagesSideBarFactory.Usage(first, 1, 2, "load()", "target();", "target"),
                    new MethodUsagesSideBarFactory.Usage(second, 2, 3, "save()", "target(); // <data>", "target")), null);
            assertEquals(2, popup.model.size());
            popup.filter.getActionMap().get("next-result").actionPerformed(null);
            assertEquals(1, popup.results.getSelectedIndex());
            popup.filter.setText("SAVE");
            assertEquals(1, popup.model.size());
            assertEquals(second, popup.results.getSelectedValue().file);
            javax.swing.JLabel rendered = (javax.swing.JLabel) popup.results.getCellRenderer()
                    .getListCellRendererComponent(popup.results, popup.model.get(0), 0, false, false);
            assertTrue(rendered.getText().contains("&lt;data&gt;"));
            assertTrue(rendered.getText().contains("<b>target</b>"));
            popup.filter.setText("First.java");
            assertEquals(first, popup.results.getSelectedValue().file);
            popup.filter.setText("missing");
            assertEquals(0, popup.model.size());
            assertNull(popup.results.getSelectedValue());
            popup.filter.setText("");
            assertEquals(2, popup.model.size());
        });
    }

}
