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
package org.netbeans.modules.java.debugjavac;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class UtilitiesTest extends NbTestCase {

    public UtilitiesTest(String testName) {
        super(testName);
    }

    public void testCommandLineParameters() throws Exception {
        clearWorkDir();

        FileObject workDir = FileUtil.toFileObject(getWorkDir());
        this.bootDir = FileUtil.createFolder(workDir, "boot");
        this.compileDir = FileUtil.createFolder(workDir, "compile");
        this.srcDir = FileUtil.createFolder(workDir, "src");

        doTestCommandLineParameters("", "-bootclasspath", "{workdir}/boot", "-classpath", "{workdir}/compile", "-source", "1.6", "-target", "1.6");
        doTestCommandLineParameters("-g", "-bootclasspath", "{workdir}/boot", "-classpath", "{workdir}/compile", "-source", "1.6", "-target", "1.6", "-g");
        doTestCommandLineParameters("-classpath \"non existing path\" -bootclasspath 'other path'", "-source", "1.6", "-target", "1.6", "-classpath", "non existing path", "-bootclasspath", "other path");
    }

    private void doTestCommandLineParameters(String extraParams, String... expectedTemplate) throws IOException {
        List<String> commandLineParameters = Utilities.commandLineParameters(srcDir, extraParams);
        List<String> expected = new ArrayList<>();

        for (String exp : expectedTemplate) {
            expected.add(exp.replace("{workdir}", getWorkDirPath()));
        }

        assertEquals(expected, commandLineParameters);
    }

    private static FileObject bootDir;
    private static FileObject compileDir;
    private static FileObject srcDir;

    @ServiceProvider(service=ClassPathProvider.class, position = 0)
    public static class ClassPathProviderImpl implements ClassPathProvider {

        @Override
        public ClassPath findClassPath(FileObject file, String type) {
            if (Objects.equals(file, srcDir)) {
                switch (type) {
                    case ClassPath.BOOT: return ClassPathSupport.createClassPath(bootDir);
                    case ClassPath.COMPILE: return ClassPathSupport.createClassPath(compileDir);
                }
            }

            return null;
        }

    }

    @SuppressWarnings("deprecation")
    @ServiceProvider(service=SourceLevelQueryImplementation.class, position = 0)
    public static class SourceLevelQueryImpl implements SourceLevelQueryImplementation {

        @Override
        public String getSourceLevel(FileObject javaFile) {
            return "1.6";
        }

    }

}
