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
package org.netbeans.modules.java.project.commandline;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.platform.JavaPlatform;
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
                if (!generateVersionClasses(systemModules, version)) {
                    return JavaPlatform.getDefault().getBootstrapLibraries();
                }
            }
            return ClassPathSupport.createClassPath(FileUtil.urlForArchiveOrDir(systemModules));
        });
    }

    static boolean generateVersionClasses(File target, String version) {
        try {
            File javaLauncher = FileUtil.toFile(JavaPlatform.getDefault().findTool("java"));
            URL dumpJarLocation = DumpVersionSpecificSystemModules.class.getProtectionDomain().getCodeSource().getLocation();
            Process p = new ProcessBuilder(javaLauncher.getAbsolutePath(),
                                           "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                                           "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                                           "-cp", FileUtil.archiveOrDirForURL(dumpJarLocation).getAbsolutePath(),
                                           DumpVersionSpecificSystemModules.class.getName(),
                                           target.getAbsolutePath(),
                                           version)
                    .inheritIO().start();
            if (p.waitFor() != 0) {
                return false;
            }
        } catch (InterruptedException | IOException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }

        return true;
    }
}
