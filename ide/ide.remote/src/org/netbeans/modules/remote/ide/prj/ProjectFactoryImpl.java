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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.project.Project;
import org.netbeans.modules.remote.ide.RemoteManager;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 */
@ServiceProvider(service=ProjectFactory.class, position=0)
public class ProjectFactoryImpl implements ProjectFactory {

    @Override
    public boolean isProject(FileObject projectDirectory) {
        try {
            return projectDirectory.getFileSystem() instanceof RemoteFileSystem;
        } catch (FileStateInvalidException ex) {
            return false;
        }
    }

    @Override
    public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
        if (projectDirectory.getFileSystem() instanceof RemoteFileSystem) {
            RemoteFileSystem rfs = (RemoteFileSystem) projectDirectory.getFileSystem();
            ProjectHandler handler = RemoteManager.getDefault().getService(rfs.getRemoteDescription(), "prj", streams -> new ProjectHandler(streams.in(), streams.out()));

            boolean isProject = handler.loadProject(projectDirectory);

            if (isProject) {
                return new ProjectImpl(projectDirectory, handler);
            }
        }

        return null;
    }

    @Override
    public void saveProject(Project project) throws IOException, ClassCastException {
        //TODO
    }

    private static final class ProjectImpl implements Project {

        private final FileObject projectDirectory;
        private final Lookup lookup;
        private final ProjectHandler handler;

        public ProjectImpl(FileObject projectDirectory, ProjectHandler handler) {
            this.projectDirectory = projectDirectory;
            this.lookup = Lookups.fixed(new ActionProviderImpl(projectDirectory, handler),
                                        new LogicalViewProviderImpl(projectDirectory, handler));
            this.handler = handler;
        }

        @Override
        public FileObject getProjectDirectory() {
            return projectDirectory;
        }

        @Override
        public Lookup getLookup() {
            return lookup;
        }

    }

    private static final class ActionProviderImpl implements ActionProvider {

        private final FileObject projectDir;
        private final ProjectHandler handler;
        private final AtomicReference<String[]> supportedActions = new AtomicReference<>();

        public ActionProviderImpl(FileObject projectDir, ProjectHandler handler) {
            this.projectDir = projectDir;
            this.handler = handler;
        }

        @Override
        public String[] getSupportedActions() {
            String[] actions = supportedActions.get();

            if (actions == null) {
                actions = handler.getSupportedActions(projectDir);
                supportedActions.compareAndSet(null, actions);
            }

            return actions;
        }

        @Override
        public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
            FileObject selectedFile = context.lookup(FileObject.class);
            handler.invokeAction(projectDir, command, selectedFile);
        }

        @Override
        public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
            FileObject selectedFile = context.lookup(FileObject.class);
            return handler.isActionEnabled(projectDir, command, selectedFile);
        }
        
    }

    private static final class LogicalViewProviderImpl implements LogicalViewProvider {

        private final FileObject projectDir;
        private final ProjectHandler handler;

        public LogicalViewProviderImpl(FileObject projectDir, ProjectHandler handler) {
            this.projectDir = projectDir;
            this.handler = handler;
        }

        @Override
        public Node createLogicalView() {
            String relativePath = "/" + projectDir.getPath();
            return handler.getNodeContext().create("projectNodes", relativePath);
        }

        @Override
        public Node findPath(Node root, Object target) {
            return null;
        }

    }
}
