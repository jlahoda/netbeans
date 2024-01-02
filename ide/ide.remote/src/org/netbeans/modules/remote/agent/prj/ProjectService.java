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
package org.netbeans.modules.remote.agent.prj;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.ContextBasedActionRequest;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.LoadProjectResponse;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.PathProjectRequest;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.Task;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 */
@ServiceProvider(service=Service.class)
public class ProjectService implements Service {

    @Override
    public String name() {
        return "prj";
    }

    @Override
    public void run(InputStream in, OutputStream out) {
        new ReceiverBuilder<Task>(in, out, Task.class)
            .addHandler(Task.LOAD_PROJECT, PathProjectRequest.class, p -> {
            boolean isProject = false;
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.path);
                isProject = prjDir != null && ProjectManager.getDefault().findProject(prjDir) != null;
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete(new LoadProjectResponse(isProject));

            return result;
        }).addHandler(Task.GET_SUPPORTED_ACTIONS, PathProjectRequest.class, p -> {
            String[] actions = new String[0];
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.path);
                Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                if (prj != null) {
                   ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                   if (ap != null) {
                       actions = ap.getSupportedActions();
                   }
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete(actions);

            return result;
        }).addHandler(Task.IS_ENABLED_ACTIONS, ContextBasedActionRequest.class, p -> {
            Boolean enabled = false;
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.projectPath);
                Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                if (prj != null) {
                   ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                   if (ap != null) {
                        List<Object> context = new ArrayList<>();
                        if (p.selectedFileObjectPath != null) {
                            context.add(Utils.resolveLocalPath(p.selectedFileObjectPath));
                        }
                        enabled = ap.isActionEnabled(p.action, Lookups.fixed(context.toArray()));
                   }
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete(enabled);

            return result;
        }).addHandler(Task.INVOKE_ACTIONS, ContextBasedActionRequest.class, p -> {
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.projectPath);
                Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                if (prj != null) {
                   ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                   if (ap != null) {
                        List<Object> context = new ArrayList<>();
                        if (p.selectedFileObjectPath != null) {
                            context.add(Utils.resolveLocalPath(p.selectedFileObjectPath));
                        }
                        ap.invokeAction(p.action, Lookups.fixed(context.toArray()));
                   }
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete("done");

            return result;
        }).startReceiver();
    }

}
