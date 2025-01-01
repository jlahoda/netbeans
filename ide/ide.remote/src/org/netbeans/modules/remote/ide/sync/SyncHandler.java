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
package org.netbeans.modules.remote.ide.sync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.lsp.client.debugger.api.DAPLineBreakpoint;
import org.netbeans.modules.remote.RemoteInvocation;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.RemoteManager;
import org.netbeans.modules.remote.ide.fs.RemoteFileSystem;
import org.openide.filesystems.FileObject;
import org.openide.text.Line.ShowOpenType;
import org.openide.text.Line.ShowVisibilityType;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Pair;

/**
 *
 * @author lahvac
 */
public class SyncHandler {

    public static void startSync(RemoteFileSystem rfs, ProgressHandle progress) {
        SyncHandler handler = RemoteManager.getDefault().getService(rfs.getRemoteDescription(), "sync", streams -> new SyncHandler(streams.in(), streams.out(), rfs.getRoot()));

        handler.startSync(progress);
    }

    private final FileObject remoteRoot;
    private final SyncInterface proxy;

    public SyncHandler(InputStream in, OutputStream out, FileObject remoteRoot) {
        this.remoteRoot = remoteRoot;
        proxy = RemoteInvocation.caller(in, out, SyncInterface.class);
    }

    private void startSync(ProgressHandle progress) {
        progress.switchToDeterminate(3);
        progress.progress("Synchronizing projects...", 0);
        List<Project> projects = new ArrayList<>();

        for (String prj : proxy.openedProjects()) {
            try {
                FileObject prjFO = Utils.resolveRemotePath(remoteRoot, prj);
                Project project = ProjectManager.getDefault().findProject(prjFO);

                if (project != null) {
                    projects.add(project);
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        OpenProjects.getDefault().open(projects.toArray(Project[]::new), false);

        progress.progress("Synchronizing opened editors...", 1);

        List<Pair<FileObject, Integer>> openedEditors = new ArrayList<>();

        for (EditorDescription editor : proxy.openedEditors()) {
            try {
                FileObject prjFO = Utils.resolveRemotePath(remoteRoot, editor.getPath());

                if (prjFO != null) {
                    openedEditors.add(Pair.of(prjFO, editor.getCaret()));
                }
            } catch (IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        Pair<FileObject, Integer> first = openedEditors.isEmpty() ? null : openedEditors.get(0);
        Collections.reverse(openedEditors);

        openedEditors.stream().forEach(desc -> {
            //TODO: FOCUS(!)
            NbDocument.openDocument(desc.first(), desc.second(), ShowOpenType.OPEN, desc == first ? ShowVisibilityType.FOCUS : ShowVisibilityType.NONE);
        });

        progress.progress("Synchronizing breakpoints...", 2);

        for (BreakpointDescription breakpoint : proxy.breakpoints()) {
            FileObject file = Utils.resolveRemotePath(remoteRoot, breakpoint.getPath());

            if (file == null) {
                continue;
            }

            DAPLineBreakpoint brk = DAPLineBreakpoint.create(file, breakpoint.getLine());

            brk.setCondition(breakpoint.getCondition());
            DebuggerManager.getDebuggerManager().addBreakpoint(brk);
        }

        progress.progress("All done", 3);
    }

    public interface SyncInterface {
        public EditorDescription[] openedEditors();
        public String[] openedProjects();
        public BreakpointDescription[] breakpoints();
    }

    public static class EditorDescription {
        private String path;
        private int caret;

        public EditorDescription() {
        }

        public EditorDescription(String path, int caret) {
            this.path = path;
            this.caret = caret;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getCaret() {
            return caret;
        }

        public void setCaret(int caret) {
            this.caret = caret;
        }

    }

    public static class BreakpointDescription {
        private String path;
        private int line;
        private String condition;

        public BreakpointDescription() {
        }

        public BreakpointDescription(String path, int line, String condition) {
            this.path = path;
            this.line = line;
            this.condition = condition;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

    }
}
