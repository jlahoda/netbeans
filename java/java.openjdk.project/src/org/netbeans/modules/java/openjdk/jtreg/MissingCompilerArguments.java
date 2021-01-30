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
package org.netbeans.modules.java.openjdk.jtreg;

import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.modules.java.openjdk.jtreg.TagParser.Result;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.Hint.Options;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.JavaFix;
import org.netbeans.spi.java.hints.JavaFix.TransformationContext;
import org.netbeans.spi.java.hints.TriggerTreeKind;
import org.openide.util.NbBundle.Messages;

@Hint(displayName = "#DN_MissingCompilerArguments", description = "#DESC_MissingCompilerArguments", category = "general", options=Options.NO_BATCH)
@Messages({
    "DN_MissingCompilerArguments=Missing Compile Tag Arguments",
    "DESC_MissingCompilerArguments=Checks for @compile tags that are missing arguments, which are likely desirable."
})
public class MissingCompilerArguments {

    @TriggerTreeKind(Kind.COMPILATION_UNIT)
    @Messages({
        "ERR_NoRawDiagnostics=-XDrawDiagnostics missing, compiler output localized and unstable"
    })
    public static List<ErrorDescription> computeWarning(HintContext ctx) {
        Result tags = TagParser.parseTags(ctx.getInfo());
        List<ErrorDescription> result = new ArrayList<>();
        for (Tag tag : tags.getTags()) {
            if (!"compile".equals(tag.getName())) {
                continue;
            }
            if (tag.getValue().startsWith("/")) {
                String[] commandAndArgs = tag.getValue().split("[\\s]+");
                String firstParam = commandAndArgs[0];
                boolean hasRef = Arrays.stream(firstParam.split("/")).anyMatch(opt -> opt.startsWith("ref="));
                boolean hasRawDiagnostics = Arrays.stream(commandAndArgs).anyMatch(arg -> "-XDrawDiagnostics".equals(arg));
                if (hasRef && !hasRawDiagnostics) {
                    ErrorDescription idealED = ErrorDescriptionFactory.forName(ctx, ctx.getPath(), Bundle.ERR_NoRawDiagnostics(), new AddRawDiagnosticsFixImpl(ctx.getInfo(), ctx.getPath(), tag.getTagEnd() + firstParam.length()).toEditorFix());

                    result.add(org.netbeans.spi.editor.hints.ErrorDescriptionFactory.createErrorDescription(idealED.getSeverity(), idealED.getDescription(), idealED.getFixes(), ctx.getInfo().getFileObject(), tag.getTagStart(), tag.getTagEnd()));
                }
            }
        }
        return result;
    }

    private static final class AddRawDiagnosticsFixImpl extends JavaFix {

        private final int pos;

        public AddRawDiagnosticsFixImpl(CompilationInfo info, TreePath tp, int pos) {
            super(info, tp);
            this.pos = pos;
        }

        @Override
        @Messages("FIX_AddRawDiagnosticsFixImpl=Add -XDrawDiagnostics")
        protected String getText() {
            return Bundle.FIX_AddRawDiagnosticsFixImpl();
        }

        @Override
        protected void performRewrite(TransformationContext ctx) throws IOException {
            ctx.getWorkingCopy().rewriteInComment(pos, 0, " -XDrawDiagnostics");
        }
    }
}
