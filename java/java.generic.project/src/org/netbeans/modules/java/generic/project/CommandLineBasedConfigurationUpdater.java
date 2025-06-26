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
package org.netbeans.modules.java.generic.project;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.Configuration;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.ToolKind;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.ToolRun;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;

/**
 *
 * @author jlahoda
 */
public class CommandLineBasedConfigurationUpdater {
    private static final RequestProcessor WORKER = new RequestProcessor(CommandLineBasedConfigurationUpdater.class.getName(), 1, false, false);

    private File configurationFile;
    private final GenericJavaProject project;
    private final Configuration configuration;
    private final URI projectDir;
    private final FileChangeListener listener = new FileChangeAdapter() {
        @Override
        public void fileChanged(FileEvent fe) {
            scheduleUpdateToolRuns();
        }
        @Override
        public void fileDeleted(FileEvent fe) {
            scheduleUpdateToolRuns();
        }
        @Override
        public void fileDataCreated(FileEvent fe) {
            scheduleUpdateToolRuns();
        }
        @Override
        public void fileRenamed(FileRenameEvent fe) {
            scheduleUpdateToolRuns();
        }
        @Override
        public void fileFolderCreated(FileEvent fe) {
            scheduleUpdateToolRuns();
        }
    };
    private final Task update;

    public CommandLineBasedConfigurationUpdater(GenericJavaProject project, Configuration configuration) {
        this.project = project;
        this.configuration = configuration;
        this.projectDir = project.getProjectDirectory().toURI();
        this.update = WORKER.create(() -> {
            File newFile = new File(project.getConfigurationFile());
            if (!Objects.equals(configurationFile, newFile)) {
                if (configurationFile != null) {
                    FileUtil.removeFileChangeListener(listener, configurationFile);
                }
                configurationFile = newFile;
                FileUtil.addFileChangeListener(listener, configurationFile);
            }
            if (configurationFile.isFile()) {
                try {
                    updateToolRuns(Files.readString(configurationFile.toPath())); //TODO: encoding? UTF-8 might be OK.
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
        GenericJavaProject.getRootPreferences(project.getProjectDirectory())
                          .addPreferenceChangeListener(evt -> scheduleUpdateToolRuns());
        scheduleUpdateToolRuns();
    }

    private void scheduleUpdateToolRuns() {
        update.schedule(1000);
    }

    void updateToolRuns(String log) {
        List<ToolRun> newRuns = new ArrayList<>();
        for (String line : log.split("\\R")) {
            if (!line.startsWith("+ javac ")) {
                continue;
            }
            line = line.substring("+ javac ".length());
            String[] parts = line.split(" +");
            newRuns.add(new ToolRun(ToolKind.JAVAC, projectDir.getPath(), "javac", List.of(parts)));
        }
        configuration.setRuns(newRuns);
    }

}
