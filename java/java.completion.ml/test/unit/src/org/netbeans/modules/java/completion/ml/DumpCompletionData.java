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
package org.netbeans.modules.java.completion.ml;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import junit.framework.Test;
import org.netbeans.api.java.queries.UnitTestForSourceQuery;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.project.*;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.java.completion.BaseTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

public final class DumpCompletionData extends NbTestCase {

    public DumpCompletionData(String name) {
        super(name);
    }

    public void doDump() throws InterruptedException {
        List<FileObject> projects = Arrays.asList(FileUtil.toFileObject(new File("/home/lahvac/src/nb/apache-netbeans/platform/openide.util.lookup")));
        OpenProjects.getDefault().open(projects.stream().map(root -> FileOwnerQuery.getOwner(root)).filter(p -> p != null).toArray(s -> new Project[s]), false);
        SourceUtils.waitScanFinished();
        List<FileObject> roots = new ArrayList<>();

        for (Project prj : OpenProjects.getDefault().getOpenProjects()) {
            for (SourceGroup sg : ProjectUtils.getSources(prj).getSourceGroups("java")) {
                URL[] sources = UnitTestForSourceQuery.findSources(sg.getRootFolder());
                if (sources == null || sources.length == 0) {
                    roots.add(sg.getRootFolder());
                }
            }
        }
        
        File targetDir = new File("/home/lahvac/src/nb/learning/new-test");
        Map<String, Integer> word2Idx = new LinkedHashMap<>();
        Function<String, Integer> getIdx = word -> word2Idx.computeIfAbsent(word, w -> word2Idx.size());
        Map<String, Map<String, Integer>> type2Word2Idx = new HashMap<>();
        getIdx.apply(ASTDumper.NOOP);
        getIdx.apply(ASTDumper.UNKNOWN);
        List<Data> data = new ArrayList<>();
        System.err.println("total roots:" + roots.size());
        int idx = 0;
        for (FileObject root : roots) {
            System.err.println("root number: " + ++idx);
            boolean training = true;
            List<FileObject> files = new ArrayList<>();
            Enumeration<? extends FileObject> en = root.getChildren(true);

            while (en.hasMoreElements()) {
                FileObject file = en.nextElement();
                if (file.isData() && "text/x-java".equals(file.getMIMEType())) {
                    files.add(file);
                }
            }
            
            if (files.isEmpty()) {
                continue;
            }

            try {
                JavaSource.create(ClasspathInfo.create(root), files)
                          .runUserActionTask(cc -> {
                              try {
                              cc.toPhase(Phase.RESOLVED); //TODO: check result!
                              new TreePathScanner<Void, Void>() {
                                  @Override
                                  public Void visitMemberSelect(MemberSelectTree node, Void p) {
                                      scan(node.getExpression(), p);
                                      Element actual = cc.getTrees().getElement(getCurrentPath());
                                      String[] sig = actual != null ? SourceUtils.getJVMSignature(ElementHandle.create(actual)) : new String[0];
                                      if (sig.length == 3) {
                                          String fqn = sig[0];
                                          String actualEn = sig[1] + ":" + sig[2];
                                          Integer outcome;
                                          if (training) {
                                              outcome = type2Word2Idx.computeIfAbsent(fqn, t -> new LinkedHashMap<>()).computeIfAbsent(actualEn, w -> type2Word2Idx.get(fqn).size());
                                          } else {
                                              outcome = type2Word2Idx.getOrDefault(fqn, Collections.emptyMap()).get(actualEn);
                                          }
                                          if (outcome != null) {
                                              if (sig.length == 3) {
                                                  TreePath path;
                                                  try {
                                                      int[] span = cc.getTreeUtilities().findNameSpan(node);
                                                      if (span == null) {
                                                          System.err.println("no span: " + node);
                                                          return null;
                                                      }
                                                      path = new DumpingTask(span[0]).getCompletionPath(cc);
                                                      if (path.getLeaf().getKind() != Kind.MEMBER_SELECT) {
                                                          System.err.println("Ignoring member select: " + node);
                                                          return null;
                                                      }
                                                  } catch (IOException ex) {
                                                      Exceptions.printStackTrace(ex);
                                                      return null;
                                                  }
                                                  TypeMirror type = cc.getTrees().getTypeMirror(new TreePath(path, ((MemberSelectTree) path.getLeaf()).getExpression()));
                                                  type = type != null && type.getKind() == TypeKind.ERROR ? cc.getTrees().getOriginalType((ErrorType) type) : type;
                                                  if (type != null && /*TODO:*/type.getKind() == TypeKind.DECLARED) { //TODO: check type is fqn
                                                      long pos = cc.getTrees().getSourcePositions().getEndPosition(cc.getCompilationUnit(), node.getExpression());
                                                      long line = cc.getCompilationUnit().getLineMap().getLineNumber(pos);
                                                      long col = cc.getCompilationUnit().getLineMap().getColumnNumber(pos);
                                                      String location = cc.getFileObject().getPath() + ":" + line + ":" + col;
                                                      List<String> dump = ASTDumper.dumpAST(cc, path);
                                                      if (dump != null) {
                                                          data.add(new Data(ASTDumper.convert(dump, word2Idx, true).stream().mapToInt(i -> i).toArray(), outcome, location));
                                                      } else {
                                                          System.err.println("cannot dump data for: " + location);
                                                      }
                                                  }
                                              }
                                          }
                                      }
                                      return null;
                                  }
                              }.scan(cc.getCompilationUnit(), null);
                              } catch (ThreadDeath td) {
                                      throw td;
                              } catch (Throwable t) {
                                  System.err.println("An exception while processing: " + cc.getFileObject());
                                  t.printStackTrace();
                              }
                          }, true);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        int maxOutputs = type2Word2Idx.values().stream().map(m -> m.size()).collect(Collectors.maxBy((i1, i2) -> i1 - i2)).get();
        try {
            dump(targetDir, "train", data, word2Idx, type2Word2Idx, maxOutputs);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        System.err.println("words: " + word2Idx.size());
    }

    private static class DumpingTask extends BaseTask {

        public DumpingTask(int caretOffset) {
            super(caretOffset, () -> false);
        }

        @Override
        protected void resolve(CompilationController controller) throws IOException {
            throw new UnsupportedOperationException("Do not call.");
        }

        public TreePath getCompletionPath(CompilationController controller) throws IOException {
            return super.getCompletionEnvironment(controller, true).getPath();
        }
        
    }
    private void dump(File targetDir, String prefix, List<Data> data, Map<String, Integer> word2Idx, Map<String, Map<String, Integer>> type2Word2Idx, int totalOutcomes) throws IOException {
        try (OutputStream osData = new FileOutputStream(new File(targetDir, prefix + "_data.txt"));
             Writer outData = new OutputStreamWriter(osData);
             OutputStream osLabels = new FileOutputStream(new File(targetDir, prefix + "_labels.txt"));
             Writer outLabels = new OutputStreamWriter(osLabels);
             OutputStream posOutStream = new FileOutputStream(new File(targetDir, prefix + "_pos.txt"));
             Writer posOut = new OutputStreamWriter(posOutStream)) {
            for (Data input : data) {
                String sep2 = "";
                for (int i : input.context) {
                    outData.write(sep2);
                    outData.write("" + i);
                    sep2 = " ";
                }
                outData.write("\n");
                sep2 = "";
                for (int i = 0; i < totalOutcomes; i++) {
                    outLabels.write(sep2);
                    if (input.outcome == i) {
                        outLabels.write("1");
                    } else {
                        outLabels.write("0");
                    }
                    sep2 = " ";
                }
                outLabels.write("\n");
                posOut.write(input.position);
                posOut.write("\n");
            }
        }
        try (OutputStream osVocabulary = new FileOutputStream(new File(targetDir, prefix + "_vocabulary.txt"));
             Writer outVocabulary = new OutputStreamWriter(osVocabulary)) {
            for (String k : word2Idx.keySet()) {
                outVocabulary.append(k);
                outVocabulary.append("\n");
            }
        }
        try (OutputStream osTypeOutcomes = new FileOutputStream(new File(targetDir, prefix + "_outcomes.txt"));
             Writer outTypeOutcomes = new OutputStreamWriter(osTypeOutcomes)) {
            for (Entry<String, Map<String, Integer>> typeOutcomes : type2Word2Idx.entrySet()) {
                outTypeOutcomes.append(typeOutcomes.getKey());
                outTypeOutcomes.append("=");
                String sep = "";
                for (String k : typeOutcomes.getValue().keySet()) {
                    outTypeOutcomes.append(sep);
                    outTypeOutcomes.append(k);
                    sep = ", ";
                }
                outTypeOutcomes.append("\n");
            }
        }
    }

    public static final class Data {
        public final int[] context;
        public final int outcome;
        public final String position;

        public Data(int[] context, int outcome, String position) {
            this.context = context;
            this.outcome = outcome;
            this.position = position;
        }

        
    }
    
    public static Test suite() {
        return NbModuleSuite.emptyConfiguration().addTest(DumpCompletionData.class, "doDump").clusters(".*").enableModules(".*").honorAutoloadEager(true).suite();
    }
}
