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
package org.netbeans.modules.remote.agent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.remote.Remote;

import org.netbeans.spi.sendopts.Arg;
import org.netbeans.spi.sendopts.ArgsProcessor;
import org.netbeans.spi.sendopts.Description;
import org.netbeans.spi.sendopts.Env;
import org.openide.*;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

public final class ArgsProcessorImpl implements ArgsProcessor {

    private static final RequestProcessor WORKER = new RequestProcessor(ArgsProcessorImpl.class.getName(), 1, false, false); //TODO: virtual!

    @Arg(longName="start-remote-agent", defaultValue = "")
    @Description(shortDescription="#DESC_StartRemoteAgent")
    @Messages("DESC_StartRemoteAgent=Starts remote agent")
    public String remoteAgent;

    @Arg(longName="remote-agent-listen", defaultValue = "")
    @Description(shortDescription="#DESC_RemoteAgentListen")
    @Messages("DESC_RemoteAgentListen=Listen for remote agent connections")
    public String remoteAgentListen;

    @Override
    public void process(Env env) throws CommandException {
        if (remoteAgent != null) {
            Remote.runAgent(env.getInputStream(), env.getOutputStream()).waitFinished();
            if ("shutdown".equals(remoteAgent)) {
                LifecycleManager.getDefault().exit();
            }
        }
        if (remoteAgentListen != null) {
            WORKER.post(() -> {
                try {
                    ServerSocket server = new ServerSocket(Integer.parseInt(remoteAgentListen));
                    System.err.println("Listening for remote agent connections on: " + remoteAgentListen);
                    while (true) {
                        Socket socket = server.accept();
                        Remote.runAgent(socket.getInputStream(), socket.getOutputStream());
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
        }
    }
}

