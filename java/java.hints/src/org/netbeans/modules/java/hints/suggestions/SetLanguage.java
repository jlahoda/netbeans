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
package org.netbeans.modules.java.hints.suggestions;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.VariableElement;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.TreePathHandle;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.TriggerTreeKind;

import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint.Kind;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.InputLine;
import org.openide.util.NbBundle.Messages;

/**
 */
@Hint(category="suggestions", displayName="#DN_SetLanguage", description="#DESC_SetLanguage", hintKind=Kind.ACTION)
@Messages({
    "DN_SetLanguage=Set @Language annotation",
    "DESC_SetLanguage=Set @Language annotation"
})
public class SetLanguage {

    @TriggerTreeKind(Tree.Kind.STRING_LITERAL)
    @Messages("ERR_SetLanguage=Language is not defined for {0}")
    public static ErrorDescription setLanguage(HintContext ctx) {
        Element targetElement = findLanguageFromTarget(ctx.getInfo(), ctx.getPath());
        if (targetElement == null) {
            return null;
        }
        for (AnnotationMirror am : ctx.getInfo().getElementUtilities().getAugmentedAnnotationMirrors(targetElement)) {
            if (am.getAnnotationType().asElement().getSimpleName().contentEquals("Language")) {
                return null;
            }
        }

        String description = describeElement(ctx.getInfo(), targetElement);
        return ErrorDescriptionFactory.forSpan(ctx, ctx.getCaretLocation(), ctx.getCaretLocation() + 1, Bundle.ERR_SetLanguage(description), new SetLanguageFix(TreePathHandle.create(ctx.getPath(), ctx.getInfo()), description));
    }

    @Messages({
        "DESC_Parameter=parameter {0} of {1}",
        "DESC_Component=component {0} of {1}"
    })
    private static String describeElement(CompilationInfo info, Element el) {
        if (el.getKind() == ElementKind.PARAMETER) {
            return Bundle.DESC_Parameter(el.getSimpleName(), describeElement(info, el.getEnclosingElement()));
        } else if (el.getKind() == ElementKind.RECORD_COMPONENT) {
            return Bundle.DESC_Component(el.getSimpleName(), describeElement(info, el.getEnclosingElement()));
        } else if (el.getKind().isClass() || el.getKind().isInterface()) {
            return ((QualifiedNameable) el).getQualifiedName().toString();
        } else if (el.getKind() == ElementKind.METHOD || el.getKind() == ElementKind.CONSTRUCTOR) {
            ExecutableElement ee = (ExecutableElement) el;
            String simpleName = el.getKind() == ElementKind.METHOD ? "." + el.getSimpleName() : "";
            return describeElement(info, el.getEnclosingElement()) + simpleName + ee.getParameters().stream().map(ve -> info.getTypeUtilities().getTypeName(ve.asType())).collect(Collectors.joining(", ", "(", ")"));
        } else if (el.getKind().isField()) {
            return describeElement(info, el.getEnclosingElement()) + "." + el.getSimpleName();
        } else {
            return el.getSimpleName().toString();
        }
    }

    //TODO: partly copied from java.editor.base/.../EmbeddingProviderImpl, can be unified?
    private static Element findLanguageFromTarget(CompilationInfo info, TreePath tp) {
        VariableElement target = null;
        switch (tp.getParentPath().getLeaf().getKind()) {
            case NEW_CLASS: {
                List<? extends ExpressionTree> arguments = ((NewClassTree) tp.getParentPath().getLeaf()).getArguments();
                target = executableParameter(info, tp, arguments);
                break;
            }
            case METHOD_INVOCATION: {
                List<? extends ExpressionTree> arguments = ((MethodInvocationTree) tp.getParentPath().getLeaf()).getArguments();
                target = executableParameter(info, tp, arguments);
                break;
            }

            case VARIABLE: {
                target = (VariableElement) info.getTrees().getElement(tp.getParentPath());
                break;
            }
        }
        return target;
    }

    private static VariableElement executableParameter(CompilationInfo info, TreePath paramPath, List<? extends ExpressionTree> arguments) {
        int argPos = arguments.indexOf(paramPath.getLeaf());
        if (argPos == (-1)) {
            return null;
        }
        Element el = info.getTrees().getElement(paramPath.getParentPath());
        if (el == null || (el.getKind() != ElementKind.METHOD && el.getKind() != ElementKind.CONSTRUCTOR)) {
            return null;
        }
        return ((ExecutableElement) el).getParameters().get(argPos);
    }

    private static final class SetLanguageFix implements Fix {

        private final TreePathHandle target;
        private final String targetDescription;

        public SetLanguageFix(TreePathHandle target, String targetDescription) {
            this.target = target;
            this.targetDescription = targetDescription;
        }

        @Override
        @Messages("FIX_SetLanguage=Add virtual @Language annotation to {0}")
        public String getText() {
            return Bundle.FIX_SetLanguage(targetDescription);
        }

        @Override
        public ChangeInfo implement() throws Exception {
            InputLine nd = new InputLine("Language:", "Select Language");
            Object result = DialogDisplayer.getDefault().notify(nd);
            if (Objects.equals(result, InputLine.OK_OPTION)) {
                String language = nd.getInputText();
                JavaSource.forFileObject(target.getFileObject())
                          .runUserActionTask(cc -> {
                              cc.toPhase(Phase.RESOLVED);
                              TreePath param = target.resolve(cc);
                              if (param == null) {
                                  //TODO: error!
                                  return ;
                              }
                              Element paramElem = findLanguageFromTarget(cc, param);
                              if (paramElem == null) {
                                  //TODO: error!
                                  return ;
                              }
                              SourceUtils.attachAnnotation(cc, paramElem, "@Language(\"" + language + "\")");
                          }, true);
            }
            return null;
        }

    }
}
