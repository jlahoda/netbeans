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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.openide.util.Pair;

/**
 *
 */
public class StreamMultiplexorTest extends NbTestCase {

    public StreamMultiplexorTest(String name) {
        super(name);
    }

    public void testSimpleDataPassing() throws Throwable {
        var ends = createEnds();
        StreamMultiplexor end1 = ends.first();
        StreamMultiplexor end2 = ends.second();
        Streams channel0End1 = end1.getStreamsForChannel(0);
        Streams channel0End2 = end2.getStreamsForChannel(0);
        channel0End1.out().write("hello!".getBytes(StandardCharsets.UTF_8));
        channel0End1.out().flush();
        assertEquals("hello!", new String(channel0End2.in().readNBytes(6), StandardCharsets.UTF_8));
        String longText = Stream.generate(() -> 'a').limit(17 * 1024 + 100).map(c -> "" + c).collect(Collectors.joining(""));
        channel0End2.out().write(longText.getBytes(StandardCharsets.UTF_8));
        channel0End2.out().flush();
        assertEquals(longText, new String(channel0End1.in().readNBytes(longText.length()), StandardCharsets.UTF_8));
    }

    public void testParallel() throws Throwable {
        String longText = Stream.generate(() -> 'a').limit(17 * 1024 + 100).map(c -> "" + c).collect(Collectors.joining(""));
        byte[] data = longText.getBytes(StandardCharsets.UTF_8);

        var ends = createEnds();
        StreamMultiplexor end1 = ends.first();
        StreamMultiplexor end2 = ends.second();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 100; i++ ){
            Streams channelEnd1 = end1.getStreamsForChannel(i);
            Streams channelEnd2 = end2.getStreamsForChannel(i);
            Thread t1 = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        channelEnd1.out().write(data);
                        channelEnd1.out().flush();
                        byte[] read = new byte[data.length];
                        channelEnd1.in().readNBytes(read, 0, read.length);
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        byte[] read = new byte[data.length];
                        channelEnd2.in().readNBytes(read, 0, read.length);
                        channelEnd2.out().write(read);
                        channelEnd2.out().flush();
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            t1.start(); workers.add(t1);
            t2.start(); workers.add(t2);
        }
        for (Thread w : workers) {
            w.join();
        }
    }

    private static Pair<StreamMultiplexor, StreamMultiplexor> createEnds() throws Throwable {
        var server = new ServerSocket(0);
        var serverMultiplexor = new AtomicReference<StreamMultiplexor>();
        var exception = new AtomicReference<Throwable>();
        var connectThread = new Thread(() -> {
            try {
                Socket accepted = server.accept();
                serverMultiplexor.set(new StreamMultiplexor(accepted.getInputStream(), accepted.getOutputStream()));
            } catch (Throwable t) {
                exception.set(t);
            }
        });
        connectThread.start();
        var clientSocket = new Socket(server.getInetAddress(), server.getLocalPort());
        connectThread.join();
        if (exception.get() != null) {
            throw exception.get();
        }
        return Pair.of(new StreamMultiplexor(clientSocket.getInputStream(), clientSocket.getOutputStream()), serverMultiplexor.get());
    }
}
