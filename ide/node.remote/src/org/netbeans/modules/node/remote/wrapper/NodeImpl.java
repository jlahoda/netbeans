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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.netbeans.api.actions.Openable;
import org.netbeans.modules.node.remote.TreeItem;
import org.netbeans.modules.node.remote.TreeItem.CollapsibleState;
import org.netbeans.modules.node.remote.api.NodeChangeType;
import org.netbeans.modules.node.remote.api.NodeChangesParams;
import org.netbeans.modules.node.remote.api.NodeOperationParams;
import org.netbeans.modules.node.remote.wrapper.NodeContext.NodeId;
import org.openide.actions.OpenAction;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ProxyLookup.Controller;

/**
 *
 * @author lahvac
 */
class NodeImpl extends AbstractNode {

    private static final Pattern SPACE = Pattern.compile(" +");
    private static final Action[] NO_ACTIONS = new Action[0];
    private final NodeContext context;
    private final int id;
    private final Controller proxyLookupController;
    private Image icon;
    private Action[] actions = NO_ACTIONS;
    private Action preferredAction = null;

    private NodeImpl(NodeContext context, int id, Controller proxyLookupController) {
        super(new ChildrenImpl(context, id), new ProxyLookup(Lookups.fixed(new NodeId(id)), new ProxyLookup(proxyLookupController)));
        this.context = context;
        this.id = id;
        this.proxyLookupController = proxyLookupController;
        NodeChangesParams params = new NodeChangesParams();
        params.setNodeId(id);
        params.setTypes(EnumSet.allOf(NodeChangeType.class));
        context.getService().changes(params);
    }

    NodeImpl(NodeContext context, int id) {
        this(context, id, new Controller());
        refreshProperties();
    }

    //XXX: ideally, this should be lazy, especially for the root node
    NodeImpl(NodeContext context, TreeItem item) {
        this(context, item.id, new Controller());
        setTreeItem(item);
    }

    private void setTreeItem(TreeItem item) {
        //synchronization???
        setDisplayName(item.label);
        setShortDescription(item.description);
        context.iconFor(item.iconDescriptor).thenAccept(img -> {
            synchronized (this) {
                icon = img;
            }
            fireIconChange();
            fireOpenedIconChange();
        });
        if (item.collapsibleState == CollapsibleState.None && !this.isLeaf()) {
            //XXX: the remote node's leafness can change?
            Children.MUTEX.writeAccess(() -> setChildren(Children.LEAF));
        }

        Set<String> contextData = new HashSet<>(Arrays.asList(SPACE.split(item.contextValue != null ? item.contextValue : "")));

        if (contextData.contains("is:file")) {
            try {
                String path;
                String resourceUri = item.resourceUri;

                if (resourceUri.startsWith("file:///")) {
                    path = resourceUri.substring("file:///".length());
                } else if (resourceUri.startsWith("file:/")) {
                    path = resourceUri.substring("file:/".length());
                } else {
                    path = null;
                }

                FileObject file = path != null ? context.resolvePath(path) : null;
                DataObject od = file != null ? DataObject.find(file) : null;
                Node n = od != null ? od.getNodeDelegate() : null;

                if (n != null) {
                    Action[] actions = n.getActions(true);
                    Action preferredAction = n.getPreferredAction();
                    Lookup nodeLookup = n.getLookup();

                    synchronized (this) {
                        this.actions = actions;
                        this.preferredAction = preferredAction;
                        proxyLookupController.setLookups(nodeLookup);
                    }
                }
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    @Override
    public Image getIcon(int type) {
        Image icon;

        synchronized (this) {
            icon = this.icon;
        }

        return icon != null ? icon : super.getIcon(type);
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public synchronized Action[] getActions(boolean context) {
        return actions;
    }

    @Override
    public synchronized Action getPreferredAction() {
        return preferredAction;
    }

    void refreshProperties() {
        context.getService().info(new NodeOperationParams(id)).thenAccept(this::setTreeItem);
    }

    void refreshChildren() {
        ((ChildrenImpl) getChildren()).refreshKeys();
    }

    public int getId() {
        return id;
    }

    private static final class ChildrenImpl extends Children.Keys<Integer> {

        private final NodeContext context;
        private final int id;
        private final AtomicBoolean childrenVisible = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<?>> pendingChildren = new AtomicReference<CompletableFuture<?>>();

        public ChildrenImpl(NodeContext context, int id) {
            super(true);
            this.context = context;
            this.id = id;
        }

        @Override
        protected void addNotify() {
            if (!childrenVisible.getAndSet(true)) {
                refreshKeys();
            }
        }

        @Override
        protected Node[] createNodes(Integer key) {
            return new Node[] {
                context.getNode(key)
            };
        }

        private void refreshKeys() {
            if (childrenVisible.get()) {
               final CompletableFuture<?> childrenFuture = context.getService().getChildren(new NodeOperationParams(id)).thenAccept(children -> {
                    setKeys(Arrays.stream(children).mapToObj(id -> id).toList());
                });
                pendingChildren.set(childrenFuture);
                childrenFuture.thenAccept(__ -> {
                    pendingChildren.compareAndSet(childrenFuture, null);
                });
            }
        }

        public Node[] getNodes(boolean optimalResult) {
            if (optimalResult) {
                addNotify();
                CompletableFuture<?> pending = pendingChildren.get();
                if (pending != null) {
                    try {
                        pending.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            return super.getNodes(optimalResult);
        }
    }


}
