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

import java.net.ServerSocket;
import java.net.Socket;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.remote.ide.RemoteManager.SshRemoteDescription;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;

/**
 *
 * @author lahvac
 */
public class RemoteTest extends NbTestCase {

    public RemoteTest(String name) {
        super(name);
    }

    public void testRunAgent() throws Throwable {
        var server = new ServerSocket(0);
        var connectThread = new Thread(() -> {
            try {
                Socket accepted = server.accept();
                Remote.runAgent(accepted.getInputStream(), accepted.getOutputStream());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        connectThread.start();
        var clientSocket = new Socket(server.getInetAddress(), server.getLocalPort());
        var remote = new Remote(clientSocket.getInputStream(), clientSocket.getOutputStream());
        Streams fsStreams = remote.runService("fs");
        RemoteFileSystem fs = new RemoteFileSystem(new SshRemoteDescription("", "", "", ""), fsStreams.out(), fsStreams.in());
        assertTrue(fs.getRoot().getChildren().length > 0);
    }

}
