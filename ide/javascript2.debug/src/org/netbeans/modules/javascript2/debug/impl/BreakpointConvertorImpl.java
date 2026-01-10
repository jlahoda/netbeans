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
package org.netbeans.modules.javascript2.debug.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.modules.javascript2.debug.breakpoints.JSLineBreakpoint;
import org.openide.util.Lookup;

public class BreakpointConvertorImpl {

    public static Object create() throws ClassNotFoundException {
        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
        Class<?> convertorSPI = cl.loadClass("org.netbeans.modules.lsp.client.debugger.spi.BreakpointConvertor");
        return Proxy.newProxyInstance(cl, new Class<?>[] {convertorSPI}, new InvocationHandler() {
            @Override
            public Object invoke(Object o, Method method, Object[] os) throws Throwable {
                return switch (method.getName()) {
                    case "convert" -> {
                        Breakpoint b = (Breakpoint) os[0];
                        if (b instanceof JSLineBreakpoint jsBreak) {
                            Class<?> consumerClass = method.getParameterTypes()[1];
                            Method lineBreakpointMethod = consumerClass.getDeclaredMethod("lineBreakpoint", URI.class, int.class, String.class);
                            lineBreakpointMethod.invoke(os[1], jsBreak.getFileObject().toURI(), jsBreak.getLineNumber(), jsBreak.getCondition());
                        }
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException();
                };
            }
        });
    }

}
