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
package org.netbeans.modules.java.generic.project;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.java.generic.project.GenericJavaRoot.Root;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author jlahoda
 */
public class GenericJavaRootsTest extends NbTestCase {
    
    static {
        System.setProperty("netbeans.dirs", System.getProperty("cluster.path.final"));
    }

    private FileObject wdFO;
    public GenericJavaRootsTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        clearWorkDir();
        File wd = getWorkDir();
        wdFO = FileUtil.toFileObject(wd);
    }

    public void testUpdateRoots() throws IOException {
        createFile("foo/java-17/foo/Foo.java", "package foo;");
        createFile("foo/java-11/foo/Foo.java", "package foo;");
        GenericJavaRoot bachRoots = new GenericJavaRoot(new GenericJavaProject(wdFO));
        bachRoots.updateRoots(INPUT);
        List<Root> roots = bachRoots.getRoots();
        List<String> actualRoots = roots.stream().map(r -> root2String(wdFO.toURL(), r)).collect(Collectors.toList());
        List<String> expectedRoots = Arrays.asList(
        );
        assertEquals(expectedRoots, actualRoots);
    }

    private String root2String(URL projectDir, Root root) {
        return "name: " + root.getDisplayName() + "\n" +
               "source-root: " + url2String(projectDir, root.getRoot()) + "\n" +
               "target-root: " + url2String(projectDir, root.getTarget()) + "\n" +
               "source-level: " + root.getSourceLevel() + "\n" +
               "module-path: " + classPath2String(projectDir, root.getModulePath()) + "\n" +
               "source-path: " + classPath2String(projectDir, root.getSourcePath()) + "\n" +
               "extra-options: " + root.getExtraCompilationOptions().stream().collect(Collectors.joining(" ")) + "\n";
    }

    private String url2String(URL projectDir, URL url) {
        try {
            return projectDir.toURI().relativize(url.toURI()).toString();
        } catch (URISyntaxException ex) {
            throw (AssertionError) new AssertionError().initCause(ex);
        }
    }

    private String classPath2String(URL projectDir, ClassPath cp) {
        return cp.entries().stream().map(e -> e.getURL()).map(u -> url2String(projectDir, u)).collect(Collectors.joining(", "));
    }

    private void createFile(String path, String code) throws IOException {
        try (OutputStream out = wdFO.getFileObject(path, false).getOutputStream()) {
            out.write(code.getBytes());
        }
    }

    private static final String INPUT =
            "+ cache\n" +
            "+ load modules\n" +
            "+ compile\n" +
            "Compile 1 module in main space...\n" +
            "+ compile-classes main\n" +
            "+ javac --release 9 --module foo --module-source-path ./*/java -d .bach/out/main/classes/java-9\n" +
            "+ compile-modules main\n" +
            "TODO Here be a better implemenation...\n" +
            "+ javac --release 17 --class-path .bach/out/main/classes/java-9/foo -implicit:none -d .bach/out/main/classes/java-17/foo foo/java-17/foo/Foo.java\n" +
            "+ javac --release 11 --class-path .bach/out/main/classes/java-9/foo -implicit:none -d .bach/out/main/classes/java-11/foo foo/java-11/foo/Foo.java\n" +
            "+ jar --create --file .bach/out/main/modules/foo.jar --module-version 0-ea -C .bach/out/main/classes/java-9/foo . --release 11 -C .bach/out/main/classes/java-11/foo . --release 17 -C .bach/out/main/classes/java-17/foo .\n" +
            "+ hash .bach/out/main/modules\n" +
            "Hash [SHA-256]                                                   Size [Bytes] Path\n" +
            "2a07f328c0f6d8e7859b4fe52aa8646a1b7b42ccd14abb00700bc7e7c0bf6f90         2246 .bach/out/main/modules/foo.jar\n" +
            "+ test";
}
