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
package org.netbeans.nbbuild;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class DownloadUtils {
    public static URLConnection openConnection(Task task, final URL url, URI[] connectedVia) throws IOException {
        final URLConnection[] conn = { null };
        final List<Exception> errs = new CopyOnWriteArrayList<>();
        final CountDownLatch connected = new CountDownLatch(1);
        ExecutorService connectors = Executors.newFixedThreadPool(3);
        connectors.submit(() -> {
            String httpProxy = System.getenv("http_proxy");
            if (httpProxy != null) {
                try {
                    URI uri = new URI(httpProxy);
                    InetSocketAddress address = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                    URLConnection test = url.openConnection(proxy);
                    test.connect();
                    conn[0] = test;
                    connected.countDown();
                    if (connectedVia != null) {
                        connectedVia[0] = uri;
                    }
                } catch (IOException | URISyntaxException ex) {
                    errs.add(ex);
                }
            }
        });
        connectors.submit(() -> {
            String httpProxy = System.getenv("https_proxy");
            if (httpProxy != null) {
                try {
                    URI uri = new URI(httpProxy);
                    InetSocketAddress address = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                    URLConnection test = url.openConnection(proxy);
                    test.connect();
                    conn[0] = test;
                    connected.countDown();
                    if (connectedVia != null) {
                        connectedVia[0] = uri;
                    }
                } catch (IOException | URISyntaxException ex) {
                    errs.add(ex);
                }
            }
        });
        connectors.submit(() -> {
            try {
                URLConnection test = url.openConnection();
                test.connect();
                conn[0] = test;
                connected.countDown();
            } catch (IOException ex) {
                errs.add(ex);
            }
        });
        try {
            connected.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
        }
        if (conn[0] == null) {
            for (Exception ex : errs) {
                task.log(ex, Project.MSG_ERR);
            }
            throw new IOException("Cannot connect to " + url);
        }
        return conn[0];
    }

    public static URL mavenFileURL(MavenCoordinate mc) throws IOException {
        String cacheName = mc.toMavenPath();
        File local = new File(new File(new File(new File(System.getProperty("user.home")), ".m2"), "repository"), cacheName.replace('/', File.separatorChar));
        final String url;
        if (local.exists()) {
            url = local.toURI().toString();
        } else {
            url = "http://central.maven.org/maven2/" + cacheName;
        }
        URL u = new URL(url);
        return u;
    }

    public static class MavenCoordinate {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String extension;
        private final String classifier;

        private MavenCoordinate(String groupId, String artifactId, String version, String extension, String classifier) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.extension = extension;
            this.classifier = classifier;
        }
        
        public boolean hasClassifier() {
            return (! classifier.isEmpty());
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public String getExtension() {
            return extension;
        }

        public String getClassifier() {
            return classifier;
        }
        
        /**
         * @return filename of the artifact by maven convention: 
         *         {@code artifact-version[-classifier].extension}
         */
        public String toArtifactFilename() {
            return String.format("%s-%s%s.%s",
                    getArtifactId(),
                    getVersion(),
                    hasClassifier() ? ("-" + getClassifier()) : "",
                    getExtension()
            );
        }
        
        /**
         * @return The repository path for an artifact by maven convention: 
         *         {@code group/artifact/version/artifact-version[-classifier].extension}.
         *         In the group part all dots are replaced by a slash. 
         */        
        public String toMavenPath() {
            return String.format("%s/%s/%s/%s",
                    getGroupId().replace(".", "/"),
                    getArtifactId(),
                    getVersion(),
                    toArtifactFilename()
                    );
        }
        
        public static boolean isMavenFile(String gradleFormat) {
            return gradleFormat.split(":").length > 2;
        }
        
        /**
         * The maven coordinate is supplied in the form:
         * 
         * <p>{@code group:name:version:classifier@extension}</p>
         * 
         * <p>For the DownloadBinaries task the parts group, name and version
         * are requiered. classifier and extension are optional. The extension
         * has a default value of "jar".
         * 
         * @param gradleFormat artifact coordinated to be parse as a MavenCoordinate
         * @return 
         * @throws IllegalArgumentException if provided string fails to parse
         */
        public static MavenCoordinate fromGradleFormat(String gradleFormat) {
            if(! isMavenFile(gradleFormat)) {
                throw new IllegalArgumentException("Supplied string is not in gradle dependency format: " + gradleFormat);
            }
            String[] coordinateExtension = gradleFormat.split("@", 2);
            String extension;
            String coordinate = coordinateExtension[0];
            if (coordinateExtension.length > 1
                    && (!coordinateExtension[1].trim().isEmpty())) {
                extension = coordinateExtension[1];
            } else {
                extension = "jar";
            }
            String[] coordinates = coordinate.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];
            String version = coordinates[2];
            String classifier = "";
            if (coordinates.length > 3) {
                classifier = coordinates[3].trim();
            }
            return new MavenCoordinate(group, artifact, version, extension, classifier);
        }
        
        /**
         * The maven coordinate is supplied in the form:
         * 
         * <p>{@code group:name:version:extension:classifier}</p>
         * 
         * <p>For the DownloadBinaries task the parts group, name and version
         * are requiered. classifier and extension are optional. The extension
         * has a default value of "jar".
         * 
         * @param aucFormat artifact coordinated to be parse as a MavenCoordinate
         * @return 
         * @throws IllegalArgumentException if provided string fails to parse
         */
        public static MavenCoordinate fromAUCFormat(String aucFormat) {
            if(!isMavenFile(aucFormat)) {
                throw new IllegalArgumentException("Supplied string is not in AUC dependency format: " + aucFormat);
            }
            String[] coordinates = aucFormat.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];
            String version = coordinates[2];
            String extension = "jar";
            if (coordinates.length > 3) {
                extension = coordinates[3];
            }
            String classifier = "";
            if (coordinates.length > 4) {
                classifier = coordinates[4].trim();
            }
            return new MavenCoordinate(group, artifact, version, extension, classifier);
        }
    }
}
