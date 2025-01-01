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
package org.netbeans.api.debugger.jpda;

import java.io.IOException;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.debugger.jpda.DebuggerStarterImpl;
import org.netbeans.modules.debugger.jpda.SPIAccessor;
import org.netbeans.spi.debugger.jpda.JPDADebuggerStarterImplementation.Context;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.netbeans.spi.debugger.jpda.JPDADebuggerStarterImplementation;


/**Based on maven/src/org/netbeans/modules/maven/debug/JPDAStart.java
 * Start the JPDA debugger
 * @author Milos Kleint
 */
public class JPDADebuggerStarter {

    private static final RequestProcessor RP = new RequestProcessor(JPDADebuggerStarter.class);

    private FileObject baseDir;
    private String actionName;
    private ClassPath jdkSourcePath = ClassPath.EMPTY;
    private ClassPath sourcePath = ClassPath.EMPTY;
    private String transport = "dt_socket";

    private JPDADebuggerStarter(FileObject baseDir) {
        this.baseDir = baseDir;
        this.actionName = baseDir.getNameExt();
    }

    public static JPDADebuggerStarter create(FileObject baseDir) {
        return new JPDADebuggerStarter(baseDir);
    }

    public @NonNull ConnectionInfo startDebugger() throws IOException, IllegalStateException {
        ConnectionInfo info = startDebugger(true);

        if (info == null) {
            throw new IllegalStateException("Cannot start debugger");
        }

        return info;
    }

    public @CheckForNull ConnectionInfo startDebugger(boolean useDefault) throws IOException, IllegalStateException {
        Context context = SPIAccessor.getInstance().newContext(baseDir, actionName, jdkSourcePath, sourcePath, transport);

        for (JPDADebuggerStarterImplementation i : Lookup.getDefault().lookupAll(JPDADebuggerStarterImplementation.class)) {
            if (i instanceof DebuggerStarterImpl && !useDefault) {
                continue;
            }

            ConnectionInfo info = i.startDebugger(context);

            if (info != null) {
                return info;
            }
        }

        return null;
    }

    public JPDADebuggerStarter withName(String actionName) {
        this.actionName = actionName;
        return this;
    }

    public JPDADebuggerStarter withJdkSourcePath(ClassPath jdkSourcePath) {
        this.jdkSourcePath = jdkSourcePath;
        return this;
    }

    public JPDADebuggerStarter withSourcePath(ClassPath sourcePath) {
        this.sourcePath = sourcePath;
        return this;
    }

    public record ConnectionInfo(String transport, String host, int port) {}
    
}
