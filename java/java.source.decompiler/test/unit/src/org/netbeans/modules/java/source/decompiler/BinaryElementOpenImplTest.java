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
package org.netbeans.modules.java.source.decompiler;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.java.source.BootClassPathUtil;
import org.netbeans.modules.java.source.ElementHandleAccessor;
import org.netbeans.modules.java.source.decompiler.BinaryElementOpenImpl.DecompileResult;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;

/**
 *
 * @author lahvac
 */
public class BinaryElementOpenImplTest extends NbTestCase {
    
    public BinaryElementOpenImplTest(String name) {
        super(name);
    }
    
    public void testOpen() {
        Class<?> current = BinaryElementOpenImplTest.class;
        ClassPath compile = ClassPathSupport.createClassPath(current.getProtectionDomain().getCodeSource().getLocation());
        ClasspathInfo cpInfo = ClasspathInfo.create(BootClassPathUtil.getBootClassPath(), compile, ClassPath.EMPTY);
        {
            ElementHandle<TypeElement> handle = ElementHandle.createTypeElementHandle(ElementKind.CLASS, current.getName());
            DecompileResult decompiled = new BinaryElementOpenImpl().decompile(cpInfo, handle, new AtomicBoolean());
            assertNotNull(decompiled);
            assertEquals(current.getName(), decompiled.binaryName);
            assertTrue(decompiled.content.contains("class " + current.getSimpleName()));
        }
        {
            ElementHandle<TypeElement> handle = ElementHandleAccessor.getInstance().create(ElementKind.METHOD, current.getName(), "testOpen", "()V");
            DecompileResult decompiled = new BinaryElementOpenImpl().decompile(cpInfo, handle, new AtomicBoolean());
            assertNotNull(decompiled);
            assertEquals(current.getName(), decompiled.binaryName);
            assertTrue(decompiled.content.contains("class " + current.getSimpleName()));
        }
        {
            ElementHandle<TypeElement> handle = ElementHandleAccessor.getInstance().create(ElementKind.METHOD, current.getName(), "nonExistent", "()V");
            DecompileResult decompiled = new BinaryElementOpenImpl().decompile(cpInfo, handle, new AtomicBoolean());
            assertNull(decompiled);
        }
        //TODO: test module-info.class
    }
    
}
