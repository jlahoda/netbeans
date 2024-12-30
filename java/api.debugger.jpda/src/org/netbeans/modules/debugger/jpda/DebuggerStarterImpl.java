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
package org.netbeans.modules.debugger.jpda;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.Transport;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.netbeans.api.debugger.jpda.DebuggerStartException;
import org.netbeans.api.debugger.jpda.JPDADebuggerStarter.ConnectionInfo;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.netbeans.spi.debugger.jpda.JPDADebuggerStarterImplementation;


/**Based on maven/src/org/netbeans/modules/maven/debug/JPDAStart.java
 * Start the JPDA debugger
 * @author Milos Kleint
 */
@ServiceProvider(service=JPDADebuggerStarterImplementation.class)
public class DebuggerStarterImpl implements JPDADebuggerStarterImplementation {

    private static final RequestProcessor RP = new RequestProcessor(DebuggerStarterImpl.class);

    public ConnectionInfo startDebugger(Context context) throws IOException, IllegalStateException {
        ListeningConnector lc = null;
        Iterator<ListeningConnector> i = Bootstrap.virtualMachineManager().
                listeningConnectors().iterator();
        for (; i.hasNext();) {
            lc = i.next();
            Transport t = lc.transport();
            if (t != null && t.name().equals(context.getTransport())) {
                break;
            }
        }
        if (lc == null) {
            throw new IllegalStateException
                    ("No trasports named " + context.getTransport() + " found!"); //NOI18N
        }

        // TODO: revisit later when http://developer.java.sun.com/developer/bugParade/bugs/4932074.html gets integrated into JDK
        // This code parses the address string "HOST:PORT" to extract PORT and then point debugee to localhost:PORT
        // This is NOT a clean solution to the problem but it SHOULD work in 99% cases
        final Map<String, Connector.Argument> args = lc.defaultArguments();
        String address;
        try {
            address = lc.startListening(args);
        } catch (IllegalConnectorArgumentsException ex) {
            throw new IllegalStateException(ex);
        }
        int colon = address.indexOf(':');
        int port = Integer.parseInt(address.substring(colon + 1));
        Connector.IntegerArgument portArg = (Connector.IntegerArgument) args.get("port"); //NOI18N
        portArg.setValue(port);
        ConnectionInfo result = new ConnectionInfo(context.getTransport(),
                                                   colon != (-1) ? address.substring(0, colon) : null,
                                                   port);

        final Map<String, Object> properties = new HashMap<>();
        properties.put("jdksources", context.getJdkSourcePath()); //NOI18N
        properties.put("sourcepath", context.getSourcePath()); //NOI18N
        properties.put("name", context.getName()); //NOI18N
        properties.put("baseDir", FileUtil.toFile(context.getBaseDir())); // NOI18N
                
        final ListeningConnector flc = lc;

        RP.post(() -> {
            try {
                JPDADebugger.startListening(flc, args,
                                            new Object[]{properties});
            } catch (DebuggerStartException ex) {
                Logger.getLogger(DebuggerStarterImpl.class.getName()).log(Level.INFO, "Debugger Start Error.", ex);
            }
        });

        return result;
    }

}
