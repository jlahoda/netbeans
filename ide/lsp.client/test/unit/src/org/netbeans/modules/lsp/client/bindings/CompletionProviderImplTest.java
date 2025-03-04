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
package org.netbeans.modules.lsp.client.bindings;

import org.netbeans.junit.NbTestCase;

public class CompletionProviderImplTest extends NbTestCase {

    public CompletionProviderImplTest(String name) {
        super(name);
    }

    public void testConvertSnippet2CodeTemplate() {
        doRunTest("println(${1:args});$0",
                  "println(${T1 default=\"args\"});${cursor}");
        doRunTest("println(${1:args}, $1);$0",
                  "println(${T1 default=\"args\"}, ${T1});${cursor}");
        //choices are not supported currently:
        doRunTest("println(${1|one,two,three|});$0",
                  "println(${T1});${cursor}");
        //variables are not supported currently:
        doRunTest("println(${TM_SELECTED_TEXT:/upcase});$0",
                  "println(${P0});${cursor}");
    }

    private void doRunTest(String snippet, String template) {
        String converted = CompletionProviderImpl.convertSnippet2CodeTemplate(snippet);
        assertEquals(template, converted);
    }
}
