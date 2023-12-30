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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.netbeans.api.project.Project;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider.LanguageServerDescription;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 */
public class LSPHandler {
    private final StreamMultiplexor mainConnection;
    private final Sender connection;
    private final Map<Integer, LanguageServerDescription> serverId2Description = new HashMap<>();

    public LSPHandler(InputStream in, OutputStream out) {
        this.mainConnection = new StreamMultiplexor(in, out);
        Streams controlChannels = mainConnection.getStreamsForChannel(0);
        this.connection = new Sender(controlChannels.in(), controlChannels.out());
    }

    public LanguageServerDescription getServer(Lookup context) {
        Project prj = context.lookup(Project.class);

        if (prj == null) {
            return null;
        }

        try {
            String relativePath = "/" + prj.getProjectDirectory().getPath();
            GetServerResponse response = connection.sendAndReceive(Task.GET_SERVER, new GetServerRequest(relativePath), GetServerResponse.class).get();
            return serverId2Description.computeIfAbsent(response.serverId, id -> {
                Streams streams = mainConnection.getStreamsForChannel(response.dataChannelId);
                return LanguageServerDescription.create(streams.in(), streams.out(), null);
            });
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    public static class GetServerRequest {
        public String projectPath;

        public GetServerRequest() {
        }

        public GetServerRequest(String projectPath) {
            this.projectPath = projectPath;
        }

    }

    public static class GetServerResponse {
        public int serverId;
        public int dataChannelId;

        public GetServerResponse() {
        }

        public GetServerResponse(int serverId, int dataChannelId) {
            this.serverId = serverId;
            this.dataChannelId = dataChannelId;
        }

    }

    public enum Task {
        GET_SERVER;
    }

}
