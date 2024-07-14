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

import java.util.List;
import javax.swing.event.ChangeListener;

import org.netbeans.modules.java.generic.project.GenericJavaRoot.Root;
import org.netbeans.spi.java.queries.CompilerOptionsQueryImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author lahvac
 */
public class CompilerOptionsQueryImpl implements CompilerOptionsQueryImplementation {

    private final GenericJavaProject project;

    public CompilerOptionsQueryImpl(GenericJavaProject project) {
        this.project = project;
    }

    @Override
    public Result getOptions(FileObject fo) {
        for (Root r : project.getRoots().getRoots()) { //TODO: performance?
            FileObject root = URLMapper.findFileObject(r.getRoot());

            if (root == null) continue;
            if (root == fo || FileUtil.isParentOf(root, fo)) {
                return new Result() {
                    @Override
                    public List<? extends String> getArguments() {
                        return r.getExtraCompilationOptions();
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
