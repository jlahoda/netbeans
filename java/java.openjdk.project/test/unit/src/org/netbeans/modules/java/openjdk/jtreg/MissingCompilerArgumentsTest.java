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

import org.junit.Test;
import org.netbeans.modules.java.hints.test.api.HintTest;

public class MissingCompilerArgumentsTest {

    @Test
    public void testNoRawDiagnostics() throws Exception {
        HintTest.create()
                .input("/*@test\n" +
                       " *@compile/fail/ref=Test.out Test.java\n" +
                       " */\n" +
                       "class Test {\n" +
                       "}\n")
                .run(MissingCompilerArguments.class)
                .findWarning("1:2-1:10:verifier:" + Bundle.ERR_NoRawDiagnostics())
                .applyFix()
                .assertVerbatimOutput("/*@test\n" +
                                      " *@compile/fail/ref=Test.out -XDrawDiagnostics Test.java\n" +
                                      " */\n" +
                                      "class Test {\n" +
                                      "}\n");
    }

    @Test
    public void testNoIssues() throws Exception {
        HintTest.create()
                .input("/*@test\n" +
                       " *@compile/fail/ref=Test.out -XDrawDiagnostics Test.java\n" +
                       " */\n" +
                       "class Test {\n" +
                       "}\n")
                .input("test/Test.out",
                       "Output",
                       false)
                .run(MissingCompilerArguments.class)
                .assertWarnings();
    }

}
