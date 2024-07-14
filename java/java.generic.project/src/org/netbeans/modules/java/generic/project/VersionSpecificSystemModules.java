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

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Exceptions;

/**
 *
 * @author jlahoda
 */
public class VersionSpecificSystemModules {
    private static final Map<String, ClassPath> VERSION_TO_SYSTEM_MODULES = new HashMap<>();

    public static ClassPath getSystemModulesFor(String version) {
        return VERSION_TO_SYSTEM_MODULES.computeIfAbsent(version, v -> {
            File systemModulesDir = Places.getCacheSubdirectory("java/system");
            File systemModules = new File(systemModulesDir, version);
            if (!systemModules.isDirectory()) {
                try {
                    JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, d -> {}, Arrays.asList("--release", version), Arrays.asList("java.base"), null);
                    task.analyze();
                    JavaFileManager fm = ((JavacTaskImpl) task).getContext().get(JavaFileManager.class);
                    for (Set<Location> locations : fm.listLocationsForModules(StandardLocation.SYSTEM_MODULES)) {
                        for (Location location : locations) {
                            String name = fm.inferModuleName(location);
                            File moduleDir = new File(systemModules, name);
                            Location moduleLocation = fm.getLocationForModule(StandardLocation.SYSTEM_MODULES, name);
                            for (JavaFileObject fo : fm.list(moduleLocation, "", EnumSet.allOf(Kind.class), true)) {
                                String binaryName = fm.inferBinaryName(moduleLocation, fo);
                                File target = new File(moduleDir, binaryName.replace(".", "/") + ".class");
                                target.getParentFile().mkdirs();
                                try (InputStream in = fo.openInputStream();
                                     OutputStream out = new FileOutputStream(target)) {
                                    FileUtil.copy(in, out);
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                
            }
            return ClassPathSupport.createClassPath(FileUtil.urlForArchiveOrDir(systemModules));
        });
    }
    
}
