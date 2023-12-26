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
package org.netbeans.modules.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;

/**
 * XXX: should handle exceptions!!!
 */
public class AsynchronousConnection {

    private static final Logger LOG = Logger.getLogger(AsynchronousConnection.class.getName());

    public static class Sender {
        private final InputStream in;
        private final OutputStream out;
        private final Map<Integer, PendingRequest<Object>> id2PendingRequest = new HashMap<>();
        private final AtomicInteger nextId = new AtomicInteger();
        private final RequestProcessor WORKER = new RequestProcessor(Sender.class.getName(), 1, false, false); //TODO: virtual thread!

        public Sender(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
            WORKER.post(() -> {
                try {
                    while (true) {
                        int id = Utils.readInt(in);
                        int size = Utils.readInt(in);
                        byte[] data = in.readNBytes(size);
                        PendingRequest<Object> request;

                        synchronized (id2PendingRequest) {
                            request = id2PendingRequest.remove(id);
                        }

                        if (request == null) {
                            LOG.log(Level.SEVERE, "No pending request number {0}", id);
                        } else {
                            Object value = Utils.gson.fromJson(new String(data, StandardCharsets.UTF_8), request.responseType);
                            request.pending.complete(value);
                        }
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
        }

        private void write(int id, Enum<?> messageKind, Object message) throws IOException {
            byte[] kindBytes = messageKind.name().getBytes(StandardCharsets.UTF_8);
            byte[] messageBytes = Utils.gson.toJson(message).getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[kindBytes.length + messageBytes.length + 12];
            Utils.writeInt(output, 0, id);
            Utils.writeInt(output, 4, kindBytes.length);
            System.arraycopy(kindBytes, 0, output, 8, kindBytes.length);
            Utils.writeInt(output, 8 + kindBytes.length, messageBytes.length);
            System.arraycopy(messageBytes, 0, output, 12 + kindBytes.length, messageBytes.length);
            synchronized (out) {
                out.write(output);
                out.flush();
            }
        }

        public <R> CompletableFuture<R> sendAndReceive(Enum<?> task, Object request, Class<R> responseType) throws IOException {
            int id = nextId.getAndIncrement();
            CompletableFuture<R> result = new CompletableFuture<>();

            synchronized (id2PendingRequest) {
                id2PendingRequest.put(id, new PendingRequest<>((CompletableFuture<Object>) result, (Class<Object>) responseType));
            }

            write(id, task, request);

            return result;
        }

        private record PendingRequest<R>(CompletableFuture<R> pending, Class<R> responseType) {}
    }

    public static <E extends Enum<E>, P, R> Task startReceiver(InputStream in, OutputStream out, Class<E> messageTypeClass, Function<E, Class<P>> messageType2DataClass, Function<P, CompletableFuture<R>> function) {
        RequestProcessor worker = new RequestProcessor(AsynchronousConnection.class.getName() + "-receiver", 1, false, false); //TODO: virtual thread!
        return worker.post(() -> {
            try {
                while (true) {
                    int id = Utils.readInt(in);
                    int kindSize = Utils.readInt(in);
                    byte[] kindBytes = in.readNBytes(kindSize);
                    int dataSize = Utils.readInt(in);
                    byte[] dataBytes = in.readNBytes(dataSize);
                    String kindName = new String(kindBytes, StandardCharsets.UTF_8);
                    Class<P> expectedType = messageType2DataClass.apply(Enum.valueOf(messageTypeClass, kindName));
                    P dataValue = Utils.gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), expectedType);
                    function.apply(dataValue).thenAccept(r -> {
                        byte[] messageBytes = Utils.gson.toJson(r).getBytes(StandardCharsets.UTF_8);
                        byte[] output = new byte[messageBytes.length + 8];
                        Utils.writeInt(output, 0, id);
                        Utils.writeInt(output, 4, messageBytes.length);
                        System.arraycopy(messageBytes, 0, output, 8, messageBytes.length);
                        synchronized (out) {
                            try {
                                out.write(output);
                                out.flush();
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    });
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }
}
