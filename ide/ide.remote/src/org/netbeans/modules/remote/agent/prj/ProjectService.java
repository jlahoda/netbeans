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
package org.netbeans.modules.remote.agent.prj;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.netbeans.api.io.Hyperlink;
import org.netbeans.api.io.OutputColor;
import org.netbeans.api.io.ShowOperation;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.remote.AsynchronousConnection.ReceiverBuilder;
import org.netbeans.modules.remote.AsynchronousConnection.Sender;
import org.netbeans.modules.remote.Service;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.Utils;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.ContextBasedActionRequest;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.GetIORequest;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.IOTask;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.LoadProjectResponse;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.PathProjectRequest;
import org.netbeans.modules.remote.ide.prj.ProjectHandler.Task;
import org.netbeans.spi.io.InputOutputProvider;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 */
@ServiceProvider(service=Service.class)
public class ProjectService implements Service {

    @Override
    public String name() {
        return "prj";
    }

    @Override
    public void run(InputStream in, OutputStream out) {
        StreamMultiplexor projectMultiplexor = new StreamMultiplexor(in, out);
        Streams commands = projectMultiplexor.getStreamsForChannel(0);
        Streams ioControl = projectMultiplexor.getStreamsForChannel(1);
        ToClientIOProvider ioProvider = new ToClientIOProvider(projectMultiplexor, ioControl);
        new ReceiverBuilder<Task>(commands.in(), commands.out(), Task.class)
            .addHandler(Task.LOAD_PROJECT, PathProjectRequest.class, p -> {
            boolean isProject = false;
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.path);
                isProject = prjDir != null && ProjectManager.getDefault().findProject(prjDir) != null;
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete(new LoadProjectResponse(isProject));

            return result;
        }).addHandler(Task.GET_SUPPORTED_ACTIONS, PathProjectRequest.class, p -> {
            String[] actions = new String[0];
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.path);
                Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                if (prj != null) {
                   ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                   if (ap != null) {
                       actions = ap.getSupportedActions();
                   }
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete(actions);

            return result;
        }).addHandler(Task.IS_ENABLED_ACTIONS, ContextBasedActionRequest.class, p -> {
            Boolean enabled = false;
            try {
                FileObject prjDir = Utils.resolveLocalPath(p.projectPath);
                Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                if (prj != null) {
                   ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                   if (ap != null) {
                        List<Object> context = new ArrayList<>();
                        if (p.selectedFileObjectPath != null) {
                            context.add(Utils.resolveLocalPath(p.selectedFileObjectPath));
                        }
                        enabled = ap.isActionEnabled(p.action, Lookups.fixed(context.toArray()));
                   }
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }

            CompletableFuture<Object> result = new CompletableFuture<>();

            result.complete(enabled);

            return result;
        }).addHandler(Task.INVOKE_ACTIONS, ContextBasedActionRequest.class, p -> {
            CompletableFuture<Object> result = new CompletableFuture<>();
            Lookup inputOutputLookup = Lookups.fixed(ioProvider);
            Lookups.executeWith(new ProxyLookup(Lookups.exclude(Lookup.getDefault(), InputOutputProvider.class, org.openide.windows.IOProvider.class), inputOutputLookup), () -> {
                try {
                    FileObject prjDir = Utils.resolveLocalPath(p.projectPath);
                    Project prj = prjDir != null ? ProjectManager.getDefault().findProject(prjDir) : null;
                    if (prj != null) {
                       ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);

                       if (ap != null) {
                            List<Object> context = new ArrayList<>();
                            if (p.selectedFileObjectPath != null) {
                                context.add(Utils.resolveLocalPath(p.selectedFileObjectPath));
                            }
                            ap.invokeAction(p.action, Lookups.fixed(context.toArray()));
                       }
                    }
                } catch (IOException | IllegalArgumentException ex) {
                    Exceptions.printStackTrace(ex);
                }


                result.complete("done");
            });
            return result;
        }).startReceiver();
    }

    public static final class ToClientIOProvider implements InputOutputProvider<Integer, PrintWriter, Void, Void> {

        private final StreamMultiplexor main;
        private final Sender ioHandler;
        private final Map<Integer, Streams> io2IOStreams = new HashMap<>(); //TODO: clear!!

        public ToClientIOProvider(StreamMultiplexor main, Streams ioControl) {
            this.main = main;
            this.ioHandler = new Sender(ioControl.in(), ioControl.out());
        }

        @Override
        public String getId() {
            return "ToClientIOProvider";
        }

        @Override
        public Integer getIO(String name, boolean newIO, Lookup lookup) {
            try {
                int channel = ioHandler.sendAndReceive(IOTask.GET_IO, new GetIORequest(name), Integer.class).get();
                Streams ioStreams = main.getStreamsForChannel(channel);
                io2IOStreams.put(channel, ioStreams);
                return channel;
            } catch (IOException | InterruptedException | ExecutionException ex) {
                Exceptions.printStackTrace(ex);
                return -1;
            }
        }

        @Override
        public Reader getIn(Integer io) {
            return new CharArrayReader(new char[0]); //TODO: support input
        }

        @Override
        public PrintWriter getOut(Integer io) {
            OutputStreamWriter sink = new OutputStreamWriter(io2IOStreams.get(io).out());
            Writer flushingWriter = new Writer() { //TODO: BridgingOutputWriter does not auto-flush
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    sink.write(cbuf, off, len);
                    sink.flush();
                }

                @Override
                public void flush() throws IOException {
                    sink.flush();
                }

                @Override
                public void close() throws IOException {
                    sink.close();
                }
            };
            return new PrintWriter(flushingWriter);
        }

        @Override
        public PrintWriter getErr(Integer io) {
//            return new PrintWriter(io2IOStreams.get(io).out()); //TODO: separate out and err!
            return getOut(io); //TODO: separate out and err!
        }

        @Override
        public void print(Integer io, PrintWriter writer, String text, Hyperlink link, OutputColor color, boolean printLineEnd) {
            writer.print(text);
            if (printLineEnd) {
                writer.println();
            }
        }

        @Override
        public Lookup getIOLookup(Integer io) {
            return Lookup.EMPTY;
        }

        @Override
        public void resetIO(Integer io) {
        }

        @Override
        public void showIO(Integer io, Set<ShowOperation> operations) {
        }

        @Override
        public void closeIO(Integer io) {
        }

        @Override
        public boolean isIOClosed(Integer io) {
            return false;
        }

        @Override
        public Void getCurrentPosition(Integer io, PrintWriter writer) {
            return null;
        }

        @Override
        public void scrollTo(Integer io, PrintWriter writer, Void position) {
        }

        @Override
        public Void startFold(Integer io, PrintWriter writer, boolean expanded) {
            return null;
        }

        @Override
        public void endFold(Integer io, PrintWriter writer, Void fold) {
        }

        @Override
        public void setFoldExpanded(Integer io, PrintWriter writer, Void fold, boolean expanded) {
        }

        @Override
        public String getIODescription(Integer io) {
            return "Remote IO";
        }

        @Override
        public void setIODescription(Integer io, String description) {
        }

    }
}
