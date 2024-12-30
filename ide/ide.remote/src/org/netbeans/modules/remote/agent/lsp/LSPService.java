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
package org.netbeans.modules.remote.agent.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.lsp.LSPHandlerWrapper.LSPHandler;
import org.netbeans.modules.remote.ide.lsp.LSPHandlerWrapper.ServerDescription;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 */
@ServiceProvider(service=Service.class)
public class LSPService implements Service {

    @Override
    public String name() {
        return "lsp";
    }

    @Override
    public void run(InputStream in, OutputStream out) {
        StreamMultiplexor mainConnection = new StreamMultiplexor(in, out);
        Streams controlChannels = mainConnection.getStreamsForChannel(0);
        AtomicInteger nextLanguageServer = new AtomicInteger(2);
        AtomicInteger nextChannel = new AtomicInteger(2);

        RemoteInvocation.receiver(controlChannels.in(), controlChannels.out(), new LSPHandler() {
            private final Map<Project, ServerDescription> project2JavaDescriptions = new WeakHashMap<>();
            @Override
            public ServerDescription startServer(String projectPath, String mimePath) {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(projectPath);
                    Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;

                    if (prj == null) {
                        return null;
                    }

                    if ("text/x-java".equals(mimePath)) {
                        return project2JavaDescriptions.computeIfAbsent(prj, p -> {
                            try {
                                int serverNumber = nextLanguageServer.incrementAndGet();
                                int channelNumber = nextChannel.incrementAndGet();
                                Streams javaChannel = mainConnection.getStreamsForChannel(channelNumber);

                                Lookup.getDefault().lookup(StartJavaServerHack.class).start(javaChannel.in(), javaChannel.out(), null, null);
                                return new ServerDescription(serverNumber, channelNumber);
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        });
                    }
                    return null;
//                    for (LanguageServerProvider provider : Lookup.getDefault().lookupAll(LanguageServerProvider.class)) {
//                        //TODO: multiple servers!
//                        provider.startServer(Lookups.fixed(prj));
//                    }
//                    result.complete(new LoadProjectResponse(isProject));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        });
    }

    public interface StartJavaServerHack {
        public void start(InputStream lspIn, OutputStream lspOut,
                          InputStream dapIn, OutputStream dapOut) throws IOException;
    }
}
