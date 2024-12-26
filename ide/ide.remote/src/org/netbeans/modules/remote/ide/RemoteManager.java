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
package org.netbeans.modules.remote.ide;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.netbeans.modules.remote.Remote;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.Utils;
import org.openide.util.Exceptions;

/**
 *
 */
public class RemoteManager {

    private static final RemoteManager INSTANCE = new RemoteManager();

    public static RemoteManager getDefault() {
        return INSTANCE;
    }

    private final Map<RemoteDescription, Remote> description2Remote = new HashMap<>();
    private final Map<Remote, Map<String, Object>> remote2Services = new HashMap<>();

    private RemoteManager() {
    }

    public Remote getRemote(RemoteDescription remoteDescription) {
        return getRemote(remoteDescription, false);
    }

    public synchronized void registerTransientRemote(RemoteDescription remoteDescription) {
        getRemote(remoteDescription, true);
    }

    private synchronized Remote getRemote(RemoteDescription remoteDescription, boolean initTransient) {
        //allow "asynchronous" start:
        Remote remote = description2Remote.get(remoteDescription);

        if (remote != null) {
            return remote;
        }

        if (remoteDescription instanceof SshRemoteDescription sshDesc) {
            List<String> connectionOptions = new ArrayList<>();

            connectionOptions.add("ssh");
            connectionOptions.addAll(Arrays.asList(sshDesc.connectionString().split(" +")));
            connectionOptions.add(sshDesc.installDir + "/bin/netbeans.remote");
            connectionOptions.add("--start-remote-agent=shutdown");
            connectionOptions.add("--nogui");
            connectionOptions.add("--userdir");
            connectionOptions.add(sshDesc.userdir);
            connectionOptions.addAll(Arrays.asList(sshDesc.additionalOptions().split(" +")));
    //        connectionOptions.add("-J-agentlib:jdwp=transport=dt_socket,suspend=y,server=y,address=8000,quiet=y");

            try {
    //            Process ssh = new ProcessBuilder(connectionOptions).redirectError(Redirect.INHERIT).start();
                Process ssh = new ProcessBuilder(connectionOptions).redirectError(Redirect.DISCARD).start();
                remote = new Remote(ssh.getInputStream(), ssh.getOutputStream());
                description2Remote.put(sshDesc, remote);
                return remote;
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }
        } else if (remoteDescription instanceof Running running) {
            if (running.transientId != null && !initTransient) {
                return null;
            }

            try {
                Socket client = new Socket(running.hostname(), running.port());
                remote = new Remote(client.getInputStream(), client.getOutputStream());
                description2Remote.put(remoteDescription, remote);
                return remote;
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }
        } else {
            throw new IllegalStateException("Unknown remote description class: " + remoteDescription.getClass().getName());
        }
    }

    public synchronized <T> T getService(RemoteDescription remoteDescription, String serviceName, Function<Streams, T> createService) {
        Remote remote = getRemote(remoteDescription);

        if (remote == null) {
            return null;
        }

        return (T) remote2Services.computeIfAbsent(remote, x -> new HashMap<>())
                                  .computeIfAbsent(serviceName, r -> {
                                      try {
                                          Streams streams = remote.runService(serviceName);
                                          return createService.apply(streams);
                                      } catch (IOException | ExecutionException ex) {
                                          Exceptions.printStackTrace(ex);
                                          return null;
                                      }
                                  });
    }

    public sealed interface RemoteDescription {
        public String encoded();
        public String shortName();
    }

    public record SshRemoteDescription(String connectionString, String installDir, String userdir, String additionalOptions, String encoded) implements RemoteDescription {

        public SshRemoteDescription(String connectionString, String installDir, String userdir,String additionalOptions) {
            this(connectionString, installDir, userdir, additionalOptions, encode(connectionString, installDir, additionalOptions, userdir));
        }

        private static String encode(String connectionString, String installDir, String additionalOptions, String userdir) {
            Map<String, String> values = new HashMap<>();
            values.put("connectionString", connectionString);
            values.put("installDir", installDir);
            values.put("userdir", userdir);
            values.put("additionalOptions", additionalOptions);
            return Utils.gson.toJson(values);
        }

        @Override
        public String shortName() {
            return connectionString();
        }

        public static SshRemoteDescription createInstance(String encoded) {
            Map<String, String> values = Utils.gson.fromJson(encoded, HashMap.class);
            return new SshRemoteDescription(values.get("connectionString"), values.get("installDir"), values.get("userdir"), values.get("additionalOptions"), encoded);
        }
    }

    public record Running(String hostname, int port, Integer transientId, String encoded) implements RemoteDescription {

        public Running(String hostname, int port, Integer transientId) {
            this(hostname, port, transientId, encode(hostname, port, transientId));
        }
        
        private static String encode(String hostname, int port, Integer transientId) {
            return hostname + ":" + port + (transientId != null ? ";" + transientId : "");
        }

        @Override
        public String shortName() {
            return hostname + ":" + port;
        }

        public static Running createInstance(String encoded) {
            String[] outterParts = encoded.split(";");
            String[] parts = outterParts[0].split(":");

            return new Running(parts[0], Integer.parseInt(parts[1]), outterParts.length > 1 ? Integer.parseInt(outterParts[1]) : null, encoded);
        }
    }
}
