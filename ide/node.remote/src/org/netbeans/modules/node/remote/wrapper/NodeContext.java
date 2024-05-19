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
package org.netbeans.modules.node.remote.wrapper;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.netbeans.modules.node.remote.NodeCallback;
import org.netbeans.modules.node.remote.TreeItem;
import org.netbeans.modules.node.remote.TreeItem.IconDescriptor;
import org.netbeans.modules.node.remote.api.CreateExplorerParams;
import org.netbeans.modules.node.remote.api.GetResourceParams;
import org.netbeans.modules.node.remote.api.NodeChangeType;
import org.netbeans.modules.node.remote.api.NodeChangedParams;
import org.netbeans.modules.node.remote.api.TreeViewService;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;

public class NodeContext {

    private final AtomicReference<TreeViewService> service = new AtomicReference<>();
    private final Map<Integer, NodeImpl> id2Node = new HashMap<>();
    private final Map<URI, Image> uri2Image = new HashMap<>();

    synchronized Node getNode(int id) {
        return id2Node.computeIfAbsent(id, i -> new NodeImpl(this, i));
    }

    public NodeCallback createCallback() {
        return new NodeCallback() {
            @Override
            public void notifyNodeChange(NodeChangedParams params) {
                if (params.getNodeId() == null) {
                    Collection<NodeImpl> allNodes;

                    synchronized (NodeContext.this) {
                        allNodes = new HashSet<>(id2Node.values());
                    }

                    for (NodeImpl node : allNodes) {
                        refreshNode(node, params.getTypes());
                    }
                    return ;
                }

                NodeImpl node;
                synchronized (NodeContext.this) {
                    node = id2Node.get(params.getNodeId());
                }
                if (node == null) {
                    System.err.println("cannot find node id: " + params.getNodeId());
                    return ;
                }
                refreshNode(node, params.getTypes());
            }

            private void refreshNode(NodeImpl node, Set<NodeChangeType> types) {
                if (types == null || types.contains(NodeChangeType.CHILDREN)) {
                    node.refreshChildren();
                }
                if (types == null || types.contains(NodeChangeType.PROPERTY) ||
                    types.contains(NodeChangeType.SELF)) {
                    node.refreshProperties();
                }
            }
        };
    }

    public void setService(TreeViewService service) {
        this.service.set(service);
    }

    public Node create(String explorerKind, Object key) {
        try {
            TreeItem item = getService().explorerManager(new CreateExplorerParams(explorerKind, key)).get();

            synchronized (this) {
                return id2Node.computeIfAbsent(item.id, i -> new NodeImpl(this, item));
            }
        } catch (InterruptedException | ExecutionException ex) {
            throw new IllegalStateException(ex);
        }
    }

    TreeViewService getService() {
        TreeViewService s = service.get();

        if (s == null) {
            throw new IllegalStateException();
        }

        return s;
    }

    CompletableFuture<Image> iconFor(IconDescriptor descriptor) {
        synchronized (this) {
            Image res = uri2Image.get(descriptor.baseUri);
            if (res != null) {
                CompletableFuture<Image> result = new CompletableFuture<>();

                result.complete(res);

                return result;
            }
        }
        return getService().getResource(new GetResourceParams(descriptor.baseUri)).thenApply(data -> {
            //TODO: handle errors!
            try {
                //TODO: check encoding, etc...
                byte[] bytes = Base64.getDecoder().decode(data.getContent());
                Image result = ImageIO.read(new ByteArrayInputStream(bytes));
                synchronized (this) {
                    uri2Image.put(descriptor.baseUri, result);
                }
                return result;
            } catch (IOException ex) {
                //TODO: cache the result!
                Exceptions.printStackTrace(ex);
                return null;
            }
        });
    }
}
