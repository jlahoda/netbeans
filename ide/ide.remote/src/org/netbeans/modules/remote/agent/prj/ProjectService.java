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
package org.netbeans.modules.remote.agent.prj;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.node.remote.LspTreeViewServiceImpl;
import org.netbeans.modules.node.remote.NodeCallback;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.ProjectInterface;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 */
@ServiceProvider(service=Service.class)
public class ProjectService implements Service {

    @Override
    public String name() {
        return "prj";
    }

    @Override
    public void run(InputStream in, OutputStream out) {
        StreamMultiplexor projectMultiplexor = new StreamMultiplexor(in, out);
        Streams commands = projectMultiplexor.getStreamsForChannel(0);
        Streams projectView = projectMultiplexor.getStreamsForChannel(1);
        RemoteInvocation.receiver(commands.in(), commands.out(), new ProjectInterface() {
            @Override
            public boolean loadProject(String projectDirectoryCandidate) {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(projectDirectoryCandidate);
                    return prjDir != null && ProjectManager.getDefault().findProject(prjDir) != null;
                } catch (IOException | IllegalArgumentException ex) {
                    throw RemoteInvocation.sneakyThrows(ex);
                }
            }

            @Override
            public String[] getSupportedActions(String projectDirectory) {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(projectDirectory);
                    Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                    if (prj != null) {
                       ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                       if (ap != null) {
                           return ap.getSupportedActions();
                       }
                    }

                    return new String[0];
                } catch (IOException | IllegalArgumentException ex) {
                    throw RemoteInvocation.sneakyThrows(ex);
                }
            }

            @Override
            public boolean isActionEnabled(String projectDirectory, String command, String lookupSelectedFileObjectPath) {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(projectDirectory);
                    Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                    if (prj != null) {
                       ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                       if (ap != null) {
                            List<Object> context = new ArrayList<>();
                            if (lookupSelectedFileObjectPath != null) {
                                context.add(Utils.resolveLocalPath(lookupSelectedFileObjectPath));
                            }
                            return ap.isActionEnabled(command, Lookups.fixed(context.toArray()));
                       }
                    }
                    return false;
                } catch (IOException | IllegalArgumentException ex) {
                    throw RemoteInvocation.sneakyThrows(ex);
                }
            }

            @Override
            public void invokeAction(String projectDirectory, String command, String lookupSelectedFileObjectPath) {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(projectDirectory);
                    Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                    if (prj != null) {
                       ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                       if (ap != null) {
                            List<Object> context = new ArrayList<>();
                            if (lookupSelectedFileObjectPath != null) {
                                context.add(Utils.resolveLocalPath(lookupSelectedFileObjectPath));
                            }
                            ap.invokeAction(command, Lookups.fixed(context.toArray()));
                       }
                    }
                } catch (IOException | IllegalArgumentException ex) {
                    throw RemoteInvocation.sneakyThrows(ex);
                }
            }

            @Override
            public String[] getGenericSourceGroups(String projectDirectory) {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(projectDirectory);
                    Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                    if (prj != null) {
                        return Arrays.stream(ProjectUtils.getSources(prj).getSourceGroups(Sources.TYPE_GENERIC))
                                     .map(sg -> sg.getRootFolder())
                                     .map(root -> "/" + root.getPath())
                                     .toArray(String[]::new);
                    }
                    return new String[0];
                } catch (IOException | IllegalArgumentException ex) {
                    throw RemoteInvocation.sneakyThrows(ex);
                }
            }
        });
        LspTreeViewServiceImpl service = new LspTreeViewServiceImpl();
        Launcher<NodeCallback> launcher = new Launcher.Builder<NodeCallback>().setLocalService(service).setRemoteInterface(NodeCallback.class).setInput(projectView.in()).setOutput(projectView.out()).create();
        service.setCallback(launcher.getRemoteProxy());
        launcher.startListening();
    }

}
