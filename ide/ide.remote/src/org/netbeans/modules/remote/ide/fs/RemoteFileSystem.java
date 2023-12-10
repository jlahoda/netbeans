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
package org.netbeans.modules.remote.ide.fs;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.spi.URLStreamHandlerProvider;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.ConnectToSsh;
import org.netbeans.modules.remote.ide.fs.FSProtokol.Request;
import org.netbeans.modules.remote.ide.fs.FSProtokol.RequestKind;
import org.netbeans.modules.remote.ide.fs.FSProtokol.Response;
import org.netbeans.modules.remote.ide.fs.FSProtokol.WriteData;
import org.openide.filesystems.AbstractFileSystem;
import org.openide.filesystems.DefaultAttributes;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.URLMapper;
import org.openide.util.Enumerations;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class RemoteFileSystem extends AbstractFileSystem implements AbstractFileSystem.List, AbstractFileSystem.Info, AbstractFileSystem.Attr {

    private final OutputStream requests;
    private final InputStream responses;
    private final Gson gson = new Gson();

    public RemoteFileSystem(OutputStream requests, InputStream responses) {
        this.requests = requests;
        this.responses = responses;
        list = this;
        info = this;
        attr = this;

        fs = this;
    }

    @Override
    public String getDisplayName() {
        return "XXX";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    private int idx;

    private synchronized <T> T sendAndReceive(RequestKind kind, Object request, Class<T> responseDataType) {
        try {
            Utils.write(requests, gson.toJson(new Request(idx++, kind, gson.toJson(request))));
            String responseString = Utils.read(responses);
            return gson.fromJson(gson.fromJson(responseString, Response.class).data, responseDataType);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public String[] children(String f) {
        return sendAndReceive(RequestKind.CHILDREN, f, String[].class);
    }

    @Override
    public Date lastModified(String name) {
        return new Date(sendAndReceive(RequestKind.LAST_MODIFIED, name, long.class));
    }

    @Override
    public boolean folder(String name) {
        return sendAndReceive(RequestKind.FOLDER, name, Boolean.class);
    }

    @Override
    public boolean readOnly(String name) {
        return sendAndReceive(RequestKind.READ_ONLY, name, Boolean.class);
    }

    @Override
    public String mimeType(String name) {
        return sendAndReceive(RequestKind.MIME_TYPE, name, String.class);
    }

    @Override
    public long size(String name) {
        return sendAndReceive(RequestKind.SIZE, name, Long.class);
    }

    @Override
    public InputStream inputStream(String name) throws FileNotFoundException {
        return new ByteArrayInputStream(sendAndReceive(RequestKind.READ_INPUT, name, byte[].class));
    }

    @Override
    public OutputStream outputStream(String name) throws IOException {
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                sendAndReceive(RequestKind.WRITE_OUTPUT, new WriteData(name, toByteArray()), String.class);
            }

        };
    }

    @Override
    public void lock(String name) throws IOException {
        sendAndReceive(RequestKind.LOCK, name, String.class);
    }

    @Override
    public void unlock(String name) {
        sendAndReceive(RequestKind.UNLOCK, name, String.class);
    }

    @Override
    public void markUnimportant(String name) {
        throw new UnsupportedOperationException("Deprecated.");
    }

    //TODO: clear on delete:
    private final Map<String, Map<String, Object>> attributes = new HashMap<>(); //TODO: how should be attributes be handled??

    @Override
    public Object readAttribute(String name, String attrName) {
        return attributes.getOrDefault(name, Map.of()).get(attrName);
    }

    @Override
    public void writeAttribute(String name, String attrName, Object value) throws IOException {
        attributes.computeIfAbsent(name, x -> new HashMap<>()).put(attrName, value);
    }

    @Override
    public Enumeration<String> attributes(String name) {
        return Enumerations.array(attributes.getOrDefault(name, Map.of()).keySet().toArray(new String[0]));
    }

    @Override
    public void renameAttributes(String oldName, String newName) {
        attributes.put(newName, attributes.remove(oldName));
    }

    @Override
    public void deleteAttributes(String name) {
        attributes.remove(name);
    }

    private static RemoteFileSystem fs;

    @ServiceProvider(service=URLMapper.class)
    public static final class URLMapperImpl extends URLMapper {

        @Override
        public URL getURL(FileObject fo, int type) {
            try {
                if (fo.getFileSystem() instanceof RemoteFileSystem) {
                    return new URL("ssh://RemoteFS/" + fo.getPath());
                }
            } catch (FileStateInvalidException | MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
            return null;
        }

        @Override
        public FileObject[] getFileObjects(URL url) {
            if (url.toString().contentEquals("ssh://RemoteFS/")) {
                if (fs == null) {
                    new ConnectToSsh().actionPerformed(null);
                }
                return new FileObject[] {
                    fs.findResource(url.toString().substring("ssh://RemoteFS/".length()))
                };
            }
            return null;
        }

    }

    @ServiceProvider(service=URLStreamHandlerFactory.class)
    public static final class URLHandlerProviderImpl implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("ssh".equals(protocol)) {
                return new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
                    }
                };
            }

            return null;
        }
    }
}
