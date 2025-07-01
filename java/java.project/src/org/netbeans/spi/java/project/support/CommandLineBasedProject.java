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
package org.netbeans.spi.java.project.support;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.modules.java.project.commandline.ClassPathProviderImpl;
import org.netbeans.modules.java.project.commandline.CommandLineBasedJavaRoot;
import org.netbeans.modules.java.project.commandline.CompilerOptionsQueryImpl;
import org.netbeans.modules.java.project.commandline.SourceForBinaryQueryImpl;
import org.netbeans.modules.java.project.commandline.SourceLevelQueryImpl;
import org.netbeans.modules.java.project.commandline.SourcesImpl;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

public class CommandLineBasedProject {

    private CommandLineBasedProject() {
    }

    public static Lookup projectLookupBase(Configuration configuration) {
        CommandLineBasedJavaRoot root = new CommandLineBasedJavaRoot(configuration);
        return Lookups.fixed(new ClassPathProviderImpl(root),
                             new CompilerOptionsQueryImpl(root),
                             new SourceForBinaryQueryImpl(root),
                             new SourceLevelQueryImpl(root),
                             new SourcesImpl(configuration, root));
    }

    public static final class ToolRun {
        public final ToolKind kind;
        public final String cwd;
        public final String toolPath;
        public final List<String> toolArguments;

        public ToolRun(ToolKind kind, String cwd, String toolPath, List<String> toolArguments) {
            this.kind = kind;
            this.cwd = cwd;
            this.toolPath = toolPath;
            this.toolArguments = toolArguments;
        }

    }

    public static final class Configuration {
        private final ChangeSupport cs = new ChangeSupport(this);
        private final Project project;
        private List<ToolRun> runs;

        public Configuration(Project project) { //TODO: project needed for generic source groups - can be done better?
            this.project = project;
            this.runs = List.of();
        }

        public Project getProject() {
            return project;
        }

        public synchronized List<ToolRun> getRuns() {
            return new ArrayList<>(runs);
        }

        public void setRuns(List<ToolRun> runs) {
            synchronized (this) {
                this.runs = new ArrayList<>(runs);
            }
            cs.fireChange();
        }

        public void addChangeListener(ChangeListener l) {
            cs.addChangeListener(l);
        }

        public void removeChangeListener(ChangeListener l) {
            cs.removeChangeListener(l);
        }
    }

    public enum ToolKind {
        JAVAC;
    }
}
