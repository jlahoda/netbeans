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
package org.netbeans.modules.lsp.client.spi.friend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import java.io.IOException;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.openide.filesystems.FileObject;

//based on the VS Code API's "LanguageConfiguration" (MIT)
//TODO: the language-configuration.json is more broad, should be enhanced to support language-configuration.json:
public final class LanguageConfiguration {
    public final @NullAllowed CommentRule comments;
    public final @NullAllowed CharacterPair[] brackets;
    public final @NullAllowed RegExp wordPattern;
    public final @NullAllowed IndentationRule indentationRules;
    public final @NullAllowed OnEnterRule[] onEnterRules;
    public final @NullAllowed AutoClosingPair[] autoClosingPairs;

    private LanguageConfiguration(CommentRule comments, CharacterPair[] brackets, RegExp wordPattern, IndentationRule indentationRules, OnEnterRule[] onEnterRules, AutoClosingPair[] autoClosingPairs) {
        this.comments = comments;
        this.brackets = brackets;
        this.wordPattern = wordPattern;
        this.indentationRules = indentationRules;
        this.onEnterRules = onEnterRules;
        this.autoClosingPairs = autoClosingPairs;
    }
    
    public static LanguageConfiguration from(CommentRule comments, CharacterPair[] brackets, RegExp wordPattern, IndentationRule indentationRules, OnEnterRule[] onEnterRules, AutoClosingPair[] autoClosingPairs) {
        return new LanguageConfiguration(comments, brackets, wordPattern, indentationRules, onEnterRules, autoClosingPairs);
    }

    public static final class RegExp {
        public final @NonNull String regExp;

        public RegExp(String regExp) {
            this.regExp = regExp;
        }

        public static RegExp from(String regExp) {
            return new RegExp(regExp);
        }
        
    }
    public static final class CharacterPair {
        public final String first;
        public final String second;

        private CharacterPair(String first, String second) {
            this.first = first;
            this.second = second;
        }

        public static CharacterPair from(String first, String second) {
            return new CharacterPair(first, second);
        }
    }

    public static final class LineComment {
        public final @NonNull String comment;
        public final boolean noIdent;

        private LineComment(String comment, boolean noIdent) {
            this.comment = comment;
            this.noIdent = noIdent;
        }

        public static LineComment from(String comment) {
            return from(comment, false);
        }

        public static LineComment from(String comment, boolean noIdent) {
            return new LineComment(comment, noIdent);
        }
    }

    public static final class CommentRule {
        public final @NullAllowed LineComment lineComment;
        public final @NullAllowed CharacterPair blockComment;

        private CommentRule(LineComment lineComment, CharacterPair blockComment) {
            this.lineComment = lineComment;
            this.blockComment = blockComment;
        }

        public static CommentRule from(LineComment lineComment, CharacterPair blockComment) {
            return new CommentRule(lineComment, blockComment);
        }
    }

    public static final class IndentationRule {
        public @NullAllowed RegExp decreaseIndentPattern;
        public @NullAllowed RegExp increaseIndentPattern;
        public @NullAllowed RegExp indentNextLinePattern;
        public @NullAllowed RegExp unIndentedLinePattern;

        private IndentationRule(RegExp decreaseIndentPattern, RegExp increaseIndentPattern, RegExp indentNextLinePattern, RegExp unIndentedLinePattern) {
            this.decreaseIndentPattern = decreaseIndentPattern;
            this.increaseIndentPattern = increaseIndentPattern;
            this.indentNextLinePattern = indentNextLinePattern;
            this.unIndentedLinePattern = unIndentedLinePattern;
        }
        
        public static IndentationRule from(RegExp decreaseIndentPattern, RegExp increaseIndentPattern, RegExp indentNextLinePattern, RegExp unIndentedLinePattern) {
            return new IndentationRule(decreaseIndentPattern, increaseIndentPattern, indentNextLinePattern, unIndentedLinePattern);
        }
    }
    
    public static enum IndentAction {
        None,
        Indent,
        IndentOutdent,
        Outdent
    }

    public static final class EnterAction {
        public final @NonNull IndentAction indentAction;
        public final @NullAllowed String appendText;
        public final @NullAllowed Integer removeText;

        private EnterAction(IndentAction indentAction, String appendText, Integer removeText) {
            this.indentAction = indentAction;
            this.appendText = appendText;
            this.removeText = removeText;
        }

        public static EnterAction from(IndentAction indentAction, String appendText, Integer removeText) {
            return new EnterAction(indentAction, appendText, removeText);
        }
    }

    public static final class OnEnterRule {
        public final @NonNull RegExp beforeText;
        public final @NullAllowed RegExp afterText;
        public final @NullAllowed RegExp previousLineText;
        public final @NonNull EnterAction action;

        private OnEnterRule(RegExp beforeText, RegExp afterText, RegExp previousLineText, EnterAction action) {
            this.beforeText = beforeText;
            this.afterText = afterText;
            this.previousLineText = previousLineText;
            this.action = action;
        }
        
        public static OnEnterRule from(RegExp beforeText, RegExp afterText, RegExp previousLineText, EnterAction action) {
            return new OnEnterRule(beforeText, afterText, previousLineText, action);
        }
    }

    public static enum SyntaxTokenType {
        Other,
        Comment,
        String,
        RegEx
    }
    
    public static final class AutoClosingPair {
        public @NonNull String open;
        public @NonNull String close;
        public @NullAllowed SyntaxTokenType[] notIn;

        private AutoClosingPair(String open, String close, SyntaxTokenType[] notIn) {
            this.open = open;
            this.close = close;
            this.notIn = notIn;
        }
        
        public static AutoClosingPair from(String open, String close, SyntaxTokenType[] notIn) {
            return new AutoClosingPair(open, close, notIn);
        }
    }

    private static final Gson GSON = new Gson();
    public static LanguageConfiguration from(String source) {
        //TODO resilience against "incorrect" config:
        Map<String, Object> config = GSON.fromJson(source, HashMap.class);
        CommentRule comments = null;
        Map<String, Object> commentsConfig = (Map<String, Object>) config.get("comments");
        if (commentsConfig != null) {
            LineComment lineComment = null;
            Object lineCommentConfig = commentsConfig.get("lineComment");
            if (lineCommentConfig instanceof String comment) {
                lineComment = LineComment.from(comment);
            } else if (lineCommentConfig instanceof Map vals) {
                lineComment = LineComment.from((String) vals.get("comment"), (boolean) vals.getOrDefault("noIndent", false));
            }
            List<String> commentsBlockConfig = (List<String>) commentsConfig.get("blockComment");
            CharacterPair blockComment = null;

            if (commentsBlockConfig != null) {
                blockComment = CharacterPair.from(commentsBlockConfig.get(0), commentsBlockConfig.get(1));
            }
            comments = CommentRule.from(lineComment, blockComment);
        }
        return LanguageConfiguration.from(comments, null, null, null, null, null);
    }

    public static LanguageConfiguration create(FileObject source) throws IOException {
        return from(source.asText());
    }
}
