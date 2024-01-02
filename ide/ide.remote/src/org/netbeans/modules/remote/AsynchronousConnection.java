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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.remote.Utils.EndOfInput;
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
                Exception exceptionalResult;
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
                } catch (EndOfInput ex) {
                    exceptionalResult = ex;
                } catch (IOException ex) {
                    exceptionalResult = ex;
                    Exceptions.printStackTrace(ex);
                }

                synchronized (id2PendingRequest) {
                    for (PendingRequest<Object> request : id2PendingRequest.values()) {
                        request.pending.completeExceptionally(exceptionalResult);
                    }
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

    public static final class ReceiverBuilder<E extends Enum<E>> {
        private final InputStream in;
        private final OutputStream out;
        private final Class<E> messageTypeClass;
        private final Map<E, Handler<?>> messageType2Handler = new HashMap<>();

        public ReceiverBuilder(InputStream in, OutputStream out, Class<E> messageTypeClass) {
            this.in = in;
            this.out = out;
            this.messageTypeClass = messageTypeClass;
        }

        public <P> ReceiverBuilder<E> addHandler(E e, Class<P> messageTypeDataClass, Function<P, CompletableFuture<Object>> handler) {
            if (messageType2Handler.containsKey(e)) {
                throw new IllegalArgumentException("Cannot redefine handler for: " + e);
            }
            messageType2Handler.put(e, new Handler<P>(messageTypeDataClass, handler));
            return this;
        }

        public Task startReceiver() {
            if (messageTypeClass.getEnumConstants().length != messageType2Handler.size()) {
                throw new IllegalStateException("Not all inputs are handled!"); //TODO: print the missing constants!
            }

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
                        E kind = Enum.valueOf(messageTypeClass, kindName);
                        Handler<?> handler = messageType2Handler.get(kind);
                        Object dataValue = Utils.gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), handler.messageTypeDataClass);
                        handler.run(dataValue).thenAccept(r -> {
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

        private static final class Handler<P> {
            private final Class<P> messageTypeDataClass;
            private final Function<P, CompletableFuture<Object>> handler;

            public Handler(Class<P> messageTypeDataClass, Function<P, CompletableFuture<Object>> handler) {
                this.messageTypeDataClass = messageTypeDataClass;
                this.handler = handler;
            }
            public CompletableFuture<?> run(Object value) {
                return handler.apply((P) value);
            }
        }
    }
}
