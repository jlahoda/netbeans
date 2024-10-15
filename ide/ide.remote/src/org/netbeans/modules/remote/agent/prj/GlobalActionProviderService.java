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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.prj.GlobalActionProviderHandler.ActionProviderInterface;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.support.LookupProviderSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Service.class)
public class GlobalActionProviderService implements Service {

    public static final String NAME = "global-action-provider";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void run(InputStream in, OutputStream out) {
        Lookup baseLookup = Lookup.getDefault();
        ActionProvider delegate = LookupProviderSupport.createActionProviderMerger().merge(baseLookup);

        RemoteInvocation.receiver(in, out, new ActionProviderInterface() {
            @Override
            public String[] getSupportedActions() {
                return delegate.getSupportedActions();
            }

            @Override
            public boolean isActionEnabled(String command, String lookupSelectedFileObjectPath) {
                return delegate.isActionEnabled(command, createContextLookup(lookupSelectedFileObjectPath));
            }

            @Override
            public void invokeAction(String command, String lookupSelectedFileObjectPath) {
                delegate.invokeAction(command, createContextLookup(lookupSelectedFileObjectPath));
            }

            private Lookup createContextLookup(String lookupSelectedFileObjectPath) {
                List<Object> context = new ArrayList<>();
                if (lookupSelectedFileObjectPath != null) {
                    context.add(Utils.resolveLocalPath(lookupSelectedFileObjectPath));
                }
                return Lookups.fixed(context.toArray());
            }
        });
    }

}
