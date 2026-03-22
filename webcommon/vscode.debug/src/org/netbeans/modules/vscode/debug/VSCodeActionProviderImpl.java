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
package org.netbeans.modules.vscode.debug;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionDescriptor.LineConvertorFactory;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.project.Project;
import org.netbeans.modules.launch.support.spi.LaunchProjectConfiguration;
import org.netbeans.modules.lsp.client.debugger.api.DAPConfiguration;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

@ServiceProviders({
    @ServiceProvider(path="launch.json/extensionHost", service=ActionProvider.class),
    @ServiceProvider(path="launch.json/pwa-extensionHost", service=ActionProvider.class)
})
public class VSCodeActionProviderImpl implements ActionProvider {

    private static final RequestProcessor WORKER = new RequestProcessor(VSCodeActionProviderImpl.class.getName(), 1, false, false);
    private static final Logger LOG = Logger.getLogger(VSCodeActionProviderImpl.class.getName());
    private static final Random RANDOM = new Random();

    private static final String[] ACTIONS = new String[] {
        COMMAND_DEBUG,
        COMMAND_RUN
    };

    @Override
    public String[] getSupportedActions() {
        return ACTIONS;
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        return true;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        LaunchProjectConfiguration conf = context.lookup(LaunchProjectConfiguration.class);
        List<String> args = new ArrayList<>();
        args.add((String) conf.getProperties().get("runtimeExecutable")); //TODO: default??
        args.addAll((List<String>) conf.getProperties().get("args"));

        int vsCodePort = 9229; //TODO: can we get a random port?
        int debugId = RANDOM.nextInt(Integer.MAX_VALUE);

        if (COMMAND_DEBUG.equals(command)) {
            args.add("--inspect-brk-extensions=" + vsCodePort);
            args.add("--debugId=" + debugId);
        }

        Future<Integer> vsCode = ExecutionService.newService(() -> new ProcessBuilder(args).start(),
                new ExecutionDescriptor().controllable(true),
                "VSCode").run();

        if (COMMAND_DEBUG.equals(command)) {
            CompletableFuture<Integer> port = new CompletableFuture<>();
            LineConvertorFactory findDAPPort = () -> line -> {
                final String marker = "Listening at 127.0.0.1:";
                if (line.startsWith(marker)) {
                    port.complete(Integer.parseInt(line.substring(marker.length())));
                }
                return null;
            };

            File vsDebugServer = InstalledFileLocator.getDefault().locate("lib/vsCodeDebug/src/vsDebugServer.js", "org.netbeans.modules.vscode.debug", false);
            ExecutionService.newService(() -> new ProcessBuilder(/*TODO:*/"node", vsDebugServer.getAbsolutePath(), "0", "127.0.0.1").start(),
                                        new ExecutionDescriptor().controllable(true).outConvertorFactory(findDAPPort),
                                        "VSCode JS Debug Adapter").run();
                Project prj = context.lookup(Project.class);
                FileObject workspace = prj.getProjectDirectory();
                File workspaceFile = FileUtil.toFile(workspace);
                String workspaceFolder = workspaceFile.getAbsolutePath();

                Runnable[] start = new Runnable[1];

                start[0] = () -> {
                    Socket s;

                    try {
                        s = new Socket("localhost", port.get(10, TimeUnit.SECONDS));
                    } catch (IOException | TimeoutException | ExecutionException | InterruptedException ex) {
                        LOG.log(Level.FINE, null, ex);
                        return ;
                    }

                    Map<String, Object> vsDebugArgs = new HashMap<>();
                    vsDebugArgs.put("type", "pwa-extensionHost");
                    vsDebugArgs.put("request", "attach");
                    vsDebugArgs.put("port", vsCodePort);
                    vsDebugArgs.put("__workspaceFolder", workspaceFolder);
                    vsDebugArgs.put("__sessionId", Integer.toString(debugId));
                    vsDebugArgs.put("cwd", workspaceFolder);
                    if (conf.getProperties().containsKey("outFiles")) {
                        vsDebugArgs.put("outFiles", conf.getProperties().get("outFiles"));
                    }

                    try {
                        DAPConfiguration.create(s.getInputStream(), s.getOutputStream()).setSessionName("VS Code").addConfiguration(vsDebugArgs).attachWaitable().thenRun(() -> {
                            if (!vsCode.isDone()) {
                                //start the debugger again:
                                start[0].run();
                            }
                        });
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                        return ;
                    }
                };

                start[0].run();
            }
    }

}
