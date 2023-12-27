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
import java.util.concurrent.CompletableFuture;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.remote.AsynchronousConnection;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.LoadProjectResponse;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.PathProjectRequest;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.Task;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
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
        AsynchronousConnection.startReceiver(in, out, Task.class, task -> PathProjectRequest.class, (task, p) -> {
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
        });
    }

}
