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
import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.node.remote.PathFinder;
import org.netbeans.modules.node.remote.api.ExplorerManagerFactory;
import org.netbeans.modules.node.remote.api.TreeDataListener;
import org.netbeans.modules.node.remote.api.TreeDataProvider;
import org.netbeans.modules.node.remote.api.TreeItemData;
import org.netbeans.modules.remote.Utils;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.openide.explorer.ExplorerManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 *
 * @author sdedic
 */
@ServiceProviders({
    @ServiceProvider(path = "Explorers/" + ProjectNodesExplorer.ID_PROJECT_LOGICAL_VIEW, service = ExplorerManagerFactory.class),
    @ServiceProvider(path = "Explorers/" + ProjectNodesExplorer.ID_PROJECT_LOGICAL_VIEW, service = PathFinder.class),
//    @ServiceProvider(service = TreeDataProvider.Factory.class, path = "Explorers/" + ProjectExplorer.ID_PROJECT_LOGICAL_VIEW)
})
public class ProjectNodesExplorer implements ExplorerManagerFactory, PathFinder/*, TreeDataProvider.Factory*/ {
    public static final String ID_PROJECT_LOGICAL_VIEW = "projectNodes"; // NOI18N

    private static final RequestProcessor PROJECT_INIT_RP = new RequestProcessor(ProjectNodesExplorer.class.getName());

    @Override
    public CompletionStage<ExplorerManager> createManager(String id, Lookup context) {
        try {
            if (!ID_PROJECT_LOGICAL_VIEW.equals(id)) {
                return null;
            }
            String path = context.lookup(String.class);
            FileObject pathFO = path != null ? Utils.resolveLocalPath(path) : null;
            Project p = pathFO != null ? ProjectManager.getDefault().findProject(pathFO) : null;
            LogicalViewProvider lvp = p != null ? p.getLookup().lookup(LogicalViewProvider.class) : null;
            if (lvp != null) {
                ExplorerManager m = new ExplorerManager();
                m.setRootContext(lvp.createLogicalView());
                CompletableFuture<ExplorerManager> result = new CompletableFuture<>();
                result.complete(m);
                return result;
            }
        } catch (IOException | IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    @Override
    public Node findPath(Node root, Object target) {
        Project p = root.getLookup().lookup(Project.class);
        LogicalViewProvider lvp = p != null ? p.getLookup().lookup(LogicalViewProvider.class) : null;
        FileObject targetFile;
        if (lvp != null && target instanceof String path && (targetFile = Utils.resolveLocalPath(path)) != null) {
            Node n = lvp.findPath(root, targetFile);
            return n;
        }
        return null;
    }

//    @Override
//    public TreeDataProvider createProvider(String treeId) {
//        return new ProjectDecorator();
//    }
//
//    static class ProjectDecorator implements TreeDataProvider {
//
//        @Override
//        public TreeItemData createDecorations(Node n, boolean expanded) {
//            TreeItemData tid = new TreeItemData();
//            FileObject f = n.getLookup().lookup(FileObject.class);
//            if (f != null && f.isData()) {
//                // set leaf status for all files in the projects view.
//                tid.makeLeaf();
//            }
//            return tid;
//        }
//
//        @Override
//        public void addTreeItemDataListener(TreeDataListener l) {
//        }
//
//        @Override
//        public void removeTreeItemDataListener(TreeDataListener l) {
//        }
//
//        @Override
//        public void nodeReleased(Node n) {
//
//        }
//    }
}
