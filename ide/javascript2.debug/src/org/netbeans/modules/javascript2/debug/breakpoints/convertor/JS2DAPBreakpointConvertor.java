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
package org.netbeans.modules.javascript2.debug.breakpoints.convertor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.javascript2.debug.breakpoints.JSLineBreakpoint;
import org.openide.util.Lookup;

public class JS2DAPBreakpointConvertor {
    private static final Logger LOG = Logger.getLogger(JS2DAPBreakpointConvertor.class.getName());
    public static Object createConvertor() {
        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);

        if (cl == null) {
            return null;
        }

        try {
            Class<?> convertorClass = Class.forName("org.netbeans.modules.lsp.client.debugger.spi.BreakpointConvertor", false, cl);
            Class<?> consumerClass = Class.forName("org.netbeans.modules.lsp.client.debugger.spi.BreakpointConvertor$ConvertedBreakpointConsumer", false, cl);
            Method lineBreakpointMethod = consumerClass.getDeclaredMethod("lineBreakpoint", String.class, int.class, String.class);
            return Proxy.newProxyInstance(cl, new Class<?>[] {convertorClass}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    switch (method.getName()) {
                        case "convert":
                            if (args[0] instanceof JSLineBreakpoint) {
                                JSLineBreakpoint brk = (JSLineBreakpoint) args[0];
                                lineBreakpointMethod.invoke(args[1], brk.getURL().toString(), brk.getLineNumber(), brk.getCondition());
                            }
                            break;
                    }
                    return null;
                }
            });
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
            LOG.log(Level.FINE, null, ex);
            return null;
        }
    }
}
