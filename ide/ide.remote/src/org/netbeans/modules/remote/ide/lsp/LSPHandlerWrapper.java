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
package org.netbeans.modules.remote.ide.lsp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.api.project.Project;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider.LanguageServerDescription;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.openide.util.Lookup;

/**
 *
 */
public class LSPHandlerWrapper {
    private final StreamMultiplexor mainConnection;
    private final LSPHandler handler;
    private final Map<Integer, LanguageServerDescription> serverId2Description = new HashMap<>();

    public LSPHandlerWrapper(InputStream in, OutputStream out) {
        this.mainConnection = new StreamMultiplexor(in, out);
        Streams controlChannels = mainConnection.getStreamsForChannel(0);
        this.handler = RemoteInvocation.caller(controlChannels.in(), controlChannels.out(), LSPHandler.class);
    }

    public LanguageServerDescription getServer(Lookup context, String mimePath) {
        Project prj = context.lookup(Project.class);

        if (prj == null) {
            return null;
        }

        String projectPath = "/" + prj.getProjectDirectory().getPath();
        ServerDescription serverDescription = handler.startServer(projectPath, mimePath);

        if (serverDescription == null) {
            return null;
        }

        return serverId2Description.computeIfAbsent(serverDescription.serverId, id -> {
            Streams streams = mainConnection.getStreamsForChannel(serverDescription.channelId);
            return LanguageServerDescription.create(streams.in(), streams.out(), null);
        });
    }

    public interface LSPHandler {
        public ServerDescription startServer(String projectPath, String mimePath);
    }

    public static class ServerDescription {
        public int serverId;
        public int channelId;

        public ServerDescription() {
        }

        public ServerDescription(int serverId, int channelId) {
            this.serverId = serverId;
            this.channelId = channelId;
        }

    }

}
