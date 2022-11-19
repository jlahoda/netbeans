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
package org.netbeans.modules.java.source.annotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.swing.text.Document;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.api.lexer.Language;
import org.netbeans.junit.NbTestCase;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class AugmentedAnnotationsTest extends NbTestCase {

    public AugmentedAnnotationsTest(String testName) {
        super(testName);
    }

    public void testGetAugmentedAnnotationMirrors1() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        TypeElement jlClass = info.getElements().getTypeElement("java.lang.Class");

        assertNotNull(jlClass);

        ExecutableElement toString = null;

        for (ExecutableElement ee : ElementFilter.methodsIn(jlClass.getEnclosedElements())) {
            if (ee.getSimpleName().contentEquals("toString") && ee.getParameters().isEmpty()) {
                toString = ee;
            }
        }

        assertNotNull(toString);

        String result = serializeAugmentedAnnotations(toString);

        assertEquals("@test.Test(clazz=TypeMirror:java.util.List, clazz-array=array:[TypeMirror:java.util.List, TypeMirror:java.util.ArrayList], string=String:test-value1, string-array=array:[String:value1, String:value2])\n", result);
    }

    public void testGetAugmentedAnnotationMirrors2() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        TypeElement jlClass = info.getElements().getTypeElement("java.lang.Class");

        assertNotNull(jlClass);

        ExecutableElement forName = null;

        for (ExecutableElement ee : ElementFilter.methodsIn(jlClass.getEnclosedElements())) {
            if (ee.getSimpleName().contentEquals("forName") && ee.getParameters().size() == 1) {
                forName = ee;
            }
        }

        assertNotNull(forName);

        String result = serializeAugmentedAnnotations(forName.getParameters().get(0));

        assertEquals("@test.Test(string=String:test-value2)\n", result);
    }

    public void testGetAugmentedAnnotationMirrorsConstructor() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        TypeElement jlClass = info.getElements().getTypeElement("java.lang.StringBuilder");

        assertNotNull(jlClass);

        ExecutableElement forName = null;

        for (ExecutableElement ee : ElementFilter.constructorsIn(jlClass.getEnclosedElements())) {
            if (ee.getParameters().size() == 1 && ee.getParameters().get(0).asType().getKind() == TypeKind.INT) {
                forName = ee;
                break;
            }
        }

        assertNotNull(forName);

        String result = serializeAugmentedAnnotations(forName.getParameters().get(0));

        assertEquals("@test.Test(string=String:test-value4)\n", result);
    }

    public void testGetAugmentedAnnotationMirrors3() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        TypeElement jlClass = info.getElements().getTypeElement("java.lang.Class");

        assertNotNull(jlClass);

        ExecutableElement forName = null;

        for (ExecutableElement ee : ElementFilter.methodsIn(jlClass.getEnclosedElements())) {
            if (ee.getSimpleName().contentEquals("getMethod") && ee.getParameters().size() == 2) {
                forName = ee;
            }
        }

        assertNotNull(forName);

        String result = serializeAugmentedAnnotations(forName.getParameters().get(0));

        assertEquals("@test.Test(string=String:test-value3)\n", result);
    }

    public void testNPEForPackage() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        PackageElement jl = info.getElements().getPackageElement("java.lang");

        assertNotNull(jl);

        String result = serializeAugmentedAnnotations(jl);

        assertEquals("", result);
    }

    public void testAddAnnotation() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        TypeElement tTest = info.getElements().getTypeElement("test.Test");

        assertNotNull(tTest);

        CacheBasedAnnotationDescriptionProvider.overrideCacheDirForTests = FileUtil.createFolder(sourceRoot, "../annotations-cache");

        assertTrue(AugmentedAnnotations.attachAnnotation(info, tTest, "@test.NonNull"));

        FileObject annotationsXML = sourceRoot.getFileObject("../annotations-cache/test/annotations.xml");

        assertNotNull(annotationsXML);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<root>\n" +
                     "    <item name=\"test.Test\">\n" +
                     "        <annotation name=\"test.NonNull\"/>\n" +
                     "    </item>\n" +
                     "</root>\n",
                     annotationsXML.asText("UTF-8"));
    }

    public void testAddAnnotation2() throws Exception {
        prepareTest("test/Test.java",
                    "package test;\n" +
                    "public class Test {\n" +
                    "}\n");

        TypeElement tTest = info.getElements().getTypeElement("test.Test");

        assertNotNull(tTest);

        CacheBasedAnnotationDescriptionProvider.overrideCacheDirForTests = FileUtil.createFolder(sourceRoot, "../annotations-cache");

        assertTrue(AugmentedAnnotations.attachAnnotation(info, tTest, "@test.Language(value=\"java\")"));

        FileObject annotationsXML = sourceRoot.getFileObject("../annotations-cache/test/annotations.xml");

        assertNotNull(annotationsXML);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<root>\n" +
                     "    <item name=\"test.Test\">\n" +
                     "        <annotation name=\"test.Language\">\n" +
                     "            <val name=\"value\" val=\"java\"/>\n" +
                     "        </annotation>\n" +
                     "    </item>\n" +
                     "</root>\n",
                     annotationsXML.asText("UTF-8"));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SourceUtilsTestUtil.prepareTest(new String[0], new Object[0]);
        clearWorkDir();

        FileUtil.refreshFor(File.listRoots());

        dataDir = getDataDir();
    }

    protected void prepareTest(String fileName, String code) throws Exception {
        FileObject workFO = FileUtil.createFolder(getWorkDir());

        assertNotNull(workFO);

        workFO.refresh();

        sourceRoot = workFO.createFolder("src");
        FileObject buildRoot  = workFO.createFolder("build");
        FileObject cache = workFO.createFolder("cache");

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

        JavaSource js = JavaSource.forFileObject(data);

        assertNotNull(js);

        info = SourceUtilsTestUtil.getCompilationInfo(js, Phase.RESOLVED);

        assertNotNull(info);
    }

    protected FileObject sourceRoot;
    protected CompilationInfo info;
    protected Document doc;

    private String serializeAugmentedAnnotations(Element forElement) {
        StringBuilder result = new StringBuilder();

        for (AnnotationMirror am : AugmentedAnnotations.getAugmentedAnnotationMirrors(info, forElement)) {
            result.append("@");
            result.append(((TypeElement) am.getAnnotationType().asElement()).getQualifiedName());
            result.append("(");

            boolean first = true;

            List<ExecutableElement> attributes = new ArrayList<ExecutableElement>(am.getElementValues().keySet());

            Collections.sort(attributes, new Comparator<ExecutableElement>() {
                @Override public int compare(ExecutableElement o1, ExecutableElement o2) {
                    return o1.getSimpleName().toString().compareTo(o2.getSimpleName().toString());
                }
            });

            for (ExecutableElement attr : attributes) {
                if (!first) result.append(", ");

                first = false;

                result.append(attr.getSimpleName());
                result.append("=");
                result.append(dumpAnnotationValue(info, am.getElementValues().get(attr)));
            }

            result.append(")\n");
        }
        return result.toString();
    }

    private String dumpAnnotationValue(CompilationInfo info, AnnotationValue valueMirror) {
        StringBuilder result = new StringBuilder();
        Object value = valueMirror.getValue();

        if (value instanceof TypeMirror) {
            result.append("TypeMirror");
            result.append(":");
            result.append(info.getTypes().erasure((TypeMirror) value).toString());
        } else if (value instanceof String) {
            result.append("String");
            result.append(":");
            result.append(value.toString());
        } else if (value instanceof Collection) {
            result.append("array:[");

            boolean first = true;

            for (Object obj : (Collection) value) {
                if (!first) result.append(", ");
                first = false;
                result.append(dumpAnnotationValue(info, (AnnotationValue) obj));
            }

            result.append("]");

            return result.toString();
        } else {
            result.append("UNKNOWN_TYPE");
            result.append(":");
            result.append(value.toString());
        }

        return result.toString();
    }

    private static File dataDir;

    @ServiceProvider(service=AnnotationsDescriptionProvider.class, position=10)
    public static final class TestAnnotationsDescriptionProvider implements AnnotationsDescriptionProvider {

        @Override
        public FileObject annotationDescriptionForRoot(FileObject root) {
            return dataDir != null ? FileUtil.toFileObject(dataDir) : null;
        }

    }
}
