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
import java.util.Arrays;
import java.util.List;
import static junit.framework.TestCase.assertEquals;
import org.netbeans.junit.NbTestCase;

/**
 *
 */
public class RemoteInvocationTest extends NbTestCase {

    public RemoteInvocationTest(String name) {
        super(name);
    }

    public void testCommunication() throws Throwable {
        var server = new ServerSocket(0);
        var ran = new boolean[1];
        var connectThread = new Thread(() -> {
            try {
                Socket accepted = server.accept();
                RemoteInvocation.receiver(accepted.getInputStream(), accepted.getOutputStream(), new Invocation() {
                    @Override
                    public String echo(String i) {
                        return i;
                    }
                    @Override
                    public String echo(String i, List<String> list) {
                        return i + ":" + list.toString();
                    }
                    @Override
                    public void run() {
                        ran[0] = true;
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        connectThread.start();
        var clientSocket = new Socket(server.getInetAddress(), server.getLocalPort());
        var sender = RemoteInvocation.caller(clientSocket.getInputStream(), clientSocket.getOutputStream(), Invocation.class);
        assertEquals("a", sender.echo("a"));
        assertEquals("a:[b, c]", sender.echo("a", Arrays.asList("b", "c")));
        sender.run();
        assertTrue(ran[0]);
    }

    public interface Invocation {
        public String echo(String i);
        public String echo(String i, List<String> list);
        public void run();
    }
}
