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
package org.netbeans.spi.debugger.jpda;

import java.io.IOException;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.debugger.jpda.JPDADebuggerStarter.ConnectionInfo;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.debugger.jpda.SPIAccessor;
import org.openide.filesystems.FileObject;

public interface JPDADebuggerStarterImplementation {

    @CheckForNull
    public ConnectionInfo startDebugger(Context context) throws IOException, IllegalStateException;
    
    public static final class Context {
        private final FileObject baseDir;
        private final String actionName;
        private final ClassPath jdkSourcePath;
        private final ClassPath sourcePath;
        private final String transport;

        Context(FileObject baseDir, String name, ClassPath jdkSourcePath, ClassPath sourcePath, String transport) {
            this.baseDir = baseDir;
            this.actionName = name;
            this.jdkSourcePath = jdkSourcePath;
            this.sourcePath = sourcePath;
            this.transport = transport;
        }

        public FileObject getBaseDir() {
            return baseDir;
        }

        public String getName() {
            return actionName;
        }

        public ClassPath getJdkSourcePath() {
            return jdkSourcePath;
        }

        public ClassPath getSourcePath() {
            return sourcePath;
        }

        public String getTransport() {
            return transport;
        }

        static {
            SPIAccessor.setInstance(new SPIAccessor() {
                @Override
                public Context newContext(FileObject baseDir, String actionName, ClassPath jdkSourcePath, ClassPath sourcePath, String transport) {
                    return new Context(baseDir, actionName, jdkSourcePath, sourcePath, transport);
                }
            });
        }
    }

}
