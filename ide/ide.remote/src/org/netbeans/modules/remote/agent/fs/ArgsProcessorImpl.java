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
package org.netbeans.modules.remote.agent.fs;

import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.remote.Remote;

import org.netbeans.spi.sendopts.Arg;
import org.netbeans.spi.sendopts.ArgsProcessor;
import org.netbeans.spi.sendopts.Description;
import org.netbeans.spi.sendopts.Env;
import org.openide.util.NbBundle.Messages;

public final class ArgsProcessorImpl implements ArgsProcessor {

    @Arg(longName="start-remote-agent", defaultValue = "")
    @Description(shortDescription="#DESC_StartRemoteAgent")
    @Messages("DESC_StartRemoteAgent=Starts remote agent")
    public String remoteAgent;

    @Override
    public void process(Env env) throws CommandException {
        if (remoteAgent != null) {
            Remote.runAgent(env.getInputStream(), env.getOutputStream()).waitFinished();
        }
    }
}

