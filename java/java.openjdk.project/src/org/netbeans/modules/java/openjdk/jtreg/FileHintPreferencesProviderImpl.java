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

import java.util.prefs.Preferences;
import org.netbeans.modules.editor.hints.settings.friend.FileHintPreferencesProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=FileHintPreferencesProvider.class, position=1000)
public class FileHintPreferencesProviderImpl implements FileHintPreferencesProvider {

    @Override
    public Preferences getFilePreferences(FileObject file, String mimeType) {
        if (!"text/x-java".equals(mimeType)) return null;

        TestRootDescription rootDesc = TestRootDescription.findRootDescriptionFor(file);

        if (rootDesc == null) {
            return null;
        }

        Preferences testPreferences = NbPreferences.forModule(FileHintPreferencesProvider.class).node("jtreg");
        Preferences unusedHintNode = testPreferences.node("org.netbeans.modules.java.hints.bugs.Unused");
        if ("undefined".equals(unusedHintNode.get("detect.unused.package.private", "undefined"))) {
            //unused elements are common in tests, and often not bugs/problematic
            //better disable the unused element hint in tests, to avoid too many false positives
            //(note semantic highlighting should still mark them):
            unusedHintNode.putBoolean("detect.unused.package.private", false);
        }

        return testPreferences;
    }

    @Override
    public boolean openFilePreferences(FileObject file, String mimeType, String hintId) {
        return false;
    }

}
