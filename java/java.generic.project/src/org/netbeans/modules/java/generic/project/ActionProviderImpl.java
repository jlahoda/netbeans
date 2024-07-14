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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.print.ConvertedLine;
import org.netbeans.api.extexecution.print.LineConvertor;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.java.generic.project.runner.Runner;
import org.netbeans.spi.project.ActionProvider;
import org.openide.LifecycleManager;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;

/**
 *
 * @author lahvac
 */
public class ActionProviderImpl implements ActionProvider {

    private static final String[] SUPPORTED_ACTIONS = {
        COMMAND_BUILD,
        COMMAND_CLEAN,
        COMMAND_REBUILD,
        COMMAND_RUN,
//        COMMAND_DEBUG,
    };

    private final GenericJavaProject project;

    public ActionProviderImpl(GenericJavaProject project) {
        this.project = project;
    }

    @Override
    public String[] getSupportedActions() {
        return SUPPORTED_ACTIONS;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        BuildConfiguration config = project.getActiveBuildConfiguration();
        File module = InstalledFileLocator.getDefault().locate("modules/org-netbeans-modules-java-generic-project.jar", "org.netbeans.modules.java.generic.project", false);
        FileObject basedirFO = project.getProjectDirectory();
        File basedir = FileUtil.toFile(basedirFO);
        ExecutionDescriptor executionDescriptor = new ExecutionDescriptor()
                .showProgress(true)
                .showSuspended(true)
                .frontWindowOnError(true)
                .controllable(true)
                .errConvertorFactory(() -> new LineConvertor() {
                    @Override
                    public List<ConvertedLine> convert(String string) {
                        return Arrays.stream(string.split("\n")).map(l -> ConvertedLine.forText(l, listenerForLine(basedir, l))).collect(Collectors.toList());
                    }
                });
        ExecutionService.newService(() -> {
            LifecycleManager.getDefault().saveAll();
//            if (COMMAND_DEBUG.equals(command)) {
//                List<List<String>> executablesFor = config.executablesFor(COMMAND_RUN);
//                return Debugger.startInDebugger(executablesFor.get(0), FileUtil.toFile(prj.getProjectDirectory()));
//            }
            List<List<String>> executablesFor;
            if (COMMAND_REBUILD.equals(command)) {
                executablesFor = new ArrayList<>();
                executablesFor.addAll(config.executablesFor(COMMAND_CLEAN));
                executablesFor.addAll(config.executablesFor(COMMAND_BUILD));
            } else {
                executablesFor = config.executablesFor(command);
            }
            String arg = executablesFor.stream().map(c -> quote(c.stream().map(p -> quote(p)).collect(Collectors.joining(" ")))).collect(Collectors.joining(" "));
            return new ProcessBuilder("java", "-classpath", module.getAbsolutePath(), Runner.class.getName(), arg).directory(basedir).start();
        }, executionDescriptor, ProjectUtils.getInformation(project).getDisplayName() + " - " + command).run();
    }
    
    private OutputListener listenerForLine(File basedir, String line) {
        Pattern errorLine = Pattern.compile("(.*):([1-9]+): (error|warning|note): (.*)");
        Matcher matcher = errorLine.matcher(line);
        if (matcher.find()) {
            String file = matcher.group(1);
            String lineNumber = matcher.group(2);
            File candidateFile = new File(file);
            if (!candidateFile.isAbsolute()) {
                candidateFile = new File(basedir, file);
            }
            FileObject targetFile = FileUtil.toFileObject(candidateFile);
            try {
                int lineNumberValue = Integer.parseInt(lineNumber);
                if (targetFile != null) {
                    return new OutputListener() {
                        @Override
                        public void outputLineSelected(OutputEvent oe) {}
                        @Override
                        public void outputLineAction(OutputEvent oe) {
                            LineCookie lc = targetFile.getLookup().lookup(LineCookie.class);
                            if (lc != null) {
                                Line line = lc.getLineSet().getCurrent(lineNumberValue);
                                if (line != null) {
                                    line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                                }
                            }
                        }
                        @Override
                        public void outputLineCleared(OutputEvent oe) {}
                    };
                }
            } catch (NumberFormatException ex) {
                //ignore
            }
        }
        return null;
    }

    private static String quote(String s) {
        return s.replace("_", "_u_").replace(" ", "_s_");
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
//        if (COMMAND_DEBUG.equals(command)) {
//            return isActionEnabled(COMMAND_RUN, context);
//        } else
        if (COMMAND_REBUILD.equals(command)) {
            return isActionEnabled(COMMAND_CLEAN, context) && isActionEnabled(COMMAND_BUILD, context);
        } else {
            return project.getActiveBuildConfiguration().executablesFor(command) != null;
        }
    }

}
