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

import java.net.URL;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.queries.SourceForBinaryQuery;

import org.netbeans.modules.java.project.commandline.CommandLineBasedJavaRoot.Root;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation2;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author lahvac
 */
public class SourceForBinaryQueryImpl implements SourceForBinaryQueryImplementation2 {

    private final CommandLineBasedJavaRoot roots;

    public SourceForBinaryQueryImpl(CommandLineBasedJavaRoot roots) {
        this.roots = roots;
    }

    @Override
    public Result findSourceRoots2(URL url) {
        for (Root r : roots.getRoots()) { //TODO: performance?
            if (url.equals(r.getTarget())) { //TODO: equals on URL!
                return new Result() { //TODO: cache!
                    @Override
                    public FileObject[] getRoots() {
                        FileObject sourceRoot = URLMapper.findFileObject(r.getRoot());
                        if (sourceRoot != null) {
                            return new FileObject[] {
                                sourceRoot
                            };
                        } else {
                            return new FileObject[0];
                        }
                    }
                    @Override
                    public boolean preferSources() {
                        return true;
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

    @Override
    public SourceForBinaryQuery.Result findSourceRoots(URL url) {
        return findSourceRoots2(url);
    }

}
