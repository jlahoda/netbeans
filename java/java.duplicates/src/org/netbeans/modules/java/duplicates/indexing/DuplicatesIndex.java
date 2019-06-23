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

package org.netbeans.modules.java.duplicates.indexing;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.modules.java.duplicates.ComputeDuplicates;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public class DuplicatesIndex {

    private final org.apache.lucene.index.IndexWriter luceneWriter;
    private final URL sourceRoot;

    public DuplicatesIndex(URL sourceRoot, FileObject cacheRoot) throws IOException {
        this.sourceRoot = sourceRoot;
        File cacheRootFile = FileUtil.toFile(cacheRoot);
        try {
            luceneWriter = new IndexWriter(FSDirectory.open(new File(cacheRootFile, NAME)), new NoAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
        } catch (CorruptIndexException ex) {
            throw new IllegalStateException(ex);
        } catch (LockObtainFailedException ex) {
            throw new IllegalStateException(ex);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void record(final CompilationInfo info, Indexable idx, final CompilationUnitTree cut) throws IOException {
        record(info.getTrees(), idx, cut);
    }

    public void record(final Trees trees, Indexable idx, final CompilationUnitTree cut) throws IOException {
        try {
            String relative = sourceRoot.toURI().relativize(idx.getURL().toURI()).toString();
            final Document doc = new Document();

            doc.add(new Field("duplicatesPath", relative, Field.Store.YES, Field.Index.NOT_ANALYZED));

            final Map<String, long[]> positions = ComputeDuplicates.encodeGeneralized(trees, cut);

            for (Entry<String, long[]> e : positions.entrySet()) {
                doc.add(new Field("duplicatesGeneralized", e.getKey(), Store.YES, Index.NOT_ANALYZED));

                StringBuilder positionsSpec = new StringBuilder();

                for (int i = 0; i < e.getValue().length; i += 2) {
                    if (positionsSpec.length() > 0) positionsSpec.append(';');
                    positionsSpec.append(e.getValue()[i]).append(':').append(e.getValue()[i + 1] - e.getValue()[i]);
                }

                doc.add(new Field("duplicatesPositions", positionsSpec.toString(), Store.YES, Index.NO));
            }

            luceneWriter.addDocument(doc);
        } catch (ThreadDeath td) {
            throw td;
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        } catch (Throwable t) {
            Logger.getLogger(DuplicatesIndex.class.getName()).log(Level.WARNING, null, t);
        }
    }

    public void remove(String relativePath) throws IOException {
        luceneWriter.deleteDocuments(new Term("duplicatesPath", relativePath));
    }

    public void close() throws IOException {
        luceneWriter.close();
    }

    public static final String NAME = "duplicates"; //NOI18N
    public static final int    VERSION = 1; //NOI18N

    public static final class NoAnalyzer extends Analyzer {

        @Override
        public TokenStream tokenStream(String string, Reader reader) {
            throw new UnsupportedOperationException("Should not be called");
        }

    }
}
