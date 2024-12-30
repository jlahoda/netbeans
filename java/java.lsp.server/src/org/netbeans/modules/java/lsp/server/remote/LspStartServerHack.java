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
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.java.lsp.server.ConnectionSpec;
import org.netbeans.modules.java.lsp.server.LspSession;
import org.netbeans.modules.java.lsp.server.debugging.Debugger;
import org.netbeans.modules.java.lsp.server.protocol.Server;
import org.netbeans.modules.remote.agent.lsp.LSPService.StartJavaServerHack;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 */
@ServiceProvider(service=StartJavaServerHack.class)
public class LspStartServerHack implements StartJavaServerHack {

    public void start(InputStream lspIn, OutputStream lspOut,
                      InputStream dapIn, OutputStream dapOut) throws IOException {
        RequestProcessor worker = new RequestProcessor(LspStartServerHack.class.getName(), 1, false, false);
        worker.post(() -> {
            try {
                LspSession session = new LspSession();
                if (lspIn != null) {
                    ConnectionSpec connectTo = ConnectionSpec.parse("stdio");
                    connectTo.prepare(
                        "Java Language Server",
                        lspIn,
                        lspOut,
                        session,
                        LspSession::setLspServer,
                        Server::launchServer
                    );
                }
                if (dapIn != null) {
                    ConnectionSpec connectTo = ConnectionSpec.parse("stdio");
                    connectTo.prepare(
                        "Java Debug Server Adapter",
                        dapIn,
                        dapOut,
                        session,
                        LspSession::setDapServer,
                        Debugger::startDebugger
                    );
                }
            } catch (IOException | CommandException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }
        });
    }
}
