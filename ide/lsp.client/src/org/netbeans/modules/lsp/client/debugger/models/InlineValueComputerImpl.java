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
package org.netbeans.modules.lsp.client.debugger.models;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InlineValue;
import org.eclipse.lsp4j.InlineValueContext;
import org.eclipse.lsp4j.InlineValueEvaluatableExpression;
import org.eclipse.lsp4j.InlineValueParams;
import org.eclipse.lsp4j.InlineValueRegistrationOptions;
import org.eclipse.lsp4j.InlineValueText;
import org.eclipse.lsp4j.InlineValueVariableLookup;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.netbeans.api.debugger.DebuggerManagerAdapter;
import org.netbeans.api.debugger.LazyDebuggerManagerListener;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.Utils;
import org.netbeans.modules.lsp.client.debugger.DAPDebugger;
import org.netbeans.modules.lsp.client.debugger.DAPFrame;
import org.netbeans.modules.lsp.client.debugger.DAPThread;
import org.netbeans.modules.lsp.client.debugger.DAPVariable;
import org.netbeans.spi.debugger.DebuggerServiceRegistration;
import org.netbeans.spi.debugger.ui.Constants;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;


public class InlineValueComputerImpl implements PreferenceChangeListener, ChangeListener, PropertyChangeListener {

    private static final Logger LOG = Logger.getLogger(InlineValueComputerImpl.class.getName());
    private static final RequestProcessor EVALUATOR = new RequestProcessor(InlineValueComputerImpl.class.getName(), 1, false, false);
    private final DAPDebugger debugger;
    private final Preferences prefs;
    private TaskDescription currentTask;
    private DAPThread currentThread;
    private DAPFrame currentFrame;

    private InlineValueComputerImpl(Session session) {
        debugger = session.lookupFirst(null, DAPDebugger.class);
        debugger.addChangeListener(this);
//        prefs = MimeLookup.getLookup("text/x-java").lookup(Preferences.class);
        prefs = MimeLookup.getLookup("").lookup(Preferences.class);
        prefs.addPreferenceChangeListener(WeakListeners.create(PreferenceChangeListener.class, this, prefs));
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (debugger.getTerminated().isDone()) {
            setNewTask(null);
            return ;
        }

        DAPThread newThread = debugger.getCurrentThread();

        if (currentThread != newThread) {
            if (currentThread != null) {
                currentThread.removePropertyChangeListener(this);
            }
            if (newThread != null) {
                newThread.addPropertyChangeListener(this);
            }
            currentThread = newThread;
            propertyChange(null);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        DAPFrame newFrame = debugger.getCurrentFrame();

        if (currentFrame != newFrame) {
            currentFrame = newFrame;
            refreshVariables();
        }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        refreshVariables();
    }

    private void refreshVariables() {
        DAPFrame frame = debugger.getCurrentFrame();

        FileObject frameFile = null;
        Document frameDocument = null;
        int frameOffset = -1;
        int frameId = -1;
        LSPBindings bindings = null;

        if (prefs.getBoolean(Constants.KEY_INLINE_VALUES, Constants.DEF_INLINE_VALUES) &&
            frame != null &&
            frame.getThread().isSuspended()) {
            try {
                URI sourceURI = frame.getSourceURI();
                frameFile = sourceURI != null ? URLMapper.findFileObject(sourceURI.toURL()) : null;
                if (frameFile != null) {
                    EditorCookie ec = frameFile.getLookup().lookup(EditorCookie.class);
                    frameDocument = ec != null ? ec.getDocument() : null;
                    frameId = frame.getId();
                    frameOffset = LineDocumentUtils.getLineStartFromIndex(LineDocumentUtils.asRequired(frameDocument, LineDocument.class), frame.getLine());
                    bindings = LSPBindings.getBindings(frameFile);
                }
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        TaskDescription newTask;

        if (frameFile != null && frameDocument != null && hasInlineValueProvider(bindings)) {
            newTask = new TaskDescription(frameFile, frameOffset, frameDocument);
        } else {
            newTask = null;
        }

        if (setNewTask(newTask)) {
            return;
        }

        if (newTask != null) {
            Document frameDocumentFin = frameDocument;
            Position pos;
            try {
                pos = Utils.createPosition(frameDocumentFin, frameOffset);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
                return ;
            }
            Range range = new Range(pos, pos);
            InlineValueParams params = new InlineValueParams(new TextDocumentIdentifier(Utils.toURI(frameFile)), range, new InlineValueContext(frameId, range));
            CompletableFuture<List<InlineValue>> computation = bindings.getTextDocumentService().inlineValue(params);

            newTask.addCancelCallback(() -> computation.cancel(true));

            computation.handleAsync((variables, exc) -> {
                if (exc != null) {
                    Exceptions.printStackTrace(exc);
                }
                if (variables != null) {
                    OffsetsBag runningBag = new OffsetsBag(newTask.frameDocument);
                    Map<String, CompletableFuture<DAPVariable>> expression2Value = new HashMap<>();
                    Map<Integer, Set<String>> line2Values = new HashMap<>();

                    for (Either3<InlineValueText, InlineValueVariableLookup, InlineValueEvaluatableExpression> var : variables) { //XXX: should be InlineValue!!!
                        try {
                            if (newTask.isCancelled()) {
                                return null;
                            }

                            int endPos;
                            String valueText;

                            if (var.isFirst()) {
                                InlineValueText valueAsText = var.getFirst();

                                valueText = valueAsText.getText();
                                endPos = Utils.getLineEnd(frameDocumentFin, valueAsText.getRange().getEnd().getLine());
                            } else {
                                String expression;
                                Range r;

                                if (var.isThird()) {
                                    InlineValueEvaluatableExpression valueAsExpr = var.getThird();

                                    expression = valueAsExpr.getExpression();
                                    r = valueAsExpr.getRange();
                                } else if (var.isSecond()) {
                                    InlineValueVariableLookup valueAsVar = var.getSecond();

                                    expression = valueAsVar.getVariableName();
                                    r = valueAsVar.getRange();
                                } else {
                                    continue;
                                }

                                CompletableFuture<DAPVariable> pending = expression2Value.computeIfAbsent(expression, expr -> debugger.evaluate(frame, expr));

                                newTask.addCancelCallback(() -> pending.cancel(true));

                                DAPVariable dapVar = pending.get();

                                valueText = dapVar != null ? dapVar.getValue() : null;

                                if (valueText == null) {
                                    continue;
                                }

                                valueText = expression + " = " + valueText;
                                endPos = Utils.getLineEnd(frameDocumentFin, r.getEnd().getLine());
                            }

                            line2Values.computeIfAbsent(endPos, __ -> new LinkedHashSet<>())
                                       .add(valueText);
                            String mergedValues = line2Values.get(endPos).stream().collect(Collectors.joining(", ", "  ", ""));
                            AttributeSet attrs = AttributesUtilities.createImmutable("virtual-text-prepend", mergedValues);

                            runningBag.addHighlight(endPos, endPos + 1, attrs);

                            setHighlights(newTask, runningBag);
                        } catch (ExecutionException | InterruptedException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
                return null;
            }, EVALUATOR);
        }
    }

    private static boolean hasInlineValueProvider(LSPBindings bindings) {
        InitializeResult result = bindings != null ? bindings.getInitResult() : null;
        ServerCapabilities capa = result != null ? result.getCapabilities() : null;
        Either<Boolean, InlineValueRegistrationOptions> inlineValueProvider = capa != null ? capa.getInlineValueProvider() : null;

        return Utils.isEnabled(inlineValueProvider);
    }

    private synchronized boolean setNewTask(TaskDescription newTask) {
        if (Objects.equals(currentTask, newTask)) {
            return true; //nothing changed, nothing to do
        }

        if (currentTask != null) {
            currentTask.cancel();
            getHighlightsBag(currentTask.frameDocument).clear();
        }

        currentTask = newTask;

        return false;
    }

    private synchronized void setHighlights(TaskDescription task, OffsetsBag highlights) {
        if (!task.isCancelled()) {
            getHighlightsBag(currentTask.frameDocument).setHighlights(highlights);
        }
    }

    @DebuggerServiceRegistration(types=LazyDebuggerManagerListener.class)
    public static final class Init extends DebuggerManagerAdapter {
        @Override
        public void sessionAdded(Session session) {
            new InlineValueComputerImpl(session);
        }
    }

    private static final class TaskDescription {
        public final FileObject frameFile;
        public final int frameOffset;
        public final Document frameDocument;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Runnable> cancelCallbacks =
                Collections.synchronizedList(new ArrayList<>());

        public TaskDescription(FileObject frameFile, int frameOffset, Document frameDocument) {
            this.frameFile = frameFile;
            this.frameOffset = frameOffset;
            this.frameDocument = frameDocument;
        }

        public void cancel() {
            cancelled.set(true);

            List<Runnable> callbacks;

            synchronized (cancelCallbacks) {
                callbacks = new ArrayList<>(cancelCallbacks);
            }

            for (Runnable r : callbacks) {
                r.run();
            }
        }

        public void addCancelCallback(Runnable r) {
            cancelCallbacks.add(r);

            if (cancelled.get()) {
                r.run();
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + Objects.hashCode(this.frameFile);
            hash = 53 * hash + this.frameOffset;
            hash = 53 * hash + System.identityHashCode(this.frameDocument);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TaskDescription other = (TaskDescription) obj;
            if (this.frameOffset != other.frameOffset) {
                return false;
            }
            if (!Objects.equals(this.frameFile, other.frameFile)) {
                return false;
            }
            return this.frameDocument == other.frameDocument;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

    }
    @MimeRegistration(mimeType="", service=HighlightsLayerFactory.class)
    public static HighlightsLayerFactory createHighlightsLayerFactory() {
        return new HighlightsLayerFactory() {
            @Override
            public HighlightsLayer[] createLayers(HighlightsLayerFactory.Context context) {
                return new HighlightsLayer[] {
                    HighlightsLayer.create(InlineValueComputerImpl.class.getName(), ZOrder.SYNTAX_RACK.forPosition(1400), false, getHighlightsBag(context.getDocument()))
                };
            }
        };
    }

    private static OffsetsBag getHighlightsBag(Document doc) {
        OffsetsBag bag = (OffsetsBag) doc.getProperty(InlineValueComputerImpl.class);
        if (bag == null) {
            doc.putProperty(InlineValueComputerImpl.class, bag = new OffsetsBag(doc, true));
        }
        return bag;
    }


    public record InlineVariable(int start, int end, int lineEnd, String expression) {}
}
