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

    public synchronized Remote getRemote(RemoteDescription remoteDescription) {
        //allow "asynchronous" start:
        Remote remote = description2Remote.get(remoteDescription);

        if (remote != null) {
            return remote;
        }

        List<String> connectionOptions = new ArrayList<>();

        connectionOptions.add("ssh");
        connectionOptions.addAll(Arrays.asList(remoteDescription.connectionString().split(" +")));
        connectionOptions.add(remoteDescription.installDir);
        connectionOptions.add("--start-remote-agent=shutdown");
        connectionOptions.add("--nogui");
        connectionOptions.add("--userdir");
        connectionOptions.add(remoteDescription.userdir);

        try {
//            Process ssh = new ProcessBuilder(connectionOptions).redirectError(Redirect.INHERIT).start();
            Process ssh = new ProcessBuilder(connectionOptions).redirectError(Redirect.DISCARD).start();
            remote = new Remote(ssh.getInputStream(), ssh.getOutputStream());
            description2Remote.put(remoteDescription, remote);
            return remote;
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    public synchronized <T> T getService(RemoteDescription remoteDescription, String serviceName, Function<Streams, T> createService) {
        Remote remote = getRemote(remoteDescription);
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

    public record RemoteDescription(String connectionString, String installDir, String userdir, String encoded) {

        public RemoteDescription(String connectionString, String installDir, String userdir) {
            this(connectionString, installDir, userdir, encode(connectionString, installDir, userdir));
        }

        private static String encode(String connectionString, String installDir, String userdir) {
            Map<String, String> values = new HashMap<>();
            values.put("connectionString", connectionString);
            values.put("installDir", installDir);
            values.put("userdir", userdir);
            return Utils.gson.toJson(values);
        }

        public static RemoteDescription createInstance(String encoded) {
            Map<String, String> values = Utils.gson.fromJson(encoded, HashMap.class);
            return new RemoteDescription(values.get("connectionString"), values.get("installDir"), values.get("userdir"), encoded);
        }
    }
}
