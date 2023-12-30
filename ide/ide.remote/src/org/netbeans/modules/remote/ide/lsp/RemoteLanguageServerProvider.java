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
package org.netbeans.modules.remote.ide.lsp;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.project.Project;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider;
import org.netbeans.modules.remote.ide.RemoteManager;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 */
@MimeRegistration(service=LanguageServerProvider.class, mimeType="", position=0)
public class RemoteLanguageServerProvider implements LanguageServerProvider {

    @Override
    public LanguageServerDescription startServer(Lookup lookup) {
        try {
            Project prj = lookup.lookup(Project.class);

            if (prj == null) {
                //cannot currently create language servers with a project:
                return null;
            }

            FileSystem fs = prj.getProjectDirectory().getFileSystem();

            if (fs instanceof RemoteFileSystem) {
                RemoteFileSystem rfs = (RemoteFileSystem) fs;
                return RemoteManager.getDefault().getService(rfs.getRemoteDescription(), "lsp", streams -> new LSPHandler(streams.in(), streams.out())).getServer(lookup);
            }
        } catch (FileStateInvalidException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

}
