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
package org.netbeans.modules.remote.agent.sync;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.editor.*;
import org.netbeans.modules.lsp.client.debugger.api.DAPLineBreakpoint;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.sync.SyncHandler.BreakpointDescription;
import org.netbeans.modules.remote.ide.sync.SyncHandler.EditorDescription;
import org.netbeans.modules.remote.ide.sync.SyncHandler.SyncInterface;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Service.class)
public class SyncService implements Service {

    @Override
    public String name() {
        return "sync";
    }

    @Override
    public void run(InputStream in, OutputStream out) {
        RemoteInvocation.receiver(in, out, new SyncInterface() {
            @Override
            public String[] openedProjects() {
                return Arrays.stream(OpenProjects.getDefault().getOpenProjects())
                             .map(prj -> prj.getProjectDirectory())
                             .map(root -> Utils.file2Path(root))
                             .toArray(String[]::new);
            }
            @Override
            public EditorDescription[] openedEditors() {
                return EditorRegistry.componentList()
                                     .stream()
                                     .map(c -> {
                                         FileObject file = NbEditorUtilities.getFileObject(c.getDocument());
                                         return new EditorDescription(Utils.file2Path(file), c.getCaretPosition());
                                     })
                                     .toArray(EditorDescription[]::new);
            }
            @Override
            public BreakpointDescription[] breakpoints() {
                List<BreakpointDescription> result = new ArrayList<>();

                for (Breakpoint brk : DebuggerManager.getDebuggerManager().getBreakpoints()) {
                    DAPLineBreakpoint converted = DAPLineBreakpoint.copyOf(brk);

                    if (converted == null) {
                        //cannot synchronize this breakpoint
                        continue;
                    }

                    result.add(new BreakpointDescription(Utils.file2Path(converted.getFileObject()), converted.getLineNumber(), converted.getCondition()));
                }

                return result.toArray(BreakpointDescription[]::new);
            }
        });
    }

}
