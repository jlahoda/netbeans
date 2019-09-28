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
package org.netbeans.modules.java.lsp.server.debugging;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.IEvaluatableBreakpoint;
import com.microsoft.java.debug.core.UsageDataSession;
import com.microsoft.java.debug.core.adapter.HotCodeReplaceEvent;
import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IExecuteProvider;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.adapter.ProtocolServer;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadGroupReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.VoidValue;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.Transport;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.connect.spi.Connection;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequestManager;
import com.sun.source.util.TreePath;
import io.reactivex.Observable;
import io.reactivex.Observer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.modules.java.lsp.server.debugging.api.DebuggerStarter;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputListener;
import org.openide.windows.OutputWriter;

/**
 *
 * @author lahvac
 */
public class Debugger {

    private static final Logger LOG = Logger.getLogger(Debugger.class.getName());

    public static int startDebugger() throws IOException {
        int port = 10001;
        ServerSocket vsCodeSide = new ServerSocket(port, 1, Inet4Address.getLoopbackAddress());
        System.err.println("Debugger listens on port: " + port);
        new Thread(() -> {
            while (true) {
                try {
                    Socket vsCodeSocket = vsCodeSide.accept();

                    SourceProvider sourceProvider = new SourceProvider();
                    ProviderContext context = new ProviderContext();
                    context.registerProvider(IVirtualMachineManagerProvider.class, new IVirtualMachineManagerProvider() {
                        @Override
                        public VirtualMachineManager getVirtualMachineManager() {
                            return new VirtualMachineManagerImpl(sourceProvider);
                        }

                    });
                    context.registerProvider(IHotCodeReplaceProvider.class, new IHotCodeReplaceProvider() {
                        @Override
                        public void onClassRedefined(Consumer<List<String>> arg0) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }

                        @Override
                        public CompletableFuture<List<String>> redefineClasses() {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }

                        @Override
                        public Observable<HotCodeReplaceEvent> getEventHub() {
                            return new Observable<HotCodeReplaceEvent>() {
                                @Override
                                protected void subscribeActual(Observer<? super HotCodeReplaceEvent> arg0) {
                                    System.err.println("arg0= " +arg0);
                                }
                            };
                        }
                    });
                    context.registerProvider(ISourceLookUpProvider.class, sourceProvider);
                    context.registerProvider(IEvaluationProvider.class, new IEvaluationProvider() {
                        @Override
                        public boolean isInEvaluation(ThreadReference thread) {
                            return false;
                        }

                        @Override
                        public CompletableFuture<Value> evaluate(String arg0, ThreadReference arg1, int arg2) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }

                        @Override
                        public CompletableFuture<Value> evaluate(String arg0, ObjectReference arg1, ThreadReference arg2) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }

                        @Override
                        public CompletableFuture<Value> evaluateForBreakpoint(IEvaluatableBreakpoint arg0, ThreadReference arg1) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }

                        @Override
                        public CompletableFuture<Value> invokeMethod(ObjectReference arg0, String arg1, String arg2, Value[] arg3, ThreadReference arg4, boolean arg5) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }

                        @Override
                        public void clearState(ThreadReference arg0) {
                        }
                    });
                    context.registerProvider(ICompletionsProvider.class, new ICompletionsProvider() {
                        @Override
                        public List<Types.CompletionItem> codeComplete(StackFrame arg0, String arg1, int arg2, int arg3) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                    });
                    context.registerProvider(IExecuteProvider.class, new IExecuteProvider() {
                        @Override
                        public Process launch(Requests.LaunchArguments la) throws IOException {
                            String filePath = la.mainClass;
                            FileObject file = filePath != null ? FileUtil.toFileObject(new File(filePath)) : null;
                            if (file == null) {
                                throw new IOException("Missing file: " + la.mainClass);
                            }
                            return new LaunchingVirtualMachine(file, sourceProvider).runWithoutDebugger();
                        }
                    });
                    ProtocolServer server = new ProtocolServer(vsCodeSocket.getInputStream(), vsCodeSocket.getOutputStream(), context);
                    server.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }).start();
        return port;
    }

    private static class VirtualMachineManagerImpl implements VirtualMachineManager {

        private final SourceProvider sourceProvider;
        private final VirtualMachineManager delegate;
        private final LaunchingConnector connector;

        public VirtualMachineManagerImpl(SourceProvider sourceProvider) {
            UsageDataSession.enableJdiEventSequence(); //Temp

            this.sourceProvider = sourceProvider;
            this.delegate = Bootstrap.virtualMachineManager();
            this.connector = new LaunchingConnector() {
                @Override
                public VirtualMachine launch(Map<String, ? extends Connector.Argument> args) throws IOException, IllegalConnectorArgumentsException, VMStartException {
                    String filePath = args.get("main").value();
                    FileObject file = filePath != null ? FileUtil.toFileObject(new File(filePath)) : null;
                    if (file == null) {
                        throw new IllegalConnectorArgumentsException("Missing file", "main");
                    }
                    return new LaunchingVirtualMachine(file, sourceProvider);
                }

                @Override
                public String name() {
                    return "NetBeans run";
                }

                @Override
                public String description() {
                    return "NetBeans run";
                }

                @Override
                public Transport transport() {
                    return delegate.defaultConnector().transport();
                }

                @Override
                public Map<String, Connector.Argument> defaultArguments() {
                    return new HashMap<String, Connector.Argument>() {{
                        put("suspend", new ArgumentImpl("suspend"));
                        put("options", new ArgumentImpl("options"));
                        put("main", new ArgumentImpl("main"));
                        put("home", new ArgumentImpl("home"));
                    }};
                }
            };
        }

        @Override
        public LaunchingConnector defaultConnector() {
            return connector;
        }

        @Override
        public List<LaunchingConnector> launchingConnectors() {
            return Arrays.asList(connector);
        }

        @Override
        public List<AttachingConnector> attachingConnectors() {
            return Collections.emptyList();
        }

        @Override
        public List<ListeningConnector> listeningConnectors() {
            return Collections.emptyList();
        }

        @Override
        public List<Connector> allConnectors() {
            return Arrays.asList(connector);
        }

        @Override
        public List<VirtualMachine> connectedVirtualMachines() {
            return delegate.connectedVirtualMachines();
        }

        @Override
        public int majorInterfaceVersion() {
            return delegate.majorInterfaceVersion();
        }

        @Override
        public int minorInterfaceVersion() {
            return delegate.minorInterfaceVersion();
        }

        @Override
        public VirtualMachine createVirtualMachine(Connection arg0, Process arg1) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public VirtualMachine createVirtualMachine(Connection arg0) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static final class ArgumentImpl implements Connector.Argument {

        private final String label;
        private String value;

        public ArgumentImpl(String label) {
            this.label = label;
        }

        @Override
        public String name() {
            return label;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public String description() {
            return label;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean isValid(String arg0) {
            return true;
        }

        @Override
        public boolean mustSpecify() {
            return false;
        }

    }

    private static final class LaunchingVirtualMachine implements VirtualMachine {
        private VirtualMachine delegate;
        private boolean inited;
        private final FileObject toRun;
        private final SourceProvider sourceProvider;
        private final InputStream out;
        private final InputStream err;
        private final IOProvider ioProvider;
        private final ActionProgress progress;
        private int exitCode;
        private boolean finished;

        public LaunchingVirtualMachine(FileObject toRun, SourceProvider sourceProvider) {
            this.toRun = toRun;
            this.sourceProvider = sourceProvider;
            Pair<InputStream, OutputStream> outPair = createCopyingStreams();
            out = outPair.first();
            Pair<InputStream, OutputStream> errPair = createCopyingStreams();
            err = errPair.first();
            ioProvider = new IOProviderImpl(outPair.second(), errPair.second());
            progress = new ActionProgress() {
                @Override
                protected void started() {}
                @Override
                public void finished(boolean success) {
                    try {
                        outPair.second().close();
                    } catch (IOException ex) {
                        LOG.log(Level.FINE, null, ex);
                    }
                    try {
                        errPair.second().close();
                    } catch (IOException ex) {
                        LOG.log(Level.FINE, null, ex);
                    }
                    synchronized (LaunchingVirtualMachine.this) {
                        exitCode = success ? 0 : 1;
                        finished = true;
                        LaunchingVirtualMachine.this.notifyAll();
                    }
                }
            };
        }

        public Process runWithoutDebugger() throws IOException {
            Lookup testLookup = Lookups.singleton(toRun);
            ActionProvider provider = null;
            String command = ActionProvider.COMMAND_TEST_SINGLE;
            for (ActionProvider ap : Lookup.getDefault().lookupAll(ActionProvider.class)) {
                if (new HashSet<>(Arrays.asList(ap.getSupportedActions())).contains(command) &&
                    ap.isActionEnabled(command, testLookup)) {
                    provider = ap;
                    break;
                }
            }
            if (provider == null) {
                throw new IOException("Cannot find run action!"); //TODO: message, locations
            }
            provider.invokeAction(command, Lookups.fixed(toRun, ioProvider, progress));
            return process();
        }

        private VirtualMachine delegate() throws VMDisconnectedException {
            if (!inited) {
                inited = true;
                try {
                    Lookup testLookup = Lookups.singleton(toRun);
                    ActionProvider provider = null;
                    String command = ActionProvider.COMMAND_DEBUG_TEST_SINGLE;
                    for (ActionProvider ap : Lookup.getDefault().lookupAll(ActionProvider.class)) {
                        if (new HashSet<>(Arrays.asList(ap.getSupportedActions())).contains(command) &&
                            ap.isActionEnabled(command, testLookup)) {
                            provider = ap;
                            break;
                        }
                    }
                    if (provider == null) {
                        throw new VMDisconnectedException("Cannot find debug action!"); //TODO: message, locations
                    }
                    //from java.openjdk.project's JPDAStart:
                    ListeningConnector lc = null;
                    Iterator<ListeningConnector> i = Bootstrap.virtualMachineManager().
                            listeningConnectors().iterator();
                    for (; i.hasNext();) {
                        lc = i.next();
                        Transport t = lc.transport();
                        if (t != null && t.name().equals("dt_socket")) {
                            break;
                        }
                    }
                    if (lc == null) {
                        throw new RuntimeException
                                    ("No trasports named dt_socket found!"); //NOI18N
                    }
                    // TODO: revisit later when http://developer.java.sun.com/developer/bugParade/bugs/4932074.html gets integrated into JDK
                    // This code parses the address string "HOST:PORT" to extract PORT and then point debugee to localhost:PORT
                    // This is NOT a clean solution to the problem but it SHOULD work in 99% cases
                    Map<String, Connector.Argument> args = lc.defaultArguments();
                    String address = lc.startListening(args);
                    //                try {
                    int port = Integer.parseInt(address.substring(address.indexOf(':') + 1));
                    //                    getProject ().setNewProperty (getAddressProperty (), "localhost:" + port);
                    Connector.IntegerArgument portArg = (Connector.IntegerArgument) args.get("port"); //NOI18N
                    portArg.setValue(port);
                    //                        lock[0] = port;
                    //                } catch (NumberFormatException e) {
                    // this address format is not known, use default
                    //                    getProject ().setNewProperty (getAddressProperty (), address);
                    //                    lock[0] = address;
                    //                }
                    provider.invokeAction(command, Lookups.fixed(toRun, ioProvider, progress, new DebuggerStarter() {
                        @Override
                        public int startDebugger(ClassPath sources) {
                            sourceProvider.sources = sources != null ? sources : ClassPath.EMPTY;
                            return port;
                        }
                    }));

                    delegate = lc.accept(args);
                } catch (IOException ex) {
                    Logger.getLogger(Debugger.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalConnectorArgumentsException ex) {
                    Logger.getLogger(Debugger.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (delegate == null) {
                throw new VMDisconnectedException();
            }
            return delegate;
        }
//        @Override XXX
//        public List<ModuleReference> allModules() {
//            return delegate().allModules();
//        }

        @Override
        public List<ReferenceType> classesByName(String arg0) {
            return delegate().classesByName(arg0);
        }

        @Override
        public List<ReferenceType> allClasses() {
            return delegate().allClasses();
        }

        @Override
        public void redefineClasses(Map<? extends ReferenceType, byte[]> arg0) {
            delegate().redefineClasses(arg0);
        }

        @Override
        public List<ThreadReference> allThreads() {
            return delegate().allThreads();
        }

        @Override
        public void suspend() {
            delegate().suspend();
        }

        @Override
        public void resume() {
            delegate().resume();
        }

        @Override
        public List<ThreadGroupReference> topLevelThreadGroups() {
            return delegate().topLevelThreadGroups();
        }

        @Override
        public EventQueue eventQueue() {
            return delegate().eventQueue();
        }

        @Override
        public EventRequestManager eventRequestManager() {
            return delegate().eventRequestManager();
        }

        @Override
        public BooleanValue mirrorOf(boolean arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public ByteValue mirrorOf(byte arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public CharValue mirrorOf(char arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public ShortValue mirrorOf(short arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public IntegerValue mirrorOf(int arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public LongValue mirrorOf(long arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public FloatValue mirrorOf(float arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public DoubleValue mirrorOf(double arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public StringReference mirrorOf(String arg0) {
            return delegate().mirrorOf(arg0);
        }

        @Override
        public VoidValue mirrorOfVoid() {
            return delegate().mirrorOfVoid();
        }

        @Override
        public Process process() {
            return new Process() {
                @Override
                public OutputStream getOutputStream() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }

                @Override
                public InputStream getInputStream() {
                    return out;
                }

                @Override
                public InputStream getErrorStream() {
                    return err;
                }

                @Override
                public int waitFor() throws InterruptedException {
                    synchronized (LaunchingVirtualMachine.this) {
                        while (!finished) {
                            LaunchingVirtualMachine.this.wait();
                        }
                        return exitCode;
                    }
                }

                @Override
                public int exitValue() {
                    synchronized (LaunchingVirtualMachine.this) {
                        while (!finished) {
                            throw new IllegalThreadStateException();
                        }
                        return exitCode;
                    }
                }

                @Override
                public void destroy() {
                    //ignore
                }
            };
        }

        @Override
        public void dispose() {
            delegate().dispose();
        }

        @Override
        public void exit(int arg0) {
            delegate().exit(arg0);
        }

        @Override
        public boolean canWatchFieldModification() {
            return delegate().canWatchFieldModification();
        }

        @Override
        public boolean canWatchFieldAccess() {
            return delegate().canWatchFieldAccess();
        }

        @Override
        public boolean canGetBytecodes() {
            return delegate().canGetBytecodes();
        }

        @Override
        public boolean canGetSyntheticAttribute() {
            return delegate().canGetSyntheticAttribute();
        }

        @Override
        public boolean canGetOwnedMonitorInfo() {
            return delegate().canGetOwnedMonitorInfo();
        }

        @Override
        public boolean canGetCurrentContendedMonitor() {
            return delegate().canGetCurrentContendedMonitor();
        }

        @Override
        public boolean canGetMonitorInfo() {
            return delegate().canGetMonitorInfo();
        }

        @Override
        public boolean canUseInstanceFilters() {
            return delegate().canUseInstanceFilters();
        }

        @Override
        public boolean canRedefineClasses() {
            return delegate().canRedefineClasses();
        }

        @Override
        public boolean canAddMethod() {
            return delegate().canAddMethod();
        }

        @Override
        public boolean canUnrestrictedlyRedefineClasses() {
            return delegate().canUnrestrictedlyRedefineClasses();
        }

        @Override
        public boolean canPopFrames() {
            return delegate().canPopFrames();
        }

        @Override
        public boolean canGetSourceDebugExtension() {
            return delegate().canGetSourceDebugExtension();
        }

        @Override
        public boolean canRequestVMDeathEvent() {
            return delegate().canRequestVMDeathEvent();
        }

        @Override
        public boolean canGetMethodReturnValues() {
            return delegate().canGetMethodReturnValues();
        }

        @Override
        public boolean canGetInstanceInfo() {
            return delegate().canGetInstanceInfo();
        }

        @Override
        public boolean canUseSourceNameFilters() {
            return delegate().canUseSourceNameFilters();
        }

        @Override
        public boolean canForceEarlyReturn() {
            return delegate().canForceEarlyReturn();
        }

        @Override
        public boolean canBeModified() {
            return delegate().canBeModified();
        }

        @Override
        public boolean canRequestMonitorEvents() {
            return delegate().canRequestMonitorEvents();
        }

        @Override
        public boolean canGetMonitorFrameInfo() {
            return delegate().canGetMonitorFrameInfo();
        }

        @Override
        public boolean canGetClassFileVersion() {
            return delegate().canGetClassFileVersion();
        }

        @Override
        public boolean canGetConstantPool() {
            return delegate().canGetConstantPool();
        }

//        @Override XXX
//        public boolean canGetModuleInfo() {
//            return delegate().canGetModuleInfo();
//        }

        @Override
        public void setDefaultStratum(String arg0) {
            delegate().setDefaultStratum(arg0);
        }

        @Override
        public String getDefaultStratum() {
            return delegate().getDefaultStratum();
        }

        @Override
        public long[] instanceCounts(List<? extends ReferenceType> arg0) {
            return delegate().instanceCounts(arg0);
        }

        @Override
        public String description() {
            return delegate().description();
        }

        @Override
        public String version() {
            return "whatever?";
        }

        @Override
        public String name() {
            return delegate().name();
        }

        @Override
        public void setDebugTraceMode(int arg0) {
            delegate().setDebugTraceMode(arg0);
        }

        @Override
        public VirtualMachine virtualMachine() {
            return this;
        }

    }

    static class SourceProvider implements ISourceLookUpProvider {

        private ClassPath sources = ClassPath.EMPTY;

        public SourceProvider() {
        }

        @Override
        public boolean supportsRealtimeBreakpointVerification() {
            return false;
        }

        @Override
        public String[] getFullyQualifiedName(String uri, int[] lines, int[] dummy) throws DebugException {
            List<String> result = new ArrayList<>();
            try {
                FileObject file = URLMapper.findFileObject(new URL(uri));
                if (file != null) {
                    JavaSource.forFileObject(file).runUserActionTask(new Task<CompilationController>() {
                        @Override
                        public void run(CompilationController parameter) throws Exception {
                            parameter.toPhase(JavaSource.Phase.ELEMENTS_RESOLVED); //XXX
                            for (int line : lines) {
                                int offsets = (int) parameter.getCompilationUnit().getLineMap().getStartPosition(line);
                                TreePath path = parameter.getTreeUtilities().pathFor(offsets);
                                while (path != null) {
                                    if (TreeUtilities.CLASS_TREE_KINDS.contains(path.getLeaf().getKind())) {
                                        result.add(parameter.getElements().getBinaryName((TypeElement) parameter.getTrees().getElement(path)).toString());
                                        break;
                                    }
                                    path = path.getParentPath();
                                }
                            }
                        }
                    }, true);
                }
            } catch (IOException | IllegalArgumentException ex) {
                throw new DebugException(ex);
            }
            System.err.println("result=" + result);
            return result.toArray(new String[0]);
        }

        @Override
        public String getSourceFileURI(String fqn, String fileName) {
            FileObject source = sources.findResource(fileName);

            if (source != null) {
                return source.toURI().toString();
            }

            return null;
        }

        @Override
        public String getSourceContents(String arg0) {
            System.err.println("arg0=" + arg0);
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static final class IOProviderImpl extends IOProvider {

        private final OutputStream out;
        private final OutputStream err;

        public IOProviderImpl(OutputStream out, OutputStream err) {
            this.out = out;
            this.err = err;
        }

        @Override
        public synchronized InputOutput getIO(String name, boolean newIO) {
            return new IO(new OutputWriterImpl(this, OutputWriterImpl.Kind.OUT),
                          new OutputWriterImpl(this, OutputWriterImpl.Kind.ERR));
        }

        @Override
        public OutputWriter getStdOut() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

    private static final class IO implements InputOutput {

        private final OutputWriter out;
        private final OutputWriter err;

        public IO(OutputWriter out, OutputWriter err) {
            this.out = out;
            this.err = err;
        }

        @Override
        public OutputWriter getOut() {
            return out;
        }

        @Override
        public Reader getIn() {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    return -1;
                }

                @Override
                public void close() throws IOException {
                }
            };
        }

        @Override
        public OutputWriter getErr() {
            return err;
        }

        @Override
        public void closeInputOutput() {
        }

        @Override
        public boolean isClosed() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setOutputVisible(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setErrVisible(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setInputVisible(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void select() {
        }

        @Override
        public boolean isErrSeparated() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setErrSeparated(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isFocusTaken() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setFocusTaken(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Reader flushReader() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

    private static class OutputWriterImpl extends OutputWriter {

        private final Kind kind;

        public OutputWriterImpl(IOProviderImpl io, Kind kind) {
            super(createWriter(io, kind));
            this.kind = kind;
        }

        private static Writer createWriter(IOProviderImpl io, Kind kind) {
            return new OutputStreamWriter(kind == Kind.OUT ? io.out : io.err);
        }

        @Override
        public void println(String s, OutputListener l) throws IOException {
            out.write(s);
            out.write("\n");
            out.flush();
        }

        @Override
        public void write(String s, int off, int len) {
            super.write(s, off, len);
            flush();
        }

        @Override
        public void write(char[] buf, int off, int len) {
            super.write(buf, off, len);
            flush();
        }

        @Override
        public void write(int c) {
            super.write(c);
            flush();
        }

        @Override
        public void reset() throws IOException {
            IOProvider io = Lookup.getDefault().lookup(IOProvider.class);
            if (io instanceof IOProviderImpl) {
                out = createWriter((IOProviderImpl) io, kind);
            }
        }

        @Override
        public void close() {
        }

        public enum Kind {
            OUT, ERR;
        }
    }

    private static Pair<InputStream, OutputStream> createCopyingStreams() {
        List<Integer> data = new LinkedList<>();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                synchronized (data) {
                    try {
                        while (data.isEmpty() && !closed.get()) {
                            data.wait();
                        }
                    } catch (InterruptedException ex) {
                        //ignore
                    }
                    if (data.isEmpty() && closed.get()) {
                        return -1;
                    }
                    return data.remove(0);
                }
            }
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int l = 0;
                while (l < len) {
                    int r = read();
                    if (r == -1) {
                        if (l == 0) return -1;
                        break;
                    }
                    b[off + l] = (byte) r;
                    l++;
                    if (r == '\n') break;
                }
                return l;
            }
        };
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                synchronized (data) {
                    data.add(b);
                    data.notifyAll();
                }
            }

            @Override
            public void close() throws IOException {
                synchronized (data) {
                    closed.set(true);
                    data.notifyAll();
                }
            }
        };
        return Pair.of(in, out);
    }
}
