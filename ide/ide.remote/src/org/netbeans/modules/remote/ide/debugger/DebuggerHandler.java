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
package org.netbeans.modules.remote.ide.debugger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.netbeans.modules.lsp.client.debugger.api.DAPConfiguration;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.lsp.client.debugger.api.DAPConfiguration.URLPathConvertor;
import org.netbeans.modules.remote.ide.RemoteManager.RemoteDescription;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;

public class DebuggerHandler {

    public static void startListenOn(RemoteDescription remoteDescription, Streams debuggerChannel) {
        StreamMultiplexor mainChannel = new StreamMultiplexor(debuggerChannel.in(), debuggerChannel.out());
        Streams controlChannel = mainChannel.getStreamsForChannel(0);
        new ReceiverBuilder<Task>(controlChannel.in(), controlChannel.out(), Task.class)
                .addHandler(Task.START_DEBUGGER_ATTACH, Attach.class, attach -> {
                    System.err.println("start debugger attach");
                    CompletableFuture<Object> cf = new CompletableFuture<>();
                    try {
                        Streams channel = mainChannel.getStreamsForChannel(attach.channel);
                        RemoteFileSystem rfs = RemoteFileSystem.getRemoteFileSystem(remoteDescription);
                        String rootURL = rfs.getRoot().toURL().toString();
                        System.err.println("rootURL: " + rootURL);
                        URLPathConvertor fileConvertor = new URLPathConvertor() {
                            @Override
                            public String toPath(String url) {
                                if (url.startsWith(rootURL)) {
                                    return url.substring(rootURL.length() - 1);
                                }
                                return null;
                            }

                            @Override
                            public String toURL(String path) {
                                return rootURL.substring(0, rootURL.length() - 1) + path;
                            }
                        };
                        DAPConfiguration.create(channel.in(), channel.out()).setURLPathConvertor(fileConvertor).addConfiguration(Map.of("type","java+", "request", "attach", "hostName", attach.hostName, "port", attach.port, "properties", attach.properties)).attach();
                        cf.complete(null);
                        System.err.println("complete");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        cf.completeExceptionally(ex);
                    }
                    return cf;
                })
                .startReceiver();
    }

    public static class Attach {
        public int channel;
        public String hostName;
        public int port;
        public Map<String, Object> properties;

        public Attach() {
        }

        public Attach(int channel, String hostName, int port, Map<String, Object> properties) {
            this.channel = channel;
            this.hostName = hostName;
            this.port = port;
            this.properties = properties;
        }

    }
    public enum Task {
        START_DEBUGGER_ATTACH;
    }
}
