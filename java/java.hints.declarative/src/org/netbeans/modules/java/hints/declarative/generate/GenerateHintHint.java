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
package org.netbeans.modules.java.hints.declarative.generate;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.modules.java.hints.declarative.debugging.ToggleDebuggingAction;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.CursorMovedSchedulerEvent;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser.Result;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.*;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author lahvac
 */
public class GenerateHintHint extends ParserResultTask<Result> {

    private static final Logger LOG = Logger.getLogger(GenerateHintHint.class.getName());

    @Override
    public void run(Result result, SchedulerEvent event) {
        try {
            runHint(result, event);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    private void runHint(Result result, SchedulerEvent event) throws BadLocationException {
        CursorMovedSchedulerEvent evt = (CursorMovedSchedulerEvent) event;
        //TODO: evt can be null???
        FileObject source = result.getSnapshot().getSource().getFileObject();
        ClassPath sourceCP = ClassPath.getClassPath(source, ClassPath.SOURCE);
        if (sourceCP == null)
            sourceCP = ClassPath.EMPTY;
        FileObject ownerRoot = sourceCP.findOwnerRoot(source);
        if (ownerRoot == null) {
            return ;
        }
        String pattern = generatePattern(result.getSnapshot().getSource(), evt.getMarkOffset(), evt.getCaretOffset());
        if (pattern == null) {
            return ;
        }
        FileObject upgrade = sourceCP.findResource("META-INF/upgrade");
        List<String> hintFiles = new ArrayList<>();
        if (upgrade != null) {
            for (FileObject c : upgrade.getChildren()) {
                if ("hint".equals(c.getExt())) {
                    hintFiles.add(c.getName());
                }
            }
        }
        //TODO EnhancedFix for ordering:
        Fix f = new Fix() {
            @Override
            public String getText() {
                return "Create Upgrade Script...";
            }

            @Override
            public ChangeInfo implement() throws Exception {
                Mutex.EVENT.readAccess(() -> {
                    ScriptTarget targetPanel = new ScriptTarget(hintFiles);
                    DialogDescriptor dd = new DialogDescriptor(targetPanel, "Create Upgrade Script");
                    if (DialogDisplayer.getDefault().notify(dd) == DialogDescriptor.OK_OPTION) {
                        try {
                            TopComponent original = WindowManager.getDefault().getRegistry().getActivated();
                            FileObject hintFile = ownerRoot.getFileObject("META-INF/upgrade/" + targetPanel.getFileName() + ".hint", false);
                            if (!hintFile.isValid()) {
                                List<FileObject> parents = new ArrayList<>();
                                FileObject parent = hintFile.getParent();
                                while (!parent.isValid()/*!FileUtil.toFile(parent).isDirectory()*/) {
                                    parents.add(parent);
                                    parent = parent.getParent();
                                }
                                for (int i = parents.size() - 1; i >=0 ; i--) {
                                    parent = parent.createFolder(parents.get(i).getNameExt());
                                }
                                hintFile = parent.createData(hintFile.getNameExt());
                            }
                            DataObject hintDO = DataObject.find(hintFile);
                            EditorCookie ec = hintDO.getLookup().lookup(EditorCookie.class);
                            StyledDocument hintDoc = ec.openDocument();
                            NbDocument.runAtomic(hintDoc, () -> {
                                try {
                                    if (hintDoc.getLength() > 0) {
                                        hintDoc.insertString(hintDoc.getLength(), "\n", null);
                                    }
                                    hintDoc.insertString(hintDoc.getLength(), pattern, null);
                                } catch (BadLocationException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            });
                            ec.open();
                            SwingUtilities.invokeLater(() -> {
                                TopComponent hintTC = WindowManager.getDefault().getRegistry().getActivated();
                                Mode originalMode = WindowManager.getDefault().findMode(original);
                                Mode alternativeMode = null;
                                WindowManager manager = WindowManager.getDefault();
                                for (Mode m : manager.getModes()) {
                                    if (manager.isEditorMode(m) && m != originalMode) {
                                        alternativeMode = m;
                                        break;
                                    }
                                }
                                if (alternativeMode == null) {
                                    alternativeMode = 
                                    WindowManager.getDefault().createModeFromXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                                 "\n" +
                                                                                 "<mode version=\"2.4\">\n" +
                                                                                 "  <kind type=\"editor\" />\n" +
                                                                                 "  <state type=\"joined\"  />\n" +
                                                                                 "  <constraints>\n" +
                                                                                 "    <path orientation=\"horizontal\" number=\"1\" weight=\"0.5\"/>\n" +
                                                                                 "  </constraints>\n" +
                                                                                 "  <bounds x=\"0\" y=\"0\" width=\"0\" height=\"0\" />\n" +
                                                                                 "  <frame state=\"0\"/>\n" +
                                                                                 "  <empty-behavior permanent=\"false\"/>\n" +
                                                                                 "</mode>");
                                }
                                if (manager.findMode(hintTC) != alternativeMode) {
                                    alternativeMode.dockInto(hintTC);
                                }
                                hintTC.requestActive();
                                ToggleDebuggingAction.enableDebugging(ec.getDocument());
                            });
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                });
                return null;
            }
        };
        ErrorDescription err = ErrorDescriptionFactory.createErrorDescription(Severity.HINT, "", Collections.singletonList(f), result.getSnapshot().getSource().getFileObject(), evt.getCaretOffset(), evt.getCaretOffset());
        HintsController.setErrors(result.getSnapshot().getSource().getFileObject(), GenerateHintHint.class.getName(), Collections.singletonList(err));
    }

    private static final Set<ElementKind> LOCAL_VARIABLES = new HashSet<>(Arrays.asList(ElementKind.EXCEPTION_PARAMETER, ElementKind.LOCAL_VARIABLE, ElementKind.PARAMETER, ElementKind.RESOURCE_VARIABLE)); //TODO: binding variable

    static String generatePattern(Source source, int caretOffset, int markOffset) {
        try {
            List<String> conditions = new ArrayList<>();
            ModificationResult mr = ModificationResult.runModificationTask(Collections.singletonList(source),
                    new UserTask() {
                        @Override
                        public void run(ResultIterator resultIterator) throws Exception {
                            WorkingCopy copy = WorkingCopy.get(resultIterator.getParserResult());
                            if (copy == null) {
                                LOG.severe("No WorkingCopy!");
                                return ;
                            }
                            copy.toPhase(Phase.RESOLVED);
                            int caret = Math.max(caretOffset, markOffset);
                            int mark = Math.min(caretOffset, markOffset);
                            //TODO: strip whitespaces/comments/etc. from caret and mark
                            int avg = (caret + mark) / 2;
                            List<TreePath> foundParts = new ArrayList<>();
                            TreePath tp = copy.getTreeUtilities().pathFor(avg);
                            //TODO: multiple statements:
                            OUTER: while (tp != null) {
                                Tree leaf = tp.getLeaf();
                                long start = copy.getTrees().getSourcePositions().getStartPosition(tp.getCompilationUnit(), leaf);
                                long end   = copy.getTrees().getSourcePositions().getEndPosition(tp.getCompilationUnit(), leaf);
                                if (start == mark && end == caret) {
                                    copy.tag(tp.getLeaf(), "snippet");
                                    copy.tag(tp.getLeaf(), "snippet");
                                    foundParts.add(tp);
                                    break;
                                }
                                if (leaf.getKind() == Kind.BLOCK) {//TODO: case
                                    if (start < mark && caret < end) {
                                        //look if there consequetive statements that would start and end on the selection:
                                        BlockTree block = (BlockTree) leaf;
                                        boolean firstFound = false;
                                        for (StatementTree st : block.getStatements()) {
                                            long statementStart = copy.getTrees().getSourcePositions().getStartPosition(tp.getCompilationUnit(), st);
                                            long statementEnd   = copy.getTrees().getSourcePositions().getEndPosition(tp.getCompilationUnit(), st);
                                            if (statementStart == mark) {
                                                copy.tag(st, "snippet-start");
                                                firstFound = true;
                                            }
                                            if (firstFound) {
                                                foundParts.add(new TreePath(tp, st));
                                            }
                                            if (statementEnd == caret && firstFound) {
                                                copy.tag(st, "snippet-end");
                                                break OUTER;
                                            }
                                        }
                                        foundParts.clear();
                                        break;
                                    }
                                }
                                tp = tp.getParentPath();
                            }
                            if (foundParts.isEmpty()) {
                                return ;
                            }
                            TreeMaker make = copy.getTreeMaker();
                            Map<Element, String> var2Name = new HashMap<>();
                            Map<String, Integer> usedNames2Index = new HashMap<>();
                            new TreePathScanner<Void, Void>() {
                                Set<Element> declared = new HashSet<>();
                                @Override
                                public Void visitVariable(VariableTree node, Void p) {
                                    declared.add(copy.getTrees().getElement(getCurrentPath()));
                                    return super.visitVariable(node, p);
                                }
                                @Override
                                public Void visitIdentifier(IdentifierTree node, Void p) {
                                    Element el = copy.getTrees().getElement(getCurrentPath());
                                    if (el != null) {
                                        if (LOCAL_VARIABLES.contains(el.getKind())) {
                                            String name = var2Name.computeIfAbsent(el, var -> {
                                                String desiredName = el.getSimpleName().toString();
                                                Integer i = usedNames2Index.get(desiredName);
                                                String result;
                                                if (i == null) {
                                                    usedNames2Index.put(desiredName, 1);
                                                    result = "$" + desiredName;
                                                } else {
                                                    usedNames2Index.put(desiredName, i + 1);
                                                    result = "$" + desiredName + i;
                                                }
                                                conditions.add(result + " instanceof " + el.asType().toString());
                                                return result;
                                            });
                                            copy.rewrite(node, make.Identifier(name));
                                        } else if (el.getKind().isClass() || el.getKind().isInterface()) {
                                            copy.rewrite(node, make.Identifier(((TypeElement) el).getQualifiedName()));
                                        }
                                    }
                                    return super.visitIdentifier(node, p);
                                }
                            }.scan(tp, null);
                            if (copy.resolveRewriteTarget(tp.getLeaf()) == tp.getLeaf())
                                copy.rewrite(tp.getLeaf(), tp.getLeaf());
                        }
                    });
            int[] spanStart;
            int[] spanEnd;
            if (mr.getSpan("snippet") != null) {
                spanStart = spanEnd = mr.getSpan("snippet");
            } else {
                spanStart = mr.getSpan("snippet-start");
                spanEnd = mr.getSpan("snippet-end");
            }
            if (spanStart != null && spanEnd != null) {
                String content = mr.getResultingSource(source.getFileObject());
                StringBuilder result = new StringBuilder();
                String pattern = content.substring(spanStart[0], spanEnd[1]).trim();

                result.append(pattern);

                if (!conditions.isEmpty()) {
                    String sep = " :: ";
                    for (String condition : conditions) {
                        result.append(sep);
                        result.append(condition);
                        sep = " && ";
                    }
                }

                result.append("\n=>\n");
                result.append(pattern);
                result.append("\n;;\n");

                return result.toString();
            }
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 10000;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.CURSOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {
    }
    
    @MimeRegistration(mimeType="text/x-java", service=TaskFactory.class)
    public static final class FactoryImpl extends TaskFactory {

        @Override
        public Collection<? extends SchedulerTask> create(Snapshot snapshot) {
            return Collections.unmodifiableList(Arrays.asList(new GenerateHintHint()));
        }
        
    }
}
