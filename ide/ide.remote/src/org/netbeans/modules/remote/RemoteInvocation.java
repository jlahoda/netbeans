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
package org.netbeans.modules.remote;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;

/**
 *
 */
public class RemoteInvocation {

    public static <T> T caller(InputStream in, OutputStream out, Class<T> intf) {
        Sender sender = new Sender(in, out);
        return (T) Proxy.newProxyInstance(RemoteInvocation.class.getClassLoader(), new Class<?>[] {intf}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String[] encodedParams;

                if (args == null) {
                    encodedParams = new String[0];
                } else {
                    encodedParams = Arrays.stream(args)
                                          .map(Utils.gson::toJson)
                                          .toArray(s -> new String[s]);
                }

                InvocationData data = new InvocationData(encodeMethod(method), encodedParams);
                Type returnType = method.getGenericReturnType();
                if (returnType == void.class) {
                    returnType = Void.class;
                }
                return sender.sendAndReceive(Task.INVOKE, data, returnType).get();
            }
        });
    }

    public static void receiver(InputStream in, OutputStream out, Object delegate) {
        Map<String, Method> encodedMethod2Method = Arrays.stream(delegate.getClass().getMethods()).collect(Collectors.toMap(m -> encodeMethod(m), m -> m));

        new ReceiverBuilder<>(in, out, Task.class)
                .addHandler(Task.INVOKE, InvocationData.class, data -> {
                    CompletableFuture<Object> result = new CompletableFuture<>();
                    try {
                        Object[] args = new Object[data.parameters.length];
                        Method toInvoke = encodedMethod2Method.get(data.methodNameAndSignature);
                        Type[] parameterTypes = toInvoke.getGenericParameterTypes();
                        for (int i = 0; i < parameterTypes.length; i++) {
                            args[i] = Utils.gson.fromJson(data.parameters[i], parameterTypes[i]);
                        }
                        toInvoke.setAccessible(true);
                        result.complete(toInvoke.invoke(delegate, args));
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                    return result;
                })
                .startReceiver();
    }

    public static RuntimeException sneakyThrows(Throwable t) {
        return doSneakyThrows(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException doSneakyThrows(Throwable t) throws T {
        throw (T) t;
    }

    public enum Task {
        INVOKE;
    }

    public static final class InvocationData {
        public String methodNameAndSignature;
        public String[] parameters;

        public InvocationData() {
        }

        public InvocationData(String methodNameAndSignature, String[] parameters) {
            this.methodNameAndSignature = methodNameAndSignature;
            this.parameters = parameters;
        }

    }

    private static String encodeMethod(Method m) {
        StringBuilder result = new StringBuilder();
        result.append(m.getName());
        result.append("(");
        for (Class<?> p : m.getParameterTypes()) {
            result.append(p.getCanonicalName()).append(";");
        }
        result.append(")");
        return result.toString();
    }
}
