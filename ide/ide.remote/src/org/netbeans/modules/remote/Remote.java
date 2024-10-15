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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.netbeans.modules.remote.agent.io.ToClientIOProvider;
import org.netbeans.modules.remote.ide.fs.io.IOHandler;
import org.netbeans.spi.io.InputOutputProvider;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * TODO: test exceptional states!!
 */
public class Remote {

    private static final Logger LOG = Logger.getLogger(Remote.class.getName());

    //TODO: state - connecting, failed, connected, etc.
    private final StreamMultiplexor multiplexor;
    private final Sender asynchronousSendAndReceive;
    private final AtomicInteger channel = new AtomicInteger(2);

    public Remote(InputStream in, OutputStream out) {
        this.multiplexor = new StreamMultiplexor(in, out);
        Streams controlChannel = multiplexor.getStreamsForChannel(0);
        Streams ioChannel = multiplexor.getStreamsForChannel(1);
        asynchronousSendAndReceive = new Sender(controlChannel.in(), controlChannel.out());
        IOHandler.run(ioChannel);
    }

    public Streams runService(String serviceName) throws IOException, ExecutionException {
        int newChannel = channel.getAndIncrement();
        Streams result = multiplexor.getStreamsForChannel(newChannel);
        CompletableFuture<ServiceRan> received = asynchronousSendAndReceive.sendAndReceive(Task.RUN_SERVICE, new RunService(serviceName), ServiceRan.class);

        while (true) {
            try {
                received.get();
                break;
            } catch (InterruptedException ex) {
                LOG.log(Level.FINE, null, ex);
            }
        }

        return result;
    }

    public enum Task {
        RUN_SERVICE,;
    }

    public static class RunService {
        public String serviceName;

        public RunService() {
        }

        public RunService(String serviceName) {
            this.serviceName = serviceName;
        }

    }

//    public static class RunService {
//        public final String serviceName;
//
//        public RunService(String serviceName) {
//            this.serviceName = serviceName;
//        }
//
//    }
//
    public static class ServiceRan {

    }

    public static RequestProcessor.Task runAgent(InputStream in, OutputStream out) {
        StreamMultiplexor multiplexor = new StreamMultiplexor(in, out);
        Streams controlChannel = multiplexor.getStreamsForChannel(0);
        Streams ioChannel = multiplexor.getStreamsForChannel(1);
        ToClientIOProvider ioProvider = new ToClientIOProvider(ioChannel);
        AtomicInteger nextChannel = new AtomicInteger(2);
        RequestProcessor.Task[] agentTask = new RequestProcessor.Task[1];
        Lookup inputOutputLookup = Lookups.fixed(ioProvider);
        ProxyLookup lookupWithContext = new ProxyLookup(Lookups.exclude(Lookup.getDefault(), InputOutputProvider.class, org.openide.windows.IOProvider.class), inputOutputLookup);
        Lookups.executeWith(lookupWithContext, () -> {
            agentTask[0] = new ReceiverBuilder<Task>(controlChannel.in(), controlChannel.out(), Task.class)
                   .addHandler(Task.RUN_SERVICE, RunService.class, value -> {
                //TODO: asynchronously
                CompletableFuture<Object> result = new CompletableFuture<Object>();
                for (Service s : Lookup.getDefault().lookupAll(Service.class)) {
                    if (value.serviceName.equals(s.name())) {
                        Streams thisServiceStreams = multiplexor.getStreamsForChannel(nextChannel.getAndIncrement());
                        s.run(thisServiceStreams.in(), thisServiceStreams.out());
                        result.complete(new ServiceRan());
                        return result;
                    }
                }
                result.completeExceptionally(new IllegalStateException("Cannot find service name " + value.serviceName));
                return result;
            }).startReceiver();
        });
        return agentTask[0];
    }
}
