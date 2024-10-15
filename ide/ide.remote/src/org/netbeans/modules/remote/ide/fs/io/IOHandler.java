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
package org.netbeans.modules.remote.ide.fs.io;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.netbeans.api.io.IOProvider;
import org.netbeans.api.io.InputOutput;
import org.netbeans.api.io.OutputWriter;
import org.netbeans.api.io.ShowOperation;
import org.netbeans.modules.remote.AsynchronousConnection;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.ide.prj.ProjectHandler;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

public class IOHandler {

    public static void run(Streams mainChannel) {
        StreamMultiplexor mainMultiplexor = new StreamMultiplexor(mainChannel.in(), mainChannel.out());
        Streams ioControl = mainMultiplexor.getStreamsForChannel(0);
        AtomicInteger nextChannel = new AtomicInteger(1);
        Map<Integer, InputOutput> channel2InputOutput = new HashMap<>(); //TODO: clear!!
        new AsynchronousConnection.ReceiverBuilder<>(ioControl.in(), ioControl.out(), IOTask.class)
                .addHandler(IOTask.GET_IO, GetIORequest.class, io -> {
                    InputOutput inputOutput = IOProvider.getDefault().getIO(io.name, io.newIO);
                    int channel = nextChannel.getAndIncrement();
                    channel2InputOutput.put(channel, inputOutput);
                    Streams streams = mainMultiplexor.getStreamsForChannel(channel);
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
                })
                .addHandler(IOTask.RESET, IORequest.class, req -> handleIORequest(channel2InputOutput, req, (io, data) -> io.reset()))
                .addHandler(IOTask.SHOW, IORequest.class, req -> handleIORequest(channel2InputOutput, req, (io, data) -> io.show(data.stream().map(o -> ShowOperation.valueOf(o)).collect(Collectors.toSet()))))
                .addHandler(IOTask.CLOSE, IORequest.class, req -> handleIORequest(channel2InputOutput, req, (io, data) -> io.close()))
                .addHandler(IOTask.IS_CLOSED, IORequest.class, req -> handleIORequestWithResult(channel2InputOutput, req, (io, data) -> io.isClosed()))
                .startReceiver();
    }

    private static CompletableFuture<Object> handleIORequest(Map<Integer, InputOutput> channel2InputOutput, IORequest request, BiConsumer<InputOutput, List<String>> runTask) {
        return handleIORequestWithResult(channel2InputOutput, request, (io, data) -> {
            runTask.accept(io, data);
            return 0;
        });
    }

    private static <T> CompletableFuture<Object> handleIORequestWithResult(Map<Integer, InputOutput> channel2InputOutput, IORequest request, BiFunction<InputOutput, List<String>, T> runTask) {
        InputOutput io = channel2InputOutput.get(request.id);
        Object result;

        if (io != null) {
            result = runTask.apply(io, request.data);
        } else {
            //TODO: log!!!
            result = null;
        }

        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        resultFuture.complete(result);
        return resultFuture;
    }

    public enum IOTask {
        GET_IO,
        RESET,
        SHOW,
        CLOSE,
        IS_CLOSED,
    }

    public static class GetIORequest {
        public String name;
        public boolean newIO;

        public GetIORequest() {
        }

        public GetIORequest(String name, boolean newIO) {
            this.name = name;
            this.newIO = newIO;
        }

    }

    public static class IORequest {
        public int id;
        public List<String> data;

        public IORequest() {
        }

        public IORequest(int id) {
            this(id, List.of());
        }

        public IORequest(int id, List<String> data) {
            this.id = id;
            this.data = data;
        }

    }
}
