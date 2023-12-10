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
package org.netbeans.modules.remote.ide;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.netbeans.modules.favorites.api.Favorites;
import org.netbeans.modules.remote.agent.fs.FileSystemAgent;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.remote.ide.ConnectToSsh"
)
@ActionRegistration(
        displayName = "#CTL_ConnectToSsh"
)
@ActionReference(path = "Menu/Tools", position = 1005)
@Messages("CTL_ConnectToSsh=Connect to using SSH...")
public final class ConnectToSsh implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            ServerSocket sock = new ServerSocket(0);

            new Thread(() -> {
                try {
                    while (true) {
                        Socket socket = sock.accept();
                        new Thread(() -> {
                            try {
                                new FileSystemAgent(FileUtil.toFileObject(new File("/")).getFileSystem(), socket.getInputStream(), socket.getOutputStream()).run();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }).start();
                    }
                } catch (IOException ex) {
                    //ignore...
                }
            }).start();
            Socket client = new Socket(sock.getInetAddress(), sock.getLocalPort());
            RemoteFileSystem fs = new RemoteFileSystem(client.getOutputStream(), client.getInputStream());
            Favorites.getDefault().add(fs.getRoot());
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
