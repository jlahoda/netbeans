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

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.modules.favorites.api.Favorites;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.RemoteManager.Running;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;
import org.netbeans.modules.remote.ide.sync.SyncHandler;
import org.openide.*;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.remote.ide.ConnectToRunning"
)
@ActionRegistration(
        displayName = "#CTL_ConnectToRunning"
)
@ActionReference(path = "Menu/Tools", position = 1010)
@Messages("CTL_ConnectToRunning=Connect to remote running IDE...")
public final class ConnectToRunning implements ActionListener {

    private static final String KEY_HOSTNAME = "hostname";
    private static final String KEY_PORT = "port";
    private static final String KEY_SYNCHRONIZE = "synchronize";

    private static final RequestProcessor WORKER = new RequestProcessor(ConnectToRunning.class.getName(), 1, false, false); //TODO: throughput?

    @Override
    public void actionPerformed(ActionEvent e) {
        RunningConnectionPanel settings = new RunningConnectionPanel();
        Preferences connectPrefs = Utils.getPreferences().node("connect");

        settings.setHostname(connectPrefs.get(KEY_HOSTNAME, "localhost"));
        settings.setPort(connectPrefs.get(KEY_PORT, ""));
        settings.setSynchronize(connectPrefs.getBoolean(KEY_SYNCHRONIZE, true));

        Dialog[] d = new Dialog[1];
        DialogDescriptor dd = new DialogDescriptor(settings, "Remote Connection Settings", true, DialogDescriptor.OK_CANCEL_OPTION, DialogDescriptor.OK_OPTION, evt -> {
            if (evt.getSource() == DialogDescriptor.OK_OPTION) {
                connectPrefs.put(KEY_HOSTNAME, settings.getHostname());
                connectPrefs.put(KEY_PORT, settings.getPort());
                connectPrefs.putBoolean(KEY_SYNCHRONIZE, settings.isSynchronize());

                String displayName = settings.getHostname() + ":" + settings.getPort();
                ProgressHandle progress = ProgressHandle.createHandle("Synchronizing with: " + displayName);
                progress.start();
                WORKER.post(() -> {
                    try {
                        progress.progress("Connecting to the remote server");
                        Running remote = new Running(settings.getHostname(), Integer.parseInt(settings.getPort()), (int) System.currentTimeMillis()); //XXX: better transientId!
                        RemoteManager.getDefault().registerTransientRemote(remote);
                        RemoteFileSystem rfs = RemoteFileSystem.getRemoteFileSystem(remote);
                        Favorites.getDefault().add(rfs.getRoot()); //TODO: remove the broken links from the previous runs on start
                        SyncHandler.startSync(rfs, progress);
                    } catch (DataObjectNotFoundException ex) {
                        Exceptions.printStackTrace(ex);
                    } finally {
                        progress.finish();
                    }
                });
            }
            d[0].setVisible(false);
        });
        d[0] = DialogDisplayer.getDefault().createDialog(dd);
        d[0].setVisible(true);
    }
}
