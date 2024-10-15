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
package org.netbeans.modules.remote.ide.prj;

import java.util.logging.Logger;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.agent.prj.GlobalActionProviderService;
import org.netbeans.modules.remote.ide.RemoteManager;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

//TODO: merge project and global actions implementation
public class GlobalActionProviderHandler {
    private static final Logger LOG = Logger.getLogger(GlobalActionProviderImpl.class.getName());

    public interface ActionProviderInterface {
        public String[] getSupportedActions();
        public boolean isActionEnabled(String command, String lookupSelectedFileObjectPath);
        public void invokeAction(String command, String lookupSelectedFileObjectPath);
    }

    @ServiceProvider(service=ActionProvider.class, position=0)
    public static class GlobalActionProviderImpl implements ActionProvider {

        //XXX: get the supported actions from the remotes???
        private final String[] SUPPORTED_ACTIONS = new String[] {
            COMMAND_TEST_SINGLE,
            COMMAND_DEBUG_TEST_SINGLE
        };

        @Override
        public String[] getSupportedActions() {
            return SUPPORTED_ACTIONS;
        }

        @Override
        public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
            FileObject selectedFile = context.lookup(FileObject.class);
            String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;

            getDelegate(context).invokeAction(command, relativeSelectedPath);
        }

        @Override
        public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
            ActionProviderInterface delegate = getDelegate(context);

            if (delegate == null) {
                return false;
            }

            FileObject selectedFile = context.lookup(FileObject.class);
            String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;

            return delegate.isActionEnabled(command, relativeSelectedPath);
        }

        private ActionProviderInterface getDelegate(Lookup context) {
            try {
                FileObject file = context.lookup(FileObject.class);
                if (file != null && file.getFileSystem() instanceof RemoteFileSystem rfs) {
                    return RemoteManager.getDefault().getService(rfs.getRemoteDescription(), GlobalActionProviderService.NAME, streams -> {
                        return RemoteInvocation.caller(streams.in(), streams.out(), ActionProviderInterface.class);
                    });
                }
            } catch (FileStateInvalidException ex) {

            }
            return null;
        }
    }
}
