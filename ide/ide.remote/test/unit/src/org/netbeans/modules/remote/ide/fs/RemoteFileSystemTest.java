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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.remote.agent.fs.FileSystemAgent;
import org.netbeans.modules.remote.ide.RemoteManager.SshRemoteDescription;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class RemoteFileSystemTest extends NbTestCase {

    private ServerSocket sock;

    public RemoteFileSystemTest(String name) {
        super(name);
    }

    public void testChildren() throws IOException {
        clearWorkDir();

        File wd = getWorkDir();
        File test1 = new File(wd, "test1"); test1.mkdir();
        File test2 = new File(wd, "test2"); test2.mkdir();
        File a = new File(test1, "a.txt");  write(a, "aa".getBytes(StandardCharsets.UTF_8));

        Socket s = new Socket("localhost", sock.getLocalPort());
        RemoteFileSystem rfs = new RemoteFileSystem(new SshRemoteDescription("", "", "", ""), s.getOutputStream(), s.getInputStream());

        FileObject rwd = rfs.findResource(wd.getAbsolutePath());
        FileObject[] wdChildren = rwd.getChildren(); //TODO: ordering

        assertEquals("test1", wdChildren[0].getNameExt());
        assertTrue(wdChildren[0].isFolder());

        assertEquals("test2", wdChildren[1].getNameExt());
        assertTrue(wdChildren[1].isFolder());

        FileObject aTxt = rwd.getFileObject("test1/a.txt");

        assertEquals("text/plain", aTxt.getMIMEType());
        assertEquals("aa", aTxt.asText("UTF-8"));
        assertEquals(a.lastModified(), aTxt.lastModified().getTime());

        try (OutputStream out = aTxt.getOutputStream()) {
            out.write("bb".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("bb", aTxt.asText("UTF-8"));
        aTxt.lock().releaseLock();
    }

    private static void write(File f, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }


    public void setUp() throws IOException {
        sock = new ServerSocket(0);

        new Thread(() -> {
            try {
                while (true) {
                    Socket socket = sock.accept();
                    new Thread(() -> {
                        try {
                            new FileSystemAgent(FileUtil.toFileObject(new File("/")).getFileSystem(), socket.getInputStream(), socket.getOutputStream()).run();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }).start();
                }
            } catch (IOException ex) {
                //ignore...
            }
        }).start();
    }

    @Override
    protected void tearDown() throws IOException {
        sock.close();
    }

    @ServiceProvider(service=MIMEResolver.class)
    public static final class MimeResolverImpl extends MIMEResolver {

        @Override
        public String findMIMEType(FileObject fo) {
            if ("txt".equals(fo.getExt())) {
                return "text/plain";
            }

            return null;
        }

    }

    public void testByteArray() throws Throwable {
        clearWorkDir();

        File wd = getWorkDir();
        File test1 = new File(wd, "test1"); test1.mkdir();
        File test2 = new File(wd, "test2"); test2.mkdir();
        File a = new File(test1, "a.txt");  write(a, "aa".getBytes(StandardCharsets.UTF_8));

        Socket s = new Socket("localhost", sock.getLocalPort());
        RemoteFileSystem rfs = new RemoteFileSystem(new SshRemoteDescription("", "", "", ""), s.getOutputStream(), s.getInputStream());

        FileObject rwd = rfs.findResource("/home/lahvac/src/jdk/jdk/src/java.base/share/classes/java/lang/");
        FileObject[] wdChildren = rwd.getChildren(); //TODO: ordering

        for (FileObject c : wdChildren) {
            if (c.isData()) {
                c.asBytes();
            }
        }
    }
}
