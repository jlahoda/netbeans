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
package org.netbeans.modules.remote.ide.prj;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.netbeans.api.io.IOProvider;
import org.netbeans.api.io.InputOutput;
import org.netbeans.api.io.OutputWriter;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 */
public final class ProjectHandler {

    private final Sender connection;

    public ProjectHandler(InputStream in, OutputStream out) {
        StreamMultiplexor projectMultiplexor = new StreamMultiplexor(in, out);
        Streams commands = projectMultiplexor.getStreamsForChannel(0);
        Streams ioControl = projectMultiplexor.getStreamsForChannel(1);
        AtomicInteger nextChannel = new AtomicInteger(2);
        this.connection = new Sender(commands.in(), commands.out());
        Map<Integer, InputOutput> channel2InputOutput = new HashMap<>(); //TODO: clear!!
        new ReceiverBuilder<>(ioControl.in(), ioControl.out(), IOTask.class)
                .addHandler(IOTask.GET_IO, GetIORequest.class, io -> {
                    InputOutput inputOutput = IOProvider.getDefault().getIO(io.name, true);
                    int channel = nextChannel.getAndIncrement();
                    channel2InputOutput.put(channel, inputOutput);
                    Streams streams = projectMultiplexor.getStreamsForChannel(channel);
                    new RequestProcessor(ProjectHandler.class.getName(), 1, false, false).post(() -> {
                        try {
                            Reader ioIn = new InputStreamReader(streams.in()); //TODO: encoding!(?)
                            OutputWriter ioOut = inputOutput.getOut();
                            int r;
                            StringBuilder wrote = new StringBuilder();
                            while ((r = ioIn.read()) != (-1)) {
                                ioOut.write(r);
                                wrote.append((char) r);
                                if (r == '\n') {
                                    ioOut.flush();
                                }
                            }
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    });
                    CompletableFuture<Object> result = new CompletableFuture<>();
                    result.complete(channel);
                    return result;
                }).startReceiver();
    }

    public boolean loadProject(FileObject folder) {
        try {
            String relativePath = "/" + folder.getPath();
            LoadProjectResponse response = connection.sendAndReceive(Task.LOAD_PROJECT, new PathProjectRequest(relativePath), LoadProjectResponse.class).get();
            return response.isProject;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    public String[] getSupportedActions(FileObject projectDir) {
        try {
            String relativePath = "/" + projectDir.getPath();
            String[] actions = connection.sendAndReceive(Task.GET_SUPPORTED_ACTIONS, new PathProjectRequest(relativePath), String[].class).get();
            return actions;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return new String[0];
        }
    }

    public boolean isActionEnabled(FileObject projectDir, String command, FileObject selectedFile) {
        try {
            String relativeProjectPath = "/" + projectDir.getPath();
            String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;
            boolean enabled = connection.sendAndReceive(Task.IS_ENABLED_ACTIONS, new ContextBasedActionRequest(relativeProjectPath, command, relativeSelectedPath), Boolean.class).get();
            return enabled;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    public void invokeAction(FileObject projectDir, String command, FileObject selectedFile) {
        try {
            String relativeProjectPath = "/" + projectDir.getPath();
            String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;
            connection.sendAndReceive(Task.INVOKE_ACTIONS, new ContextBasedActionRequest(relativeProjectPath, command, relativeSelectedPath), Boolean.class).get();
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public static class PathProjectRequest {
        public String path;

        public PathProjectRequest() {
        }

        public PathProjectRequest(String path) {
            this.path = path;
        }
    }

    public static class ContextBasedActionRequest {
        public String projectPath;
        public String action;
        public String selectedFileObjectPath;

        public ContextBasedActionRequest() {
        }

        public ContextBasedActionRequest(String projectPath, String action, String selectedFileObjectPath) {
            this.projectPath = projectPath;
            this.action = action;
            this.selectedFileObjectPath = selectedFileObjectPath;
        }


    }

    public static class LoadProjectResponse {
        public boolean isProject;

        public LoadProjectResponse() {
        }

        public LoadProjectResponse(boolean isProject) {
            this.isProject = isProject;
        }
    }

    public enum Task {
        LOAD_PROJECT,
        GET_SUPPORTED_ACTIONS,
        IS_ENABLED_ACTIONS,
        INVOKE_ACTIONS,
    }

    public enum IOTask {
        GET_IO,
    }

    public static class GetIORequest {
        public String name;

        public GetIORequest() {
        }

        public GetIORequest(String name) {
            this.name = name;
        }

    }
}
