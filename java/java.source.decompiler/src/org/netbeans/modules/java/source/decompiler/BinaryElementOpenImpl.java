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
package org.netbeans.modules.java.source.decompiler;

import com.sun.source.util.TreePath;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ClasspathInfo.PathKind;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.java.BinaryElementOpen;
import org.netbeans.modules.java.source.indexing.JavaIndex;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=BinaryElementOpen.class, position=50)
public class BinaryElementOpenImpl implements BinaryElementOpen {

    private static final Logger LOG = Logger.getLogger(BinaryElementOpenImpl.class.getName());
    private static final String DISABLE_ERRORS = "disable-java-errors";         //NOI18N
    private static final String CLASSFILE_ROOT = "classfile-root";              //NOI18N
    private static final String CLASSFILE_BINNAME = "classfile-binaryName";     //NOI18N

    @Override
    public boolean open(ClasspathInfo cpInfo, ElementHandle<? extends Element> toOpen, AtomicBoolean cancel) {
        try {
            DecompileResult decompileResult = decompile(cpInfo, toOpen, cancel);
            if (decompileResult == null) {
                LOG.info("Cannot decompile: " + toOpen); //NOI18N
                return false;
            }
            final File sourceRoot = new File (JavaIndex.getIndex(decompileResult.root.toURL()),"gensrc");     //NOI18N
            final FileObject sourceRootFO = FileUtil.createFolder(sourceRoot);
            if (sourceRootFO == null) {
                LOG.info("Cannot create folder: " + sourceRoot); //NOI18N
                return false;
            }
            FileObject source = FileUtil.createData(sourceRootFO, decompileResult.binaryName.replace(".", "/") + ".java");
            try (OutputStream out = source.getOutputStream()) {
                out.write(decompileResult.content.getBytes(StandardCharsets.UTF_8));
            }

            FileUtil.toFile(source).setReadOnly();

            source.setAttribute(DISABLE_ERRORS, true);
            source.setAttribute(CLASSFILE_ROOT, decompileResult.root.toURL());
            source.setAttribute(CLASSFILE_BINNAME, decompileResult.binaryName);

            final int[] pos = new int[] {-1};

            try {
                JavaSource.create(cpInfo, source).runUserActionTask(new Task<CompilationController>() {
                    @Override public void run(CompilationController parameter) throws Exception {
                        if (cancel.get()) return ;
                        parameter.toPhase(JavaSource.Phase.RESOLVED);

                        Element el = toOpen.resolve(parameter);

                        if (el == null) return ;

                        TreePath p = parameter.getTrees().getPath(el);

                        if (p == null) return ;

                        pos[0] = (int) parameter.getTrees().getSourcePositions().getStartPosition(p.getCompilationUnit(), p.getLeaf());
                    }
                }, true);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }

            if (pos[0] != (-1) && !cancel.get()) {
                return open(source, pos[0]);
            } else {
                return false;
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean open(FileObject source, int pos) {
        return org.netbeans.api.java.source.UiUtils.open(source, pos);
    }

    public DecompileResult decompile(ClasspathInfo cpInfo, ElementHandle<? extends Element> toOpen, AtomicBoolean cancel) {
        //TODO: multi-release jars on classpath
        String[] binaryName = new String[1];
        JavaSource js = JavaSource.create(cpInfo);
        try {
            js.runUserActionTask(new Task<CompilationController>() {
                @Override
                public void run(CompilationController cc) throws Exception {
                    cc.toPhase(Phase.ELEMENTS_RESOLVED);

                    Element toOpenInstance = toOpen.resolve(cc);

                    if (toOpenInstance != null && toOpenInstance.getKind() == ElementKind.MODULE) {
                        binaryName[0] = "module-info"; //NOI18N
                    } else {
                        final ClassPath cp = ClassPathSupport.createProxyClassPath(
                                cpInfo.getClassPath(PathKind.BOOT),
                                cpInfo.getClassPath(PathKind.COMPILE),
                                cpInfo.getClassPath(PathKind.SOURCE));
                        final TypeElement te = toOpenInstance != null ? cc.getElementUtilities().outermostTypeElement(toOpenInstance) : null;

                        if (te == null) {
                            LOG.info("Cannot resolve element: " + toOpen.toString() + " on classpath: " + cp.toString()); //NOI18N
                            return;
                        }

                        binaryName[0] = cc.getElements().getBinaryName(te).toString();  //NOI18N
                    }
                }
            }, true);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
        if (binaryName[0] == null) {
            return null;
        }
        ClassPath complete = ClassPathSupport.createProxyClassPath(cpInfo.getClassPath(PathKind.BOOT), cpInfo.getClassPath(PathKind.COMPILE));
        String[] result = new String[1];
        File root = Places.getCacheSubdirectory("fernflower");
        Fernflower decompiler = new Fernflower(new IBytecodeProvider() {
            @Override
            public byte[] getBytecode(String extPath, String intPath) throws IOException {
                FileObject resource = complete.findResource(extPath.substring(root.getAbsolutePath().length() + 1));
                if (resource == null) {
                    return null;
                }
                try (InputStream in = resource.getInputStream()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    FileUtil.copy(in, baos);
                    return baos.toByteArray();
                }
            }
        }, new IResultSaver() {
            @Override
            public void saveFolder(String path) {
            }

            @Override
            public void copyFile(String source, String path, String entryName) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
                if (qualifiedName.equals(binaryName[0].replace('.', '/'))) {
                    result[0] = content;
                }
            }

            @Override
            public void createArchive(String path, String archiveName, Manifest manifest) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void saveDirEntry(String path, String archiveName, String entryName) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void copyEntry(String source, String path, String archiveName, String entry) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void closeArchive(String path, String archiveName) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }, new HashMap<>(), new IFernflowerLogger() {
            @Override
            public void writeMessage(String message, IFernflowerLogger.Severity severity) {
                System.err.println(severity.toString() + ": " + message);
            }

            @Override
            public void writeMessage(String message, IFernflowerLogger.Severity severity, Throwable t) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        String pack = binaryName[0].substring(0, binaryName[0].lastIndexOf('.')).replace(".", "/");
        String simpleName = binaryName[0].substring(binaryName[0].lastIndexOf('.') + 1);
        FileObject classFileRoot = null;
        for (FileObject directory : complete.findAllResources(pack)) {
            for (FileObject classfile : directory.getChildren()) {
                if ("class".equals(classfile.getExt()) && (classfile.getName().equals(simpleName) || classfile.getName().startsWith(simpleName + "$"))) {
                    decompiler.addSource(new File(root, complete.getResourceName(classfile)));
                }
            }
            if (classFileRoot == null) {
                classFileRoot = complete.findOwnerRoot(directory);
            }
        }
        if (classFileRoot != null) {
            decompiler.decompileContext();
            return new DecompileResult(classFileRoot, binaryName[0], result[0]);
        } else {
            return null;
        }
    }

    public static class DecompileResult {
        public final FileObject root;
        public final String binaryName;
        public final String content;

        public DecompileResult(FileObject root, String binaryName, String content) {
            this.root = root;
            this.binaryName = binaryName;
            this.content = content;
        }
        
    }
}
