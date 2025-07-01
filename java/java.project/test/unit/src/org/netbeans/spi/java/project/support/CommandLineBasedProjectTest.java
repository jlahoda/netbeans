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
package org.netbeans.spi.java.project.support;

import java.util.List;
import org.netbeans.api.project.Project;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.Configuration;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.ToolKind;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.ToolRun;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation2;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

public class CommandLineBasedProjectTest extends NbTestCase {
    
    public CommandLineBasedProjectTest(String name) {
        super(name);
    }
    
    public void testServices() throws Exception {
        clearWorkDir();

        FileObject wd = FileUtil.toFileObject(getWorkDir());
        FileObject libSrcDir = FileUtil.createFolder(wd, "lib/src/");
        FileObject libSrcFile = FileUtil.createData(libSrcDir, "lib/Lib.java");
        FileObject libClassesDir = FileUtil.createData(wd, "lib/classes/");
        FileObject srcDir = FileUtil.createFolder(wd, "src");
        FileObject srcFile = FileUtil.createData(srcDir, "use/Use.java");
        FileObject classesDir = FileUtil.createData(wd, "classes");
        Configuration configuration = new Configuration(new Project() {
            @Override public FileObject getProjectDirectory() {
                return wd;
            }
            @Override public Lookup getLookup() {
                return Lookup.EMPTY;
            }
        });

        configuration.setRuns(List.of(new ToolRun(ToolKind.JAVAC,
                                                   FileUtil.toFile(wd).getAbsolutePath(),
                                                   "javac",
                                                   List.of("--release", "11",
                                                           "-sourcepath", FileUtil.toFile(libSrcDir).getAbsolutePath(),
                                                           "-d", FileUtil.toFile(libClassesDir).getAbsolutePath(),
                                                           FileUtil.toFile(libSrcFile).getAbsolutePath())),
                                      new ToolRun(ToolKind.JAVAC,
                                                   FileUtil.toFile(wd).getAbsolutePath(),
                                                   "javac",
                                                   List.of("--release", "17",
                                                           "-sourcepath", FileUtil.toFile(srcDir).getAbsolutePath(),
                                                           "-classpath", FileUtil.toFile(libClassesDir).getAbsolutePath(),
                                                           "-d", FileUtil.toFile(classesDir).getAbsolutePath(),
                                                           FileUtil.toFile(srcFile).getAbsolutePath()))));

        Lookup baseProjectLookup = CommandLineBasedProject.projectLookupBase(configuration);
        SourceLevelQueryImplementation2 slq = baseProjectLookup.lookup(SourceLevelQueryImplementation2.class);

        assertNotNull(slq);
        assertEquals("11", slq.getSourceLevel(libSrcFile).getSourceLevel());
        assertEquals("11", slq.getSourceLevel(libSrcDir).getSourceLevel());
        assertEquals("17", slq.getSourceLevel(srcFile).getSourceLevel());
        assertEquals("17", slq.getSourceLevel(srcDir).getSourceLevel());
    }
    
}
