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

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.netbeans.api.diff.DiffController;
import org.netbeans.api.diff.StreamSource;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.TopComponent.Description;
import org.openide.windows.WindowManager;

/**
 *
 * @author lahvac
 */
public class DebuggingPreview {

    private final Document originalJavaDoc;
    private final Document newCodeDoc;
    private final TopComponent diffComponent;

    public DebuggingPreview(Document originalJavaDoc, String hintFileName) {
        assert SwingUtilities.isEventDispatchThread();

        this.originalJavaDoc = originalJavaDoc;
        this.newCodeDoc = MimeLookup.getLookup("text/x-java").lookup(EditorKit.class).createDefaultDocument();

        String[] text = new String[] {""};
        originalJavaDoc.render(() -> {
            try {
                text[0] = originalJavaDoc.getText(0, originalJavaDoc.getLength());
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
        setNewCode(text[0]);
        diffComponent = new DiffTopComponent();
        try {
            DiffController controller = DiffController.createEnhanced(StreamSource.createSource("Test1", "Test2", "text/x-java", new StringReader(text[0])), new DocStreamSource("Updated code", newCodeDoc));
            diffComponent.setDisplayName("Hint Preview - " + hintFileName);
            diffComponent.add(controller.getJComponent(), BorderLayout.CENTER);
            Mode mode = WindowManager.getDefault().findMode("output");
            mode.dockInto(diffComponent);
            diffComponent.open();
            diffComponent.requestVisible();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public void setNewCode(String newCodeText) {
        NbDocument.runAtomic((StyledDocument) newCodeDoc, () -> {
            try {
                newCodeDoc.remove(0, newCodeDoc.getLength());
                newCodeDoc.insertString(0, newCodeText, null);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    public Document getOriginalJavaDoc() {
        return originalJavaDoc;
    }

    public TopComponent getDiffComponent() {
        return diffComponent;
    }

    private static final class DocStreamSource extends StreamSource {

        private final String name;
        private final Document doc;
        private final Lookup lookup;

        public DocStreamSource(String name, Document doc) {
            this.name = name;
            this.doc = doc;
            this.lookup = Lookups.fixed(doc);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTitle() {
            return name;
        }

        @Override
        public String getMIMEType() {
            return "text/x-java";
        }

        @Override
        public Reader createReader() throws IOException {
            String[] text = new String[1];

            doc.render(() -> {
                try {
                    text[0] = doc.getText(0, doc.getLength());
                } catch (BadLocationException ex) {
                    text[0] = "";
                }
            });

            return new StringReader(text[0]);
        }

        @Override
        public Writer createWriter(org.netbeans.api.diff.Difference[] conflicts) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Lookup getLookup() {
            return lookup;
        }

    }

    @Description(preferredID="hint-debugging-diff",
                 persistenceType=TopComponent.PERSISTENCE_NEVER)
    private static class DiffTopComponent extends TopComponent {

        public DiffTopComponent() {
            setLayout(new BorderLayout());
        }
    }
}
