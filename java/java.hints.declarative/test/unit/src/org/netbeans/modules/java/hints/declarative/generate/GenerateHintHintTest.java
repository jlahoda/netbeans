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
package org.netbeans.modules.java.hints.declarative.generate;

import java.io.File;
import javax.swing.text.Document;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.api.java.source.SourceUtilsTestUtil2;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.api.lexer.Language;
import org.netbeans.junit.NbTestCase;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 *
 * @author lahvac
 */
public class GenerateHintHintTest extends NbTestCase {
    
    public GenerateHintHintTest(String name) {
        super(name);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SourceUtilsTestUtil.prepareTest(new String[] {"org/netbeans/modules/java/editor/resources/layer.xml"}, new Object[0]);
        SourceUtilsTestUtil2.disableConfinementTest();
    }

    public void testDetectPattern1() throws Exception {
        generatePatternTest("package test;\n" +
                            "public class Test {\n" +
                            "    public void test(String str) {\n" +
                            "        |System.err.println(str)|;\n" +
                            "    }\n" +
                            "}\n",
                            "java.lang.System.err.println($str) :: $str instanceof java.lang.String\n" +
                            "=>\n" +
                            "java.lang.System.err.println($str)\n" +
                            ";;\n");
    }

    public void testDetectPattern2() throws Exception {
        generatePatternTest("package test;\n" +
                            "public class Test {\n" +
                            "    public void test(String str) {\n" +
                            "        |System.err.println(str);\n" +
                            "        System.out.println(str);|\n" +
                            "    }\n" +
                            "}\n",
                            "java.lang.System.err.println($str);\n" +
                            /*XXX: whitespace*/"        " +
                            "java.lang.System.out.println($str); :: $str instanceof java.lang.String\n" +
                            "=>\n" +
                            "java.lang.System.err.println($str);\n" +
                            /*XXX: whitespace*/"        " +
                            "java.lang.System.out.println($str);\n" +
                            ";;\n");
    }

    private void generatePatternTest(String code, String generatedPattern) throws Exception {
        String[] codeSplit = code.split("\\|");
        
        prepareTest("test/Test.java", codeSplit[0] + codeSplit[1] + codeSplit[2]);

        String pattern = GenerateHintHint.generatePattern(info.getSnapshot().getSource(), codeSplit[0].length(), codeSplit[0].length() + codeSplit[1].length());
        assertEquals(generatedPattern, pattern);
    }

    private void prepareTest(String fileName, String code) throws Exception {
        clearWorkDir();
        File wdFile = getWorkDir();
        FileUtil.refreshFor(wdFile);

        FileObject wd = FileUtil.toFileObject(wdFile);
        assertNotNull(wd);
        sourceRoot = FileUtil.createFolder(wd, "src");
        FileObject buildRoot = FileUtil.createFolder(wd, "build");
        FileObject cache = FileUtil.createFolder(wd, "cache");

        FileObject data = FileUtil.createData(sourceRoot, fileName);
        File dataFile = FileUtil.toFile(data);

        assertNotNull(dataFile);

        TestUtilities.copyStringToFile(dataFile, code);

        SourceUtilsTestUtil.prepareTest(sourceRoot, buildRoot, cache);

        DataObject od = DataObject.find(data);
        EditorCookie ec = od.getLookup().lookup(EditorCookie.class);

        assertNotNull(ec);

        doc = ec.openDocument();
        doc.putProperty(Language.class, JavaTokenId.language());
        doc.putProperty("mimeType", "text/x-java");

        JavaSource js = JavaSource.forFileObject(data);

        assertNotNull(js);

        info = SourceUtilsTestUtil.getCompilationInfo(js, Phase.RESOLVED);

        assertNotNull(info);
    }

    private FileObject sourceRoot;
    private CompilationInfo info;
    private Document doc;
    
}
