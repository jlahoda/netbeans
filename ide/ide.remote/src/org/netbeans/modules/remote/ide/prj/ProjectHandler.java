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
import java.util.concurrent.atomic.AtomicInteger;
import org.netbeans.api.io.IOProvider;
import org.netbeans.api.io.InputOutput;
import org.netbeans.api.io.OutputWriter;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 */
public final class ProjectHandler {

    private final ProjectInterface outgoingProjectInterface;

    public ProjectHandler(InputStream in, OutputStream out) {
        StreamMultiplexor projectMultiplexor = new StreamMultiplexor(in, out);
        Streams commands = projectMultiplexor.getStreamsForChannel(0);
        Streams ioControl = projectMultiplexor.getStreamsForChannel(1);
        AtomicInteger nextChannel = new AtomicInteger(2);
        this.outgoingProjectInterface = RemoteInvocation.caller(commands.in(), commands.out(), ProjectInterface.class);
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
        String relativePath = "/" + folder.getPath();
        return outgoingProjectInterface.loadProject(relativePath);
    }

    public String[] getSupportedActions(FileObject projectDir) {
        String relativePath = "/" + projectDir.getPath();
        return outgoingProjectInterface.getSupportedActions(relativePath);
    }

    public boolean isActionEnabled(FileObject projectDir, String command, FileObject selectedFile) {
        String relativeProjectPath = "/" + projectDir.getPath();
        String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;

        return outgoingProjectInterface.isActionEnabled(relativeProjectPath, command, relativeSelectedPath);
    }

    public void invokeAction(FileObject projectDir, String command, FileObject selectedFile) {
        String relativeProjectPath = "/" + projectDir.getPath();
        String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;
        outgoingProjectInterface.invokeAction(relativeProjectPath, command, relativeSelectedPath);
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

    public interface ProjectInterface {
        public boolean loadProject(String projectDirectoryCandidate);
        public String[] getSupportedActions(String projectDirectory);
        public boolean isActionEnabled(String projectDirectory, String command, String lookupSelectedFileObjectPath);
        public void invokeAction(String projectDirectory, String command, String lookupSelectedFileObjectPath);
    }
}
