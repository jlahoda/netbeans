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

import java.util.LinkedHashSet;
import java.util.Set;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.classpath.JavaClassPathConstants;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.modules.java.project.commandline.CommandLineBasedJavaRoot.Root;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author lahvac
 */
public class ClassPathProviderImpl implements ClassPathProvider {

    private final CommandLineBasedJavaRoot roots;
    public ClassPathProviderImpl(CommandLineBasedJavaRoot roots) {
        this.roots = roots;
        roots.addChangeListener(evt -> updateClassPathsRegistration());
        updateClassPathsRegistration();
    }

    @Override
    public ClassPath findClassPath(FileObject fo, String type) {
        for (Root r : roots.getRoots()) { //TODO: performance?
            FileObject root = URLMapper.findFileObject(r.getRoot());

            if (root == null) continue;
            if (root == fo || FileUtil.isParentOf(root, fo)) {
                switch (type) {
                    case ClassPath.SOURCE: return r.getSourcePath();
                    case ClassPath.COMPILE: return r.getClassPath();
                    case JavaClassPathConstants.MODULE_COMPILE_PATH: return r.getModulePath();
                    case ClassPath.BOOT:
//                    case JavaClassPathConstants.MODULE_BOOT_PATH: return /*TODO:*/JavaPlatform.getDefault().getBootstrapLibraries();
                    case JavaClassPathConstants.MODULE_BOOT_PATH: return VersionSpecificSystemModules.getSystemModulesFor(r.getSourceLevel()); //TODO: difference between -source and --release!
                }
                return null;
            }
        }
        return null;
    }

    private Set<ClassPath> bootCP = new LinkedHashSet<>();
    private Set<ClassPath> compileCP = new LinkedHashSet<>();
    private Set<ClassPath> sourceCP = new LinkedHashSet<>();

    public synchronized void updateClassPathsRegistration() {
        Set<ClassPath> newBootCP = new LinkedHashSet<>();
        Set<ClassPath> newCompileCP = new LinkedHashSet<>();
        Set<ClassPath> newSourceCP = new LinkedHashSet<>();
        newBootCP.add(JavaPlatform.getDefault().getBootstrapLibraries());
        for (Root r : roots.getRoots()) {
            newCompileCP.add(r.getModulePath());
            newSourceCP.add(r.getSourcePath());
        }
        Set<ClassPath> addedBootCP = new LinkedHashSet<>(newBootCP);
        Set<ClassPath> addedCompileCP = new LinkedHashSet<>(newCompileCP);
        Set<ClassPath> addedSourceCP = new LinkedHashSet<>(newSourceCP);
        Set<ClassPath> removedBootCP = new LinkedHashSet<>(bootCP);
        Set<ClassPath> removedCompileCP = new LinkedHashSet<>(compileCP);
        Set<ClassPath> removedSourceCP = new LinkedHashSet<>(sourceCP);
        addedBootCP.removeAll(bootCP);
        addedCompileCP.removeAll(compileCP);
        addedSourceCP.removeAll(sourceCP);
        removedBootCP.removeAll(newBootCP);
        removedCompileCP.removeAll(newCompileCP);
        removedSourceCP.removeAll(newSourceCP);
        bootCP = newBootCP;
        compileCP = newCompileCP;
        sourceCP = newSourceCP;
        GlobalPathRegistry.getDefault().register(ClassPath.BOOT, addedBootCP.toArray(new ClassPath[0]));
        GlobalPathRegistry.getDefault().register(ClassPath.COMPILE, addedCompileCP.toArray(new ClassPath[0]));
        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, addedSourceCP.toArray(new ClassPath[0]));
        GlobalPathRegistry.getDefault().unregister(ClassPath.BOOT, removedBootCP.toArray(new ClassPath[0]));
        GlobalPathRegistry.getDefault().unregister(ClassPath.COMPILE, removedCompileCP.toArray(new ClassPath[0]));
        GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, removedSourceCP.toArray(new ClassPath[0]));
    }
    
}
