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
package org.netbeans.nbbuild.extlibs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 *
 */
public class SetupLimitModules extends Task {

    private String limitModulesProperty;
    private String releaseVersion;
    private String excludedModules;
    private String nbjdkHome;
    private File buildDir;
    private File cacheFile;

    public void setLimitModulesProperty(String limitModulesProperty) {
        this.limitModulesProperty = limitModulesProperty;
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }

    public void setExcludedModules(String excludedModules) {
        this.excludedModules = excludedModules;
    }

    public void setNbjdkHome(String nbjdkHome) {
        this.nbjdkHome = nbjdkHome;
    }

    public void setBuildDir(File buildDir) {
        this.buildDir = buildDir;
    }

    public void setCacheFile(File cacheFile) {
        this.cacheFile = cacheFile;
    }

    @Override
    public void execute() throws BuildException {
        try {
            Properties cache = new Properties();

            if (cacheFile != null && cacheFile.canRead()) {
                try (InputStream in = new FileInputStream(cacheFile)) {
                    cache.load(in);
                }
            }

            String cacheKey = nbjdkHome + "-" + releaseVersion;
            String limitedModules = cache.getProperty(cacheKey);

            if (limitedModules == null) {
                Path executable = buildDir.toPath().resolve("LimitModulesFromExcludeModules.java");
                try (OutputStream out = Files.newOutputStream(executable);
                     Writer w = new OutputStreamWriter(out)) {
                    w.write(COMPUTE_LIMIT_MODULES);
                }
                List<String> command = new ArrayList<>();
                command.add(new File(new File(nbjdkHome, "bin"), "java").getAbsolutePath());
                command.add(executable.toFile().getAbsolutePath());
                command.add(releaseVersion);
                command.addAll(Arrays.asList(excludedModules.split(",")));
                Process p = new ProcessBuilder(command).redirectError(Redirect.INHERIT).start();
                p.waitFor();
                StringBuilder limitModulesText = new StringBuilder();
                InputStream in = p.getInputStream();
                int r;
                while ((r = in.read()) != (-1)) {
                    limitModulesText.append((char) r);
                }
                limitedModules = limitModulesText.toString().trim();
                if (cacheFile != null) {
                    cache.put(cacheKey, limitedModules);

                    try (OutputStream out = new FileOutputStream(cacheFile)) {
                        cache.store(out, "");
                    }
                }
            }

            getProject().setNewProperty(limitModulesProperty, limitedModules);
        } catch (IOException | InterruptedException ex) {
            throw new BuildException(ex);
        }
    }

    private static final String COMPUTE_LIMIT_MODULES =
            """
            import com.sun.source.util.JavacTask;
            import java.io.IOException;
            import java.net.URI;
            import java.util.Arrays;
            import java.util.Collections;
            import java.util.HashSet;
            import java.util.LinkedList;
            import java.util.List;
            import java.util.Set;
            import java.util.stream.Collectors;
            import javax.lang.model.element.ModuleElement;
            import javax.lang.model.element.ModuleElement.RequiresDirective;
            import javax.lang.model.util.ElementFilter;
            import javax.tools.SimpleJavaFileObject;
            import javax.tools.ToolProvider;

            public class LimitModulesFromExcludeModules {

                public static void main(String[] args) throws IOException {
                    String release = args[0];
                    Set<String> excludedModules = new HashSet<>();

                    Arrays.stream(args)
                          .skip(1)
                          .forEach(excludedModules::add);

                    JavacTask task = (JavacTask)
                            ToolProvider.getSystemJavaCompiler()
                                        .getTask(null, null, null, List.of("--release", release), null, List.of(new JFOImpl(URI.create("mem://Test.java"), "")));

                    task.analyze();

                    String limitModules =
                        task.getElements()
                            .getAllModuleElements()
                            .stream()
                            .filter(m -> !m.getQualifiedName().toString().startsWith("jdk.internal."))
                            .filter(m -> canInclude(m, excludedModules))
                            .map(m -> m.getQualifiedName())
                            .collect(Collectors.joining(","));

                    System.out.println(limitModules);
                }

                private static boolean canInclude(ModuleElement m, Set<String> excludes) {
                    return Collections.disjoint(transitiveDependencies(m), excludes);
                }

                private static Set<String> transitiveDependencies(ModuleElement m) {
                    List<ModuleElement> todo = new LinkedList<>();
                    Set<ModuleElement> seenModules = new HashSet<>();

                    todo.add(m);

                    while (!todo.isEmpty()) {
                        ModuleElement current = todo.remove(0);

                        if (seenModules.add(current)) {
                            for (RequiresDirective rd : ElementFilter.requiresIn(current.getDirectives())) {
                                todo.add(rd.getDependency());
                            }
                        }
                    }

                    return seenModules.stream()
                                      .map(c -> c.getQualifiedName().toString())
                                      .collect(Collectors.toSet());
                }

                private static final class JFOImpl extends SimpleJavaFileObject {

                    private final String content;

                    public JFOImpl(URI uri, String content) {
                        super(uri, Kind.SOURCE);
                        this.content = content;
                    }

                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                        return content;
                    }

                }
            }
            """;
}
