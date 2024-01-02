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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 */
public final class ProjectHandler {

    private final Sender connection;

    public ProjectHandler(InputStream in, OutputStream out) {
        this.connection = new Sender(in, out);
    }

    public boolean loadProject(FileObject folder) {
        try {
            String relativePath = "/" + folder.getPath();
            LoadProjectResponse response = connection.sendAndReceive(Task.LOAD_PROJECT, new PathProjectRequest(relativePath), LoadProjectResponse.class).get();
            return response.isProject;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    public String[] getSupportedActions(FileObject projectDir) {
        try {
            String relativePath = "/" + projectDir.getPath();
            String[] actions = connection.sendAndReceive(Task.GET_SUPPORTED_ACTIONS, new PathProjectRequest(relativePath), String[].class).get();
            return actions;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return new String[0];
        }
    }

    public boolean isActionEnabled(FileObject projectDir, String command, FileObject selectedFile) {
        try {
            String relativeProjectPath = "/" + projectDir.getPath();
            String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;
            boolean enabled = connection.sendAndReceive(Task.IS_ENABLED_ACTIONS, new ContextBasedActionRequest(relativeProjectPath, command, relativeSelectedPath), Boolean.class).get();
            return enabled;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    public void invokeAction(FileObject projectDir, String command, FileObject selectedFile) {
        try {
            String relativeProjectPath = "/" + projectDir.getPath();
            String relativeSelectedPath = selectedFile != null ? ("/" + selectedFile.getPath()) : null;
            connection.sendAndReceive(Task.INVOKE_ACTIONS, new ContextBasedActionRequest(relativeProjectPath, command, relativeSelectedPath), Boolean.class).get();
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public static class PathProjectRequest {
        public String path;

        public PathProjectRequest() {
        }

        public PathProjectRequest(String path) {
            this.path = path;
        }
    }

    public static class ContextBasedActionRequest {
        public String projectPath;
        public String action;
        public String selectedFileObjectPath;

        public ContextBasedActionRequest() {
        }

        public ContextBasedActionRequest(String projectPath, String action, String selectedFileObjectPath) {
            this.projectPath = projectPath;
            this.action = action;
            this.selectedFileObjectPath = selectedFileObjectPath;
        }


    }

    public static class LoadProjectResponse {
        public boolean isProject;

        public LoadProjectResponse() {
        }

        public LoadProjectResponse(boolean isProject) {
            this.isProject = isProject;
        }
    }

    public enum Task {
        LOAD_PROJECT,
        GET_SUPPORTED_ACTIONS,
        IS_ENABLED_ACTIONS,
        INVOKE_ACTIONS,
    }
}
