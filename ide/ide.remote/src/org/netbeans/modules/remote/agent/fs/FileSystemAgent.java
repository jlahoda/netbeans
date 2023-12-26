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

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.fs.FSProtokol.Request;
import static org.netbeans.modules.remote.ide.fs.FSProtokol.RequestKind.CHILDREN;
import static org.netbeans.modules.remote.ide.fs.FSProtokol.RequestKind.FOLDER;
import static org.netbeans.modules.remote.ide.fs.FSProtokol.RequestKind.MIME_TYPE;
import org.netbeans.modules.remote.ide.fs.FSProtokol.Response;
import org.netbeans.modules.remote.ide.fs.FSProtokol.WriteData;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class FileSystemAgent {

    private static final Logger LOG = Logger.getLogger(FileSystemAgent.class.getName());
    //TODO: could use virtual threads?
    private final RequestProcessor WORKER = new RequestProcessor(FileSystemAgent.class.getName(), 1, false, false);

    private final FileSystem fs;
    private final InputStream in;
    private final OutputStream out;
    private final Gson gson = new Gson();
    private final Map<FileObject, FileLock> file2Lock = new HashMap<>(); //TODO: should auto-unlock the files is the client crashes

    public FileSystemAgent(FileSystem fs, InputStream in, OutputStream out) {
        this.fs = fs;
        this.in = in;
        this.out = out;
    }

    private void start() {
        WORKER.post(this::run);
    }

    public void run() {
        try {
            while (true) {
                Request request = gson.fromJson(Utils.read(in), Request.class);
                switch (request.kind) {
                    case CHILDREN:
                        handlePathRequest(request, path -> {
                            return Arrays.stream(path.getChildren())
                                         .map(f -> f.getNameExt())
                                         .toArray(s -> new String[s]);
                        });
                        break;
                    case LAST_MODIFIED:
                        handlePathRequest(request, path -> path.lastModified().getTime());
                        break;
                    case FOLDER:
                        handlePathRequest(request, path -> path.isFolder());
                        break;
                    case READ_ONLY:
                        handlePathRequest(request, path -> !path.canWrite());
                        break;
                    case MIME_TYPE:
                        handlePathRequest(request, path -> path.getMIMEType());
                        break;
                    case SIZE:
                        handlePathRequest(request, path -> path.getSize());
                        break;
                    case READ_INPUT:
                        handlePathRequest(request, path -> path.asBytes());
                        break;
                    case WRITE_OUTPUT:
                        handleRequest(request, WriteData.class, data -> {
                            FileObject fo = fs.findResource(data.path);
                            try (OutputStream out = fo.getOutputStream()) {
                                out.write(data.data);
                            }
                            return "done";
                        });
                        break;
                    case LOCK:
                        handlePathRequest(request, path -> {
                            FileLock lock = path.lock();

                            file2Lock.put(path, lock);
                            return "done";
                        });
                        break;
                    case UNLOCK:
                        handlePathRequest(request, path -> {file2Lock.remove(path).releaseLock(); return "done";});
                        break;
//                    case READ_ATTRIBUTE:
//                        handleAttributeRequest(request, (file, data) -> {
//                            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
//                                return file.getAttribute(data.dataName);
//    //                            oos.write(attribute);
//                                return null;
//                            }
//                        })
//                        break;
//                    case WRITE_ATTRIBUTE:
//                        break;
//                    case LIST_ATTRIBUTES:
//                        break;
//                    case RENAME_ATTRIBUTES:
//                        break;
//                    case DELETE_ATTRIBUTES:
//                        break;
                    default:
                        throw new AssertionError(request.kind.name());
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, null, ex);
        }
    }

    private <T> void handleRequest(Request request, Class<T> dataType, Process<T, Object> data2Response) throws IOException {
        Object responseData = data2Response.process(gson.fromJson(request.data, dataType));
        Response response = new Response(request.id, gson.toJson(responseData));
        Utils.write(out, gson.toJson(response));
    }

    private void handlePathRequest(Request request, Process<FileObject, Object> path2Response) throws IOException {
        handleRequest(request, String.class, path -> {
            FileObject fo = fs.findResource(path);
            if (fo == null) {
                System.err.println("no FileObject for: " + path);
            }
            return path2Response.process(fo);
        });
    }

//    private void handleAttributeRequest(Request request, AttributeProcess<Object> path2Response) throws IOException {
//        handleRequest(request, AttributeData.class, data -> {
//            FileObject fo = fs.findResource(data.path);
//            if (fo == null) {
//                System.err.println("no FileObject for: " + data.path);
//            }
//            return path2Response.process(fo, data);
//        });
//    }

    private interface Process<P, R> {
        public R process(P p) throws IOException;
    }

//    private interface AttributeProcess<R> {
//        public R process(FileObject file, AttributeData data) throws IOException;
//    }

    @ServiceProvider(service=Service.class)
    public static final class ServiceImpl implements Service {

        @Override
        public String name() {
            return "fs";
        }

        @Override
        public void run(InputStream in, OutputStream out) {
            try {
                FileSystem localFS = FileUtil.toFileObject(new File("/")).getFileSystem();
                new FileSystemAgent(localFS, in, out).start();
            } catch (FileStateInvalidException ex) {
                LOG.log(Level.SEVERE, null, ex);
                throw new UncheckedIOException(ex);
            }
        }

    }
}
