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

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.JavaFix;
import org.netbeans.spi.java.hints.TriggerTreeKind;

/**
 *
 * @author lahvac
 */
@Hint(displayName="Mark @Null or @NotNull", description="Mark @Null or @NotNull", category="suggestions", hintKind=Hint.Kind.ACTION)
public class MarkNullNotNull {

    @TriggerTreeKind(Kind.VARIABLE)
    public static ErrorDescription var(HintContext ctx) {
        Element el = ctx.getInfo().getTrees().getElement(ctx.getPath());

        if (el == null || (el.getKind() != ElementKind.FIELD && el.getKind() != ElementKind.PARAMETER)) return null;

        return hint(ctx, ctx.getInfo().getTreeUtilities().findNameSpan((VariableTree) ctx.getPath().getLeaf()), NullAllowed.class.getName());
    }

    @TriggerTreeKind(Kind.METHOD)
    public static ErrorDescription method(HintContext ctx) {
        Element el = ctx.getInfo().getTrees().getElement(ctx.getPath());

        if (el == null) return null;

        return hint(ctx, ctx.getInfo().getTreeUtilities().findNameSpan((MethodTree) ctx.getPath().getLeaf()), CheckForNull.class.getName());
    }

    private static ErrorDescription hint(HintContext ctx, int[] span, String nullAllowedAnnotations) {
        if (ctx.getCaretLocation() < span[0] || ctx.getCaretLocation() > span[1]) return null;

        return ErrorDescriptionFactory.forName(ctx, ctx.getPath(), "", new AddAnnotationFix(ctx.getInfo(), ctx.getPath(), NonNull.class.getName()).toEditorFix(), new AddAnnotationFix(ctx.getInfo(), ctx.getPath(), nullAllowedAnnotations).toEditorFix());
    }

    private static final class AddAnnotationFix extends JavaFix {

        private final String annotation;

        public AddAnnotationFix(CompilationInfo info, TreePath tp, String annotation) {
            super(info, tp);
            this.annotation = annotation;
        }

        @Override
        protected String getText() {
            return "Add @" + annotation;
        }

        @Override
        protected void performRewrite(TransformationContext ctx) throws Exception {
            Element el = ctx.getWorkingCopy().getTrees().getElement(ctx.getPath());

            if (el == null) {
                //what to do?
                return ;
            }

            SourceUtils.attachAnnotation(ctx.getWorkingCopy(), el, annotation);
        }

    }
}
