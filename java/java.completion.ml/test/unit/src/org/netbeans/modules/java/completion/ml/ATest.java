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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Callable;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.netbeans.api.scripting.Scripting;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class ATest {
    
    public ATest() {
    }
    
    @Test
    public void testSomeMethod() throws IOException, ScriptException, NoSuchMethodException {
        System.err.println("factories: " + Scripting.createManager().getEngineFactories());
        ScriptEngine engine = Scripting.newBuilder().build().getEngineByExtension("js");
        StringBuilder script = new StringBuilder();
//        Object installFetch = engine.eval("(function(self) { this['fetch'] = function (l) { return self.fetch(l); }; this['window'] = self; })");
        //https://github.com/github/fetch
//        engine.eval("")
        Object installFetch = engine.eval("(function(self) { this['fetch'] = function (l) { var content = self.fetch(l); var result = { ok: true}; var arr = new Uint8Array(content.getArray().length); for (var i = 0; i < arr.length; i++) arr[i] = content.getArray()[i]; console.log(arr.length); result.arrayBuffer = function() { return arr; }; result.text = function() { return content.getText(); }; result.json = function() { return JSON.parse(content.getText()); }; return Promise.resolve(result); }; })");
        ((Invocable) engine).invokeMethod(installFetch, "call", null, new Window());
        Object installConsole = engine.eval("(function(self) { this['console'] = self; })");
        ((Invocable) engine).invokeMethod(installConsole, "call", null, new Console());
        Object installReceive = engine.eval("(function(self) { this['receive'] = function (d) { self.dataReceived(d); }; })");
        ((Invocable) engine).invokeMethod(installReceive, "call", null, new Receive());
//        engine.eval("fetch(1);");
//        engine.eval("window.fetch(1);");
        script.append(FileUtil.toFileObject(Paths.get("/home/lahvac/src/nb/learning/tf.js").toFile()).asText());
        script.append("var model; tf.loadLayersModel('/home/lahvac/src/nb/learning/tjs/model.json').then(function(m) { console.log('has model!'); model = m; }, function(err) { console.log(err); });");
//        script.append("var example = [1, 2, 3, 4, 0, 1, 2, 5, 6, 7];");
//        script.append("var result = model.predict(example);");
        engine.eval(script.toString());
        System.err.println("hoho");
        for (int i = 0; i < 100; i++) {
            long s = System.currentTimeMillis();
            engine.eval("var example = tf.tensor([1, 2, 3, 4, 0, 1, 2, 5, 6, 7]); receive(model.predict(example).dataSync());");
            long e = System.currentTimeMillis();
            System.err.println("hoho, prediction took: " + (e - s));
        }
            long s = System.currentTimeMillis();
            engine.eval("var example = tf.tensor([829, 166, 3, 4, 991, 853, 834, 835, 5, 3]); receive(model.predict(example).dataSync());");
            long e = System.currentTimeMillis();
            System.err.println("hoho, prediction took: " + (e - s));
//        System.err.println(engine.getBindings(ScriptContext.GLOBAL_SCOPE).get("result"));
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
        public void dataReceived(float[] data) {
            System.err.println(Arrays.toString(data));
        }
    }
}
