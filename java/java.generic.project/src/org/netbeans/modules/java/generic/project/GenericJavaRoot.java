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
package org.netbeans.modules.java.generic.project;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DirectiveTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.JavacTask;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.spi.java.classpath.ClassPathFactory;
import org.netbeans.spi.java.classpath.ClassPathImplementation;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;

/**
 *
 * @author jlahoda
 */
public class GenericJavaRoot {
    private static final RequestProcessor WORKER = new RequestProcessor(GenericJavaRoot.class.getName(), 1, false, false);
    private static final String PATH_SEPARATOR = System.getProperty("path.separator");

    private final Map<String, ClassPath> modulePathElement2ClassPath = new HashMap<>(); //TODO: cleanup?
    private File configurationFile;
    private List<Root> roots = new ArrayList<>();
    private final ChangeSupport cs = new ChangeSupport(this);
    private final GenericJavaProject project;
    private final URI projectDir;
    private AllModules allModules = new AllModules();
    private final FileChangeListener listener = new FileChangeAdapter() {
        @Override
        public void fileChanged(FileEvent fe) {
            scheduleUpdateRoots();
        }
        @Override
        public void fileDeleted(FileEvent fe) {
            scheduleUpdateRoots();
        }
        @Override
        public void fileDataCreated(FileEvent fe) {
            scheduleUpdateRoots();
        }
        @Override
        public void fileRenamed(FileRenameEvent fe) {
            scheduleUpdateRoots();
        }
        @Override
        public void fileFolderCreated(FileEvent fe) {
            scheduleUpdateRoots();
        }
    };
    private final Task update;

    public GenericJavaRoot(GenericJavaProject project) {
        this.project = project;
        this.projectDir = project.getProjectDirectory().toURI();
        this.roots = Collections.emptyList();
        this.update = WORKER.create(() -> {
            File newFile = new File(project.getConfigurationFile());
            if (!Objects.equals(configurationFile, newFile)) {
                if (configurationFile != null) {
                    FileUtil.removeFileChangeListener(listener, configurationFile);
                }
                configurationFile = newFile;
                FileUtil.addFileChangeListener(listener, configurationFile);
            }
            if (configurationFile.isFile()) {
                try {
                    updateRoots(Files.readString(configurationFile.toPath())); //TODO: encoding? UTF-8 might be OK.
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
        GenericJavaProject.getRootPreferences(project.getProjectDirectory())
                          .addPreferenceChangeListener(evt -> scheduleUpdateRoots());
        scheduleUpdateRoots();
    }

    public List<Root> getRoots() {
        return new ArrayList<>(roots);
    }

    public void addChangeListener(ChangeListener l) {
        cs.addChangeListener(l);
    }
    
    public void removeChangeListener(ChangeListener l) {
        cs.removeChangeListener(l);
    }

    private void scheduleUpdateRoots() {
        update.schedule(1000);
    }

    private void updateRoots(String log) {
        List<Root> newRoots = new ArrayList<>();
        Map<String, URL> name2Module = new HashMap<>(); //TODO: incorrect, only inferred from --module/module-source-path!
        String defaultSourceLevel = project.getProjectJavaPlatform().getSpecification().getVersion().toString();
        for (String line : log.split("\\R")) {
            if (!line.startsWith("+ javac ")) {
                continue;
            }
            line = line.substring("+ javac ".length());
            String[] parts = line.split(" +");
            String release = defaultSourceLevel;
            String module = null;
            String moduleSourcePath = null;
            ClassPath classPath = ClassPath.EMPTY;
            ClassPath modulePath = ClassPath.EMPTY;
            String outDir = null;
            List<String> extraArgs = new ArrayList<>();
            List<String> inputFiles = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                switch (parts[i]) {
                    case "--release":
                        release = parts[++i];
                        break;
                    case "--module":
                        module = parts[++i];
                        break;
                    case "--module-source-path":
                        moduleSourcePath = parts[++i];
                        break;
                    case "-classpath":
                    case "--class-path":
                        classPath = classPathFromString(parts[++i]);
                        break;
                    case "-d":
                        outDir = parts[++i];
                        break;
                    case "--module-path":
                        ArrayList<Object> modulePathElements = new ArrayList<>();
                        for (String element : parts[++i].split(PATH_SEPARATOR)) {
                            modulePathElements.add(modulePathElement2ClassPath.computeIfAbsent(element, el -> {
                                return ClassPathFactory.createClassPath(new ModuleClassPathImplementation(new File(new File(projectDir), element)));
                            }));
                        }
                        modulePath = ClassPathSupport.createProxyClassPath(modulePathElements.toArray(ClassPath[]::new));
                        break;
                    case "--patch-module":
                        String spec = parts[++i];
                        String[] els = spec.split("=");
                        extraArgs.add("--patch-module");
                        extraArgs.add(els[0] + "=" + projectDir.resolve(els[1]).getPath()); //Ugh.
                        break;
                    case "-implicit:none": break; //ignore...
                    default:
                        if (parts[i].endsWith(".java")) {//TODO: case sensitive
                            inputFiles.add(parts[i]);
                        } else {
                            extraArgs.add(parts[i]);
                        }
                        break;
                }
            }
            if (module != null && moduleSourcePath != null) {
                String[] moduleSourcePathParts = moduleSourcePath.split(Pattern.quote(PATH_SEPARATOR));
                if (moduleSourcePathParts.length > 1) {
                    System.err.println("unsupported module source path!");
                }
                String[] moduleParts = module.split(",");
                for (String oneModule : moduleParts) {
                    if (outDir == null) {
                        //TODO: warning!
                        System.err.println("unsupported, no output dir!");
                        continue;
                    }
                    URL rootURL;
                    URL outDirURL;
                    try {
                        if (moduleSourcePathParts[0].contains("*")) {
                            rootURL = projectDir.resolve(moduleSourcePathParts[0].replace("*", oneModule)).toURL();
                        } else {
                            rootURL = projectDir.resolve(moduleSourcePathParts[0]).resolve(oneModule).toURL();
                        }
                        if (!rootURL.toString().endsWith("/")) {//TODO: performance
                            rootURL = new URL(rootURL.toString() + "/");
                        }
                        if (!outDir.endsWith("/")) outDir += "/";
                        outDirURL = projectDir.resolve(outDir).resolve(oneModule + "/").toURL();
                        name2Module.put(oneModule, outDirURL);
                        File moduleInfo = new File(new File(rootURL.toURI()), "module-info.java");
                        ClassPath currentProjectModuleCP = ClassPathFactory.createClassPath(new ModuleInfoClassPathImplementation(allModules, moduleInfo));
                        ClassPath thisModularModuleCP = ClassPathSupport.createProxyClassPath(modulePath, currentProjectModuleCP);
                        Root root = new Root(oneModule, rootURL,
                                release,
                                ClassPathSupport.createProxyClassPath(classPath, thisModularModuleCP),
                                thisModularModuleCP,
                                ClassPathSupport.createClassPath(rootURL),
                                outDirURL,
                                extraArgs);
                        newRoots.add(root);
                    } catch (MalformedURLException | URISyntaxException ex) {
                        Exceptions.printStackTrace(ex);
                        continue;
                    }
                }
            } else {
                URL rootURL;
                if (inputFiles.size() > 0) {
                    try {
                        FileObject file = URLMapper.findFileObject(projectDir.resolve(inputFiles.get(0)).toURL());
                        if (file == null) {
                            System.err.println("input file does not exist!");
                            continue;
                        }
                        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, d -> {}, null, null, Arrays.asList(new JFOImpl(file.asText()))); //TODO: encoding
                        PackageTree pack = task.parse().iterator().next().getPackage();
                        int depth = 0;
                        if (pack != null) {
                            ExpressionTree packageName = pack.getPackageName();
                            while (packageName.getKind() == Kind.MEMBER_SELECT) {
                                depth++;
                                packageName = ((MemberSelectTree) packageName).getExpression();
                            }
                            depth++;
                        }
                        file = file.getParent();
                        while (depth-- > 0) {
                            file = file.getParent();
                        }
                        rootURL = file.toURL();
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                        continue;
                    } catch (URISyntaxException ex) {
                        Exceptions.printStackTrace(ex);
                        continue;
                    }
                } else {
                    System.err.println("no input files...");
                    continue;
                }
                try {
                    String displayName = rootURL.toString().replaceAll(".*/([^/]*)/([^/]*)/$", "$1 ($2)");
                    File moduleInfo = new File(new File(rootURL.toURI()), "module-info.java");
                    ClassPath currentProjectModuleCP = ClassPathFactory.createClassPath(new ModuleInfoClassPathImplementation(allModules, moduleInfo));
                    ClassPath thisModularModuleCP = ClassPathSupport.createProxyClassPath(modulePath, currentProjectModuleCP);
                    Root root = new Root(displayName, rootURL,
                            release,
                            ClassPathSupport.createProxyClassPath(classPath, thisModularModuleCP),
                            thisModularModuleCP,
                            ClassPathSupport.createClassPath(rootURL),
                            projectDir.resolve(outDir).toURL(),
                            extraArgs);
                    newRoots.add(root);
                } catch (MalformedURLException | URISyntaxException ex) {
                    Exceptions.printStackTrace(ex);
                    continue;
                }
            }
            //TODO: includes/excludes??
        }
        //TODO: synchronization, more precise diffing??
        roots = newRoots;
        allModules.setName2ProjectModule(name2Module);
        cs.fireChange();
    }

    private ClassPath classPathFromString(String spec) {
        List<URL> roots = new ArrayList<>();
        for (String piece : spec.split(Pattern.quote(PATH_SEPARATOR))) {
            piece = piece.replace(PATH_SEPARATOR, "/");
            File entry = new File(piece);
            if (!entry.isAbsolute()) {
                entry = new File(projectDir.resolve(piece));
            }
            roots.add(FileUtil.urlForArchiveOrDir(entry));
        }
        return ClassPathSupport.createClassPath(roots.toArray(new URL[0]));
    }

    public static class Root {
        private final String displayName;
        private final URL root;
        private final String sourceLevel;
        private final ClassPath classPath;
        private final ClassPath modulePath;
        private final ClassPath sourcePath;
        private final URL target;
        //processor path?
        private final List<String> extraCompilationOptions;

        public Root(String displayName, URL root, String sourceLevel, ClassPath classPath, ClassPath modulePath, ClassPath sourcePath, URL target, List<String> extraCompilationOptions) {
            this.displayName = displayName;
            this.root = root;
            this.sourceLevel = sourceLevel;
            this.classPath = classPath;
            this.modulePath = modulePath;
            this.sourcePath = sourcePath;
            this.target = target;
            this.extraCompilationOptions = extraCompilationOptions;
        }


        public String getDisplayName() {
            return displayName;
        }

        public URL getRoot() {
            return root;
        }

        public String getSourceLevel() {
            return sourceLevel;
        }

        public ClassPath getClassPath() {
            return classPath;
        }

        public ClassPath getModulePath() {
            return modulePath;
        }

        public ClassPath getSourcePath() {
            return sourcePath;
        }

        public URL getTarget() {
            return target;
        }

        public List<String> getExtraCompilationOptions() {
            return extraCompilationOptions;
        }

        public Pattern getIncludes() {
            return null;
        }

        public Pattern getExcludes() {
            return null;
        }
    }

    private static final class AllModules {
        private Map<String, URL> name2ProjectModule = new HashMap<>();
        private final ChangeSupport cs = new ChangeSupport(this);

        public void setName2ProjectModule(Map<String, URL> name2ProjectModule) {
            this.name2ProjectModule = name2ProjectModule;
            cs.fireChange();
        }

        public Map<String, URL> getName2ProjectModule() {
            return name2ProjectModule;
        }
        
        public void addChangeListener(ChangeListener l) {
            cs.addChangeListener(l);
        }
    }

    private static final class ModuleClassPathImplementation extends FileChangeAdapter implements ClassPathImplementation {

        private final File path;
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private List<PathResourceImplementation> resources;

        public ModuleClassPathImplementation(File path) {
            this.path = path;
            FileUtil.addFileChangeListener(this, path);
            updateModules();
        }

        private void updateModules() { //TODO: synchronization
            File[] files = path.listFiles();
            List<PathResourceImplementation> resources = new ArrayList<>();
            if (files != null) {
                for (File candidate : files) {
                    try {
                        URL url = candidate.toURI().toURL();
                        if (candidate.isDirectory() || FileUtil.isArchiveFile(url)) {
                            url = FileUtil.urlForArchiveOrDir(candidate);
                            if (url != null) {
                                resources.add(ClassPathSupport.createResource(url));
                            }
                        }
                    } catch (MalformedURLException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            List<PathResourceImplementation> oldResources = this.resources;
            this.resources = resources;
            pcs.firePropertyChange(PROP_RESOURCES, oldResources, resources);
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            updateModules();
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            updateModules();
        }
        
        @Override
        public List<? extends PathResourceImplementation> getResources() {
            return resources;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
            pcs.addPropertyChangeListener(pl);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
            pcs.removePropertyChangeListener(pl);
        }
        
    }

    private static final class ModuleInfoClassPathImplementation extends FileChangeAdapter implements ClassPathImplementation, ChangeListener {

        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private List<PathResourceImplementation> resources = new ArrayList<>();
        private final AllModules allModules;
        private final File moduleInfo;

        public ModuleInfoClassPathImplementation(AllModules allModules, File moduleInfo) {
            this.allModules = allModules;
            this.moduleInfo = moduleInfo;
            FileUtil.addFileChangeListener(this, moduleInfo);
            allModules.addChangeListener(this);
        }

        @Override
        public synchronized void fileChanged(FileEvent fe) {
            resources = null;
            pcs.firePropertyChange(PROP_RESOURCES, null, null);
        }

        @Override
        public synchronized void stateChanged(ChangeEvent e) {
            resources = null;
            pcs.firePropertyChange(PROP_RESOURCES, null, null);
        }

        @Override
        public synchronized List<? extends PathResourceImplementation> getResources() {
            if (resources != null) {
                return resources;
            }
            List<PathResourceImplementation> newResources = new ArrayList<>();
            if (moduleInfo.canRead())
            try {
                Set<String> dependencies = new HashSet<>();
                JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, d -> {}, null, null, Arrays.asList(new JFOImpl(Files.readString(moduleInfo.toPath()))));
                CompilationUnitTree cut = task.parse().iterator().next();
                ModuleTree module = cut.getModule();
                for (DirectiveTree d : module.getDirectives()) {
                    if (d.getKind() == Kind.REQUIRES) {
                        dependencies.add(((RequiresTree) d).getModuleName().toString());
                    }
                }
                for (Iterator<Map.Entry<String, URL>> it = allModules.getName2ProjectModule().entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, URL> name2Binary = it.next();
                    if (dependencies.contains(name2Binary.getKey())) {
                        newResources.add(ClassPathSupport.createResource(name2Binary.getValue()));
                    }
                }
            } catch (IOException | URISyntaxException ex) {
                Exceptions.printStackTrace(ex);
            }
            resources = newResources;
            System.err.println("module: " + moduleInfo);
            System.err.println("path: " + newResources);
            return newResources;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
            pcs.addPropertyChangeListener(pl);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
            pcs.removePropertyChangeListener(pl);
        }
        
    }

    private static final class JFOImpl extends SimpleJavaFileObject {

        private final String code;
        public JFOImpl(String code) throws URISyntaxException {
            super(new URI("mem://Test.java"), JavaFileObject.Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }
    }
}
