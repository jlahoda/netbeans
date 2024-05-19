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
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.modules.node.remote.TreeItem;
import org.netbeans.modules.node.remote.TreeItem.CollapsibleState;
import org.netbeans.modules.node.remote.api.NodeChangeType;
import org.netbeans.modules.node.remote.api.NodeChangesParams;
import org.netbeans.modules.node.remote.api.NodeOperationParams;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

/**
 *
 * @author lahvac
 */
class NodeImpl extends AbstractNode {

    private final NodeContext context;
    private final int id;
    private Image icon;

    private NodeImpl(NodeContext context, int id, boolean internal) {
        super(new ChildrenImpl(context, id));
        this.context = context;
        this.id = id;
        NodeChangesParams params = new NodeChangesParams();
        params.setNodeId(id);
        params.setTypes(EnumSet.allOf(NodeChangeType.class));
        context.getService().changes(params);
    }

    NodeImpl(NodeContext context, int id) {
        this(context, id, true);
        refreshProperties();
    }

    //XXX: ideally, this should be lazy, especially for the root node
    NodeImpl(NodeContext context, TreeItem item) {
        this(context, item.id, true);
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

    void refreshProperties() {
        context.getService().info(new NodeOperationParams(id)).thenAccept(this::setTreeItem);
    }

    void refreshChildren() {
        ((ChildrenImpl) getChildren()).refreshKeys();
    }

    private static final class ChildrenImpl extends Children.Keys<Integer> {

        private final NodeContext context;
        private final int id;
        private final AtomicBoolean childrenVisible = new AtomicBoolean();

        public ChildrenImpl(NodeContext context, int id) {
            super(true);
            this.context = context;
            this.id = id;
        }

        @Override
        protected void addNotify() {
            childrenVisible.set(true);
            refreshKeys();
        }

        @Override
        protected Node[] createNodes(Integer key) {
            return new Node[] {
                context.getNode(key)
            };
        }

        private void refreshKeys() {
            if (childrenVisible.get()) {
                context.getService().getChildren(new NodeOperationParams(id)).thenAccept(children -> {
                    setKeys(Arrays.stream(children).mapToObj(id -> id).toList());
                });
            }
        }
    }


}
