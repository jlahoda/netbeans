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
package org.netbeans.modules.lsp.client.bindings.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.modules.lsp.client.spi.friend.LanguageConfiguration;
import org.netbeans.modules.lsp.client.spi.friend.LanguageConfiguration.AutoClosingPair;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;

public class TypingCompletionImpl implements TypedTextInterceptor {

    private static final Object KEY_SKIP_POSITIONS = new Object();
    private final LanguageConfiguration lc;
    private final Map<Character, AutoClosingPair[]> lastChar2ClosingPair; //TODO: could be optimized using a char
    private int moveOffset;

    public TypingCompletionImpl(LanguageConfiguration lc) {
        this.lc = lc;
        Map<Character, List<AutoClosingPair>> tempLastChar2ClosingPair = new HashMap<>();
        Arrays.stream(lc.autoClosingPairs)
              .forEach(p -> tempLastChar2ClosingPair.computeIfAbsent(p.open.charAt(p.open.length() - 1), x -> new ArrayList<>()).add(p));
        lastChar2ClosingPair = tempLastChar2ClosingPair.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toArray(AutoClosingPair[]::new)));
    }

    @Override
    public boolean beforeInsert(Context context) throws BadLocationException {
        return false;
    }

    @Override
    public void insert(MutableContext context) throws BadLocationException {
        List<Position> skipPositions = (List<Position>) context.getDocument().getProperty(KEY_SKIP_POSITIONS);
        if (skipPositions != null) {
            for (Iterator<Position> it = skipPositions.iterator(); it.hasNext();) {
                Position skipPosition = it.next();
                if (skipPosition.getOffset() == context.getOffset() && context.getDocument().getText(skipPosition.getOffset(), 1).equals(context.getText())) {
                    context.setText("", 0);
                    moveOffset = 1;
                    it.remove();
                    return ;
                }
            }
        }

        char lastChar = context.getText().charAt(0);
        AutoClosingPair[] candidates = lastChar2ClosingPair.get(lastChar);

        if (candidates != null) {
            for (AutoClosingPair candidate : candidates) {
                if (candidate.open.length() > 1) {
                    int prefixLen = candidate.open.length() - 1;
                    if (context.getDocument().getLength() <= prefixLen) {
                        continue;
                    }
                    if (!candidate.open.substring(0, prefixLen).equals(context.getDocument().getText(context.getOffset() - prefixLen, prefixLen))) {
                        continue;
                    }
                }
                context.setText(context.getText() + candidate.close, 1);
                moveOffset = -1;
                return ;
            }
        }
    }

    @Override
    public void afterInsert(Context context) throws BadLocationException {
        if (moveOffset == (-1)) {
            if (context.getText().length() == 2) {
                Position skipPosition = context.getDocument().createPosition(context.getComponent().getCaretPosition());
                List<Position> skipPositions = (List<Position>) context.getDocument().getProperty(KEY_SKIP_POSITIONS);
                if (skipPositions == null) {
                    skipPositions = new ArrayList<>();
                }
                skipPositions.add(skipPosition);
                context.getDocument().putProperty(KEY_SKIP_POSITIONS, skipPositions);
            }
        } else {
            context.getComponent().setCaretPosition(context.getComponent().getCaretPosition() + moveOffset);
        }
    }

    @Override
    public void cancelled(Context context) {
    }

    public static final class FactoryImpl implements Factory {

        @Override
        public TypedTextInterceptor createTypedTextInterceptor(MimePath mimePath) {
            LanguageConfiguration lc = MimeLookup.getLookup(mimePath).lookup(LanguageConfiguration.class);
            return lc != null && lc.autoClosingPairs != null && lc.autoClosingPairs.length > 0 ? new TypingCompletionImpl(lc) : null;
        }

        public static FactoryImpl create(Map map) {
            return new FactoryImpl();
        }
    }
}
