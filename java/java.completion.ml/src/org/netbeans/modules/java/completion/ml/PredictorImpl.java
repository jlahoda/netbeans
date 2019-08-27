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
package org.netbeans.modules.java.completion.ml;

import com.sun.source.util.TreePath;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.scripting.Scripting;
import org.netbeans.modules.java.completion.JavaCompletionTask.Predictor;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.*;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=Predictor.class)
public class PredictorImpl implements Predictor {
    private final ScriptEngine engine;

    public PredictorImpl() throws ScriptException, NoSuchMethodException, IOException {
        long s = System.currentTimeMillis();
        this.engine = Scripting.newBuilder().build().getEngineByExtension("js");
        StringBuilder script = new StringBuilder();
        //fetch implementation inspired by (but not based on): https://github.com/github/fetch
        Object installFetch = engine.eval("(function(self) { this['fetch'] = function (l) { var content = self.fetch(l); var result = { ok: true}; var arr = new Uint8Array(content.getArray().length); for (var i = 0; i < arr.length; i++) arr[i] = content.getArray()[i]; result.arrayBuffer = function() { return arr; }; result.text = function() { return content.getText(); }; result.json = function() { return JSON.parse(content.getText()); }; return Promise.resolve(result); }; })");
        ((Invocable) engine).invokeMethod(installFetch, "call", null, new Window());
        Object installConsole = engine.eval("(function(self) { this['console'] = self; })");
        ((Invocable) engine).invokeMethod(installConsole, "call", null, new Console());
        script.append(FileUtil.toFileObject(Paths.get(DATA_DIR + "/tf.js").toFile()).asText());
        script.append("var model; tf.loadLayersModel('" + DATA_DIR + "/model.json').then(function(m) { model = m; }, function(err) { console.log(err); });");
        engine.eval(script.toString());
        long e = System.currentTimeMillis();
        System.err.println("Predictor preparation took: " + (e - s));
    }
    
    private static final File DATA_DIR;

    static {
        DATA_DIR = InstalledFileLocator.getDefault().locate("modules/data/org.netbeans.modules.java.completion.ml", "org.netbeans.modules.java.completion.ml", false);
    }

    private static Map<String, Integer> readVocabulary() {
        Map<String, Integer> vocabulary = new HashMap<>();
        try (InputStream isVocabulary = new FileInputStream(new File(DATA_DIR, "train_vocabulary.txt"));
             BufferedReader inVocabulary = new BufferedReader(new InputStreamReader(isVocabulary))) {
            String line;
            int i = 0;
            while ((line = inVocabulary.readLine()) != null) {
                vocabulary.put(line, i++);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return vocabulary;
    }

    public List<String> predict(CompilationInfo info, TreePath tp, TypeMirror type) {
        long s = System.currentTimeMillis();
        try {
            List<String> outcomes = possibleOutcomes(info.getElements().getBinaryName((TypeElement) ((DeclaredType) type).asElement()).toString());
            if (outcomes == null) {
                return null;
            }

            List<String> dump = ASTDumper.dumpAST(info, tp);
            if (dump == null) {
                System.err.println("no dump");
                return null;
            }
            return doPredict(outcomes, ASTDumper.convert(dump, readVocabulary(), false));
        } catch (ScriptException | IOException | NoSuchMethodException ex) {
            Exceptions.printStackTrace(ex);
            return Collections.emptyList();
        } finally {
            long e = System.currentTimeMillis();
            System.err.println("prediction took: " + (e - s));
        }
    }

    List<String> possibleOutcomes(String type) throws IOException {
        try (InputStream isOutcomes = new FileInputStream(new File(DATA_DIR, "train_outcomes.txt"))) {
            Properties outcomesProp = new Properties();
            outcomesProp.load(isOutcomes);
            String outcomesList = outcomesProp.getProperty(type);
            if (outcomesList == null) {
                System.err.println("no outcomes");
                return null;
            }
            return Arrays.asList(outcomesList.split(", *"));
        }
    }
    
    List<String> doPredict(List<String> possibleOutcomes, List<Integer> inputData) throws ScriptException, NoSuchMethodException {
        String tensor = inputData.stream().map(i -> String.valueOf(i)).collect(Collectors.joining(", ", "[", "]"));
        System.err.println("tensor=" + tensor);
        Receive rec = new Receive();
        Object installReceive = engine.eval("(function(self) { this['receive'] = function (d) { self.dataReceived(d); }; })");
        ((Invocable) engine).invokeMethod(installReceive, "call", null, rec);
        engine.eval("var inputData = tf.tensor([" + tensor + "]); receive(model.predict(inputData).dataSync())");
        float[] output = rec.data;
        System.err.println("prediction result=" + output.length + ":" + Arrays.toString(output));
        List<Outcome> results = new ArrayList<>();
        int i = 0;
        for (float f : output/*[0]*/) {
            if (i >= possibleOutcomes.size()) break;
            results.add(new Outcome(possibleOutcomes.get(i++), f));
        }
        System.err.println("outcomes1= " + results);
        Collections.sort(results, (r1, r2) -> -Float.compare(r1.value, r2.value));
        return results.stream().filter(r -> r.value > 0).limit(5).map(o -> o.signature).collect(Collectors.toList());
    }

    private static final class Outcome {
        public final String signature;
        public final float value;

        public Outcome(String signature, float value) {
            this.signature = signature;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Outcome{" + "signature=" + signature + ", value=" + value + '}';
        }
        
    }

    public static class Window {
        public Object fetch(String path) throws IOException {
            return new FetchedData(FileUtil.toFileObject(Paths.get(path).toFile()).asBytes());
        }
    }
    public static class FetchedData {
        private final byte[] content;

        public FetchedData(byte[] content) {
            this.content = content;
        }
        
        public byte[] getArray() {
            return content;
        }
        public String getText() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }
    public static class Console {
        public void log(Object obj) {
            System.err.println("log: " + obj);
        }
    }
    public static class Receive {
        public float[] data;
        public void dataReceived(float[] data) {
            this.data = data;
        }
    }
}
