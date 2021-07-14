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
package org.netbeans.modules.java.hints.declarative.debugging;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import javax.swing.event.CaretListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.modules.OnStart;

/**
 *
 * @author lahvac
 */
public class DocumentTracker implements PropertyChangeListener {

    private static final Object LISTENER = new Object();
            static final Object SELECTION_SPAN = new Object();
    private static DocumentTracker INSTANCE = new DocumentTracker();
    public static Reference<Document> lastJavaDoc;
    public static Reference<JTextComponent> lastJavaComponent;

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        JTextComponent c = EditorRegistry.focusedComponent();
        if (c != null && "text/x-java".equals(NbEditorUtilities.getMimeType(c))) {
            if (c.getClientProperty(LISTENER) == null) {
                CaretListener cl = (e) -> {
                    c.getDocument().putProperty(SELECTION_SPAN, new int [] {e.getDot(), e.getMark()});
                };
                c.putClientProperty(LISTENER, cl);
                c.addCaretListener(cl);
            }
            if (lastJavaDoc == null || lastJavaDoc.get() != c.getDocument()) {
                lastJavaDoc = new WeakReference<>(c.getDocument());
                lastJavaComponent = new WeakReference<>(c);
            }
        }
    }

    @OnStart
    public static final class Init implements Runnable {

        @Override
        public void run() {
            EditorRegistry.addPropertyChangeListener(INSTANCE);
        }
        
    }
}
