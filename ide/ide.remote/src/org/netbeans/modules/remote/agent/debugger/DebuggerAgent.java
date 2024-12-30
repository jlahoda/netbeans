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
package org.netbeans.modules.remote.agent.debugger;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.ide.debugger.DebuggerHandler.Attach;
import org.netbeans.modules.remote.ide.debugger.DebuggerHandler.Task;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public class DebuggerAgent {

    private final StreamMultiplexor mainChannel;
    private final Sender control;
    private final AtomicInteger nextChannel;

    public DebuggerAgent(Streams debuggerChannel) {
        this.mainChannel = new StreamMultiplexor(debuggerChannel.in(), debuggerChannel.out());
        Streams controlChannel = mainChannel.getStreamsForChannel(0);
        this.control = new Sender(controlChannel.in(), controlChannel.out());
        this.nextChannel = new AtomicInteger(1);
    }

    public PendingDebugger prepareDebugger() {
        int channel = nextChannel.getAndIncrement();
        Streams currentStreams = mainChannel.getStreamsForChannel(channel);
        return new PendingDebugger(currentStreams.in(), currentStreams.out(), channel);
    }

    public final class PendingDebugger {
        public final InputStream in;
        public final OutputStream out;
        private final int channel;

        public PendingDebugger(InputStream in, OutputStream out, int channel) {
            this.in = in;
            this.out = out;
            this.channel = channel;
        }

        public void attachDebugger(String hostName, int localPort, Map<String, Object> properties) { //XXX: more general!
            try {
                control.sendAndReceive(Task.START_DEBUGGER_ATTACH, new Attach(channel, hostName, localPort, properties), Void.class).get();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }

    }

    public interface WrapJavaDebuggerHack {
        public Lookup replaceLookup(Lookup lookup, DebuggerAgent agent);
    }
}
