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

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.api.java.source.support.ErrorAwareTreePathScanner;

/**
 *
 * @author lahvac
 */
public class ASTDumper {
    public static final String NOOP = "<noop>";
    public static final String UNKNOWN = "<unknown>";
    private static final int MAX_ELEMS = 10;

    public static List<String> dumpAST(CompilationInfo info, TreePath path) {
        if (path.getLeaf().getKind() != Kind.MEMBER_SELECT) {
        System.err.println("leaf=" + path.getLeaf());
        }
        TreePath methodBlock = path;
        while (methodBlock.getParentPath() != null && (methodBlock.getLeaf().getKind() != Tree.Kind.BLOCK || (methodBlock.getParentPath().getLeaf().getKind() != Kind.METHOD && !TreeUtilities.CLASS_TREE_KINDS.contains(methodBlock.getParentPath().getLeaf().getKind())))) {
            methodBlock = methodBlock.getParentPath();
        }
        List<String> record = new ArrayList<>();
        class Scanner extends ErrorAwareTreePathScanner<Void, Void> {
            private boolean doRecord = true;
            @Override
            public Void scan(Tree tree, Void p) {
                if (tree != null) {
                    record(tree.getKind().name());
                } else {
                    record(NOOP);
                }
                return super.scan(tree, p);
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void p) {
                record(node.getName().toString());
                if (getCurrentPath().getParentPath().getLeaf().getKind() != Tree.Kind.METHOD_INVOCATION || getCurrentPath().getParentPath().getLeaf() != path.getLeaf()) { //TODO: document
                    record(String.valueOf(info.getTrees().getTypeMirror(getCurrentPath())));
                }
                return super.visitIdentifier(node, p);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void p) {
                scan(node.getExpression(), p);
                if (node == path.getLeaf()) {
                    doRecord = false;
                    TypeMirror type = info.getTrees().getTypeMirror(new TreePath(getCurrentPath(), node.getExpression()));
                    if (type != null && /*TODO:*/ type.getKind() == TypeKind.DECLARED) {
                        record.remove(record.size() - 1);
                        Collections.reverse(record);
                        while (record.size() > MAX_ELEMS) {
                            record.remove(record.size() - 1);
                        }
                        while (record.size() < MAX_ELEMS) {
                            record.add(NOOP);
                        }
                    }
                }
                record(node.getIdentifier().toString());
                if (getCurrentPath().getParentPath().getLeaf().getKind() != Tree.Kind.METHOD_INVOCATION || getCurrentPath().getParentPath().getLeaf() != path.getLeaf()) { //TODO: document
                    record(String.valueOf(info.getTrees().getTypeMirror(getCurrentPath())));
                }
                return null;
            }

            private void record(String s) {
                if (doRecord)
                    record.add(s);
            }
        };

        Scanner scanner = new Scanner();
        scanner.scan(methodBlock, null);

        return scanner.doRecord ? null : record;
    }

    public static List<Integer> convert(List<String> record, Map<String, Integer> vocabulary, boolean append) {
        return record.stream().map(r -> vocabulary.computeIfAbsent(r, rx -> append ? vocabulary.size() : vocabulary.get(UNKNOWN))).collect(Collectors.toList());
    }

}
