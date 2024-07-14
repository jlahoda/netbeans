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

import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.event.ChangeListener;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.classpath.JavaClassPathConstants;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.modules.java.generic.project.GenericJavaRoot.Root;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation2;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author lahvac
 */
public class SourceLevelQueryImpl implements SourceLevelQueryImplementation2 {

    private final GenericJavaProject project;

    public SourceLevelQueryImpl(GenericJavaProject project) {
        this.project = project;
    }

    @Override
    public Result getSourceLevel(FileObject fo) {
        for (Root r : project.getRoots().getRoots()) { //TODO: performance?
            FileObject root = URLMapper.findFileObject(r.getRoot());

            if (root == null) continue;
            if (root == fo || FileUtil.isParentOf(root, fo)) {
                return new Result() {
                    @Override
                    public String getSourceLevel() {
                        return r.getSourceLevel();
                    }
                    @Override
                    public void addChangeListener(ChangeListener cl) {
                    }
                    @Override
                    public void removeChangeListener(ChangeListener cl) {
                    }
                };
            }
        }
        return null;
    }

}
