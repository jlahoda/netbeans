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
package org.netbeans.modules.launch.support;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.netbeans.api.project.Project;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

public class VSCodeActionProviderImplTest extends NbTestCase {

    private static final Gson GSON = new GsonBuilder().setFormattingStyle(FormattingStyle.PRETTY).create();

    public VSCodeActionProviderImplTest(String name) {
        super(name);
    }
    
    public void testParseTasks() throws Exception {
        clearWorkDir();
        File wdFile = getWorkDir();
        FileObject wd = FileUtil.toFileObject(wdFile);
        try (OutputStream out = FileUtil.createData(wd, ".vscode/tasks.json").getOutputStream()) {
            out.write("""
                      {
                              "version": "2.0.0",
                              "tasks": [
                                      {
                                              "type": "npm",
                                              "script": "watch",
                                              "group": "build",
                                              "isBackground": true,
                                              "problemMatcher": [
                                                      "$ts-webpack-watch",
                                                      "$tslint-webpack-watch"
                                              ]
                                      }
                              ]
                      }
                      """.getBytes());
        }
        VSCodeActionProviderImpl instance = new VSCodeActionProviderImpl(new Project() {
            @Override
            public FileObject getProjectDirectory() {
                return wd;
            }

            @Override
            public Lookup getLookup() {
                return Lookup.EMPTY;
            }
        });
        List<Map<String, Object>> expResult = null;
        List<Map<String, Object>> result = instance.parseTasks();

        String resultText = GSON.toJson(result);
//        assertEquals(expResult, result);
    }
    
}
