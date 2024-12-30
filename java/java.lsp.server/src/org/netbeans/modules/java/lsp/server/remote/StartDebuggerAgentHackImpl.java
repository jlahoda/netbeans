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
package org.netbeans.modules.java.lsp.server.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.debugger.jpda.JPDADebuggerStarter;
import org.netbeans.api.debugger.jpda.JPDADebuggerStarter.ConnectionInfo;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.java.lsp.server.ConnectionSpec;
import org.netbeans.modules.java.lsp.server.LspSession;
import org.netbeans.modules.java.lsp.server.debugging.Debugger;
import org.netbeans.modules.remote.agent.debugger.DebuggerAgent;
import org.netbeans.modules.remote.agent.debugger.DebuggerAgent.PendingDebugger;
import org.netbeans.modules.remote.agent.debugger.DebuggerAgent.WrapJavaDebuggerHack;
import org.netbeans.spi.debugger.jpda.JPDADebuggerStarterImplementation;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=WrapJavaDebuggerHack.class)
public class StartDebuggerAgentHackImpl implements WrapJavaDebuggerHack {

    @Override
    public Lookup replaceLookup(Lookup lookup, DebuggerAgent agent) {
        return new ProxyLookup(Lookups.exclude(lookup, JPDADebuggerStarterImplementation.class),
                               Lookups.fixed(new StartDAPDebuggerHack(agent)));
    }

    private static final class StartDAPDebuggerHack implements JPDADebuggerStarterImplementation {

        private static final RequestProcessor WAIT = new RequestProcessor(StartDAPDebuggerHack.class.getName(), /*XXX: should be unlimited and virtual threads??*/100, false, false);
        private final DebuggerAgent agent;

        public StartDAPDebuggerHack(DebuggerAgent agent) {
            this.agent = agent;
        }

        @Override
        public ConnectionInfo startDebugger(Context context) throws IOException, IllegalStateException {
            System.err.println("startdebugger");
            ServerSocket debuggeeSocket = new ServerSocket(0, 1, InetAddress.getLocalHost());
            ServerSocket debuggerSocket = new ServerSocket(0, 1, InetAddress.getLocalHost());
            CountDownLatch connected = new CountDownLatch(2);
            AtomicReference<Socket> debuggee = new AtomicReference<>();
            AtomicReference<Socket> debugger = new AtomicReference<>();
            WAIT.post(() -> {
                try {
                    debuggee.set(debuggeeSocket.accept());
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                System.err.println("debuggee connected");
                connected.countDown();
            });
            WAIT.post(() -> {
                System.err.println("waiting for debugger on: " + debuggerSocket.getLocalPort());
                try {
                    debugger.set(debuggerSocket.accept());
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                System.err.println("debugger connected");
                connected.countDown();
            });
            WAIT.post(() -> {
                try {
                    connected.await();
                    Socket socket1 = debuggee.get();
                    Socket socket2 = debugger.get();
                    WAIT.post(() -> copySocketStreams(socket1, socket2));
                    WAIT.post(() -> copySocketStreams(socket2, socket1));
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            });

            System.err.println("prepareDebugger:");
            PendingDebugger pendingDebugger = agent.prepareDebugger();
            WAIT.post(() -> {
                System.err.println("connect:");
                LspSession xxxSession = new LspSession();

                try {
                    ConnectionSpec connectTo = ConnectionSpec.parse("stdio");
                    connectTo.prepare(
                        "Java Debug Server Adapter",
                        pendingDebugger.in,
                        pendingDebugger.out,
                        xxxSession, //XXX
                        LspSession::setDapServer,
                        Debugger::startDebugger
                    );
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            });

            System.err.println("attachDebugger:");
            System.err.println("InetAddress.getLocalHost(): " + InetAddress.getLocalHost());
            System.err.println("InetAddress.getLocalHost().getHostName(): " + InetAddress.getLocalHost().getHostName());
            System.err.println("debuggerSocket.getLocalPort(): " + debuggerSocket.getLocalPort());
            System.err.println("debuggerSocket.getInetAddress(): " + debuggerSocket.getInetAddress());
            System.err.println("debuggerSocket.getInetAddress(): " + debuggerSocket.getInetAddress().getHostName());
            System.err.println("debuggerSocket.getInetAddress(): " + debuggerSocket.getInetAddress().getHostAddress());
            WAIT.post(() -> {
                Map<String, Object> properties = new HashMap<>();
                properties.put("baseDir", FileUtil.toFile(context.getBaseDir()).getAbsolutePath());
                properties.put("sourcepath", context.getSourcePath().entries().stream().map(e -> e.getURL().toString()).toArray(String[]::new));
                pendingDebugger.attachDebugger(debuggerSocket.getInetAddress().getHostName(), debuggerSocket.getLocalPort(), properties);
            });

            System.err.println("invoked");
            return new ConnectionInfo("dt_socket", InetAddress.getLocalHost().getHostName(), debuggeeSocket.getLocalPort());
        }

        private static void copySocketStreams(Socket socket1, Socket socket2) {
            try {
                if (socket1 == null) {
                    if (socket2 != null) {
                        socket2.close();
                    }
                    return ;
                }
                InputStream in = socket1.getInputStream();
                OutputStream out = socket2.getOutputStream();
                int r;
                while ((r = in.read()) != (-1)) {
                    out.write(r);
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
