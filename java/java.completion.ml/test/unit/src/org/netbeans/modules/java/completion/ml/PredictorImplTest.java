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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author jlahoda
 */
public class PredictorImplTest extends NbTestCase {
    
    public PredictorImplTest(String name) {
        super(name);
    }
    
    public void testPredict() throws Exception {
        File dir = new File(PredictorImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        System.setProperty("netbeans.dirs", dir.getParentFile().getParentFile().getAbsolutePath());
        PredictorImpl predictor = new PredictorImpl();
        List<String> predicted = predictor.doPredict(predictor.possibleOutcomes("java.util.Map"), Arrays.asList(116, 8, 9, 33, 34, 49, 0, 3630, 3713, 8));
        System.err.println("predicted=" + predicted); //should include entrySet
    }

}
