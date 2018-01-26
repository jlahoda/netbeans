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

package org.netbeans.nbbuild.extlibs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.netbeans.nbbuild.extlibs.licenseinfo.Fileset;
import org.netbeans.nbbuild.extlibs.licenseinfo.Licenseinfo;

public class ExclusionsFromLicenseInfo extends Task {

    private File nball;
    public void setNball(File nball) {
        this.nball = nball;
    }

    private String licenseinfoFileset;
    public void setLicenseinfoFileset(String licenseinfoFileset) {
        this.licenseinfoFileset = licenseinfoFileset;
    }
    
    private Set<String> modules;

    public @Override void execute() throws BuildException {
        try {
            modules = new TreeSet<>();
            for (String cluster : getProject().getProperty("nb.clusters.list").split("[, ]+")) {
                modules.addAll(Arrays.asList(getProject().getProperty(cluster).split("[, ]+")));
            }
            modules.add("nbbuild");

            FileSet licenseinfoFileset = new FileSet();
            licenseinfoFileset.setProject(getProject());
            licenseinfoFileset.setDir(nball.getAbsoluteFile());
            for (String module : modules) {
                File moduleDir = new File(nball, module);
                File licenseInfoFile = new File(moduleDir, "licenseinfo.xml");
                if (!licenseInfoFile.exists()) {
                    continue;
                }
                
                Licenseinfo li = Licenseinfo.parse(licenseInfoFile);
                for(Fileset fs: li.getFilesets()) {
                    for(File f: fs.getFiles()) {
                        Path relativePath = nball.toPath().relativize(f.toPath());
                        licenseinfoFileset.appendIncludes(new String[]{relativePath.toString()});
                    }
                }
            }
            
            getProject().addReference(this.licenseinfoFileset, licenseinfoFileset);
        } catch (IOException ex) {
            throw new BuildException(ex);
        }
    }
}
