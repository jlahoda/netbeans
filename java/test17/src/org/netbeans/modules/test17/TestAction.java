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
package org.netbeans.modules.test17;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HexFormat;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.test17.TestAction"
)
@ActionRegistration(
        displayName = "#CTL_TestAction"
)
@ActionReference(path = "Menu/File", position = 0)
@Messages("CTL_TestAction=Test")
public final class TestAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        StatusDisplayer.getDefault().setStatusText("From hex format: " + HexFormat.fromHexDigits("ae32"));
    }
}
