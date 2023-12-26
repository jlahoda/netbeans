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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.openide.util.Pair;
import org.openide.util.RequestProcessor;

/**
 *
 */
public class AsynchronousConnectionTest extends NbTestCase {

    public AsynchronousConnectionTest(String name) {
        super(name);
    }

    public void testCommunication() throws Throwable {
        var server = new ServerSocket(0);
        var connectThread = new Thread(() -> {
            try {
                Socket accepted = server.accept();
                RequestProcessor worker = new RequestProcessor("test-receiver", 1, false, false); //TODO: virtual thread!
                Random random = new Random();
                AsynchronousConnection.startReceiver(accepted.getInputStream(), accepted.getOutputStream(), MessageType.class, type -> Integer.class, in -> {
                    CompletableFuture<Integer> result = new CompletableFuture<>();
                    worker.post(() -> {
                        result.complete(in);
                    }, random.nextInt(1000));
                    return result;
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        connectThread.start();
        var clientSocket = new Socket(server.getInetAddress(), server.getLocalPort());
        var sender = new Sender(clientSocket.getInputStream(), clientSocket.getOutputStream());
        List<CompletableFuture<Integer>> pending = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            pending.add(sender.sendAndReceive(MessageType.ECHO, i, Integer.class));
        }
        for (int i = 0; i < pending.size(); i++) {
            assertEquals(i, (int) pending.get(i).get());
        }
    }

    public enum MessageType {
        ECHO;
    }
}
