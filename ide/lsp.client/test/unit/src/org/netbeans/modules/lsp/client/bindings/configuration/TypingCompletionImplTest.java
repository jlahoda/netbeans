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

import javax.swing.JEditorPane;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import org.junit.Test;
import org.netbeans.modules.editor.NbEditorDocument;
import org.netbeans.modules.lsp.client.spi.friend.LanguageConfiguration;
import org.netbeans.modules.editor.lib2.typinghooks.TypingHooksSpiAccessor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor.MutableContext;

import static org.junit.Assert.*;

public class TypingCompletionImplTest {

    @Test
    public void testTypingCompletion() throws Exception {
        doTest("""
               |
               """,
               LanguageConfiguration.from("{ 'autoClosingPairs': [ { 'open': '(', 'close': ')' } ] }"),
               new Input('(',
                         """
                         (|)
                         """),
               new Input(')',
                         """
                         ()|
                         """));
        doTest("""
               a|
               """,
               LanguageConfiguration.from("{ 'autoClosingPairs': [ { 'open': '(', 'close': ')' } ] }"),
               new Input('(',
                         """
                         a(|)
                         """),
               new Input(')',
                         """
                         a()|
                         """));
        doTest("""
               <!-|
               """,
               LanguageConfiguration.from("{ 'autoClosingPairs': [ { 'open': '<!--', 'close': '-->' } ] }"),
               new Input('-',
                         """
                         <!--|-->
                         """));
        doTest("""
               !-|
               """,
               LanguageConfiguration.from("{ 'autoClosingPairs': [ { 'open': '<!--', 'close': '-->' } ] }"),
               new Input('-',
                         """
                         !--|
                         """));
    }

    private void doTest(String code, LanguageConfiguration config, Input... inputs) throws Exception {
        //TODO: could be nice to be also test mark > caret.
        NbEditorDocument doc = new NbEditorDocument("text/x-test");
        JTextComponent c = new JEditorPane();
        c.setDocument(doc);
        String[] parts = code.split("\\|");
        assertEquals("Caret marked incorrectly", 2, parts.length);
        int caret = parts[0].length();
        code = parts[0] + parts[1];
        doc.insertString(0, code, null);
        Caret caretObj = c.getCaret();
        caretObj.setDot(caret);
        TypingCompletionImpl typing = new TypingCompletionImpl(config);
        for (Input input : inputs) {
            String typed = String.valueOf(input.c());
            MutableContext context = TypingHooksSpiAccessor.get().createTtiContext(c, doc.createPosition(caretObj.getDot()), typed, "");
            boolean callAfterInsert = false;
            if (!typing.beforeInsert(context)) {
                typing.insert(context);
                callAfterInsert = true;
            }
            Object[] data = TypingHooksSpiAccessor.get().getTtiContextData(context);
            if (data == null) {
                data = new Object[] {typed, 1, false};
            }
            doc.insertString(caretObj.getDot(), (String) data[0], null);
            caretObj.setDot(caretObj.getDot() + (int) data[1]);
            assertFalse((Boolean) data[2]);
            if (callAfterInsert) {
                typing.afterInsert(context);
            }
            String expected = input.expectedOutput();
            String[] expectedParts = expected.split("\\|");
            int expectedCaret = expectedParts[0].length();
            expected = expectedParts[0] + expectedParts[1];
            assertEquals(expectedCaret, caretObj.getDot());
            assertEquals(expected, doc.getText(0, doc.getLength()));
        }
    }

    private record Input(char c, String expectedOutput) {}
}
