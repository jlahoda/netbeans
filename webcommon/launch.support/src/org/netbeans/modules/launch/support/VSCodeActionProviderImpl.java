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
package org.netbeans.modules.launch.support;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.event.ChangeListener;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.print.ConvertedLine;
import org.netbeans.api.extexecution.print.LineConvertor;
import org.netbeans.api.project.Project;
import org.netbeans.modules.launch.support.spi.LaunchProjectConfiguration;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.LookupProvider.Registration.ProjectType;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.*;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.Union2;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

@ProjectServiceProvider(projectTypes=@ProjectType(id="org-netbeans-modules-web-clientproject", position=0), service=ActionProvider.class) //TODO: would be better if we could set this to all/any projects
public class VSCodeActionProviderImpl implements ActionProvider {

    private static final Gson GSON = new GsonBuilder().create();
    private static final RequestProcessor WORKER = new RequestProcessor(VSCodeActionProviderImpl.class.getName(), 1, false, false);
    private static final String[] ACTIONS = new String[] {
        COMMAND_DEBUG,
        COMMAND_RUN
    };

    private final Project prj;
    private final Map<String, BackgroundTask> name2RunningTask = new HashMap<>();

    public VSCodeActionProviderImpl(Project prj) {
        this.prj = prj;
    }

    @Override
    public String[] getSupportedActions() {
        return ACTIONS;
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        ActionProvider ap = getDelegate(command, getAugmentedContext(context));

        return  ap != null;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        ProxyLookup augmentedContext = getAugmentedContext(context);
        ActionProvider ap = getDelegate(command, augmentedContext);
        LaunchProjectConfiguration conf = context.lookup(LaunchProjectConfiguration.class);

        if (ap == null || conf == null) {
            //fail
            return ;
        }

        CompletableFuture<TaskStatus> launchTaskFinished = new CompletableFuture<>();
        String preLaunchTask = (String) conf.getProperties().get("preLaunchTask");

        if (preLaunchTask != null) {
            Map<String, Object> task = parseTasks().get(preLaunchTask);
            if (task != null) {
                if (task.getOrDefault("isBackground", Boolean.FALSE) instanceof Boolean b && b) {
                    BackgroundTask backgroundTask = runBackgroundTask(preLaunchTask, task);
                    WORKER.post(() -> {
                        synchronized (backgroundTask) {
                            while (backgroundTask.status == TaskStatus.BUSY) {
                                try {
                                    backgroundTask.wait();
                                } catch (InterruptedException ex) {
                                    //TODO:
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                            launchTaskFinished.complete(backgroundTask.status);
                        }
                    });
                } else {
                    //TODO!
                }
            } else {
                //TODO: what now?
            }
        } else {
            launchTaskFinished.complete(TaskStatus.SUCCESS);
        }

        launchTaskFinished.thenAccept(status -> {
            if (status == TaskStatus.FAILURE) {
                //what exactly?
                return ;
            }
            Map<String, Union2<String, Supplier<String>>> properties2Values = new HashMap<>();
            properties2Values.put("workspaceFolder", Union2.createFirst(FileUtil.toFile(prj.getProjectDirectory()).getAbsolutePath()));
            properties2Values.put("execPath", Union2.createSecond(() -> {
                //TODO: allow other modules to provide settings:
                String vsCode = getVSCodeLocation();
                if ("".equals(vsCode) || !Files.isExecutable(Path.of(vsCode))) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("Please setup path for VS Code in the settings."));
                    throw new RuntimeException("No VS Code set.");
                }
                return vsCode;
            }));
            Map<String, Object> resolvedProperties = (Map<String, Object>) expandVariables(conf.getProperties(), properties2Values);
            ProxyLookup resolvedLookup = new ProxyLookup(Lookups.exclude(augmentedContext, LaunchProjectConfiguration.class), Lookups.fixed(new LaunchProjectConfiguration(conf.getDisplayName(), resolvedProperties)));
            ap.invokeAction(command, resolvedLookup);
        });
    }

    private ActionProvider getDelegate(String command, Lookup context) {
        if (COMMAND_DEBUG.equals(command)) {
            System.err.println("!!!");
        }
        LaunchProjectConfiguration conf = context.lookup(LaunchProjectConfiguration.class);

        if (conf == null) {
            return null;
        }

        String type = (String) conf.getProperties().getOrDefault("type", "");

        for (ActionProvider ap : Lookups.forPath("launch.json/" + type).lookupAll(ActionProvider.class)) {
            if (Set.of(ap.getSupportedActions()).contains(command) && ap.isActionEnabled(command, context)) {
                return ap;
            }
        }

        return null;
    }

    private ProxyLookup getAugmentedContext(Lookup context) {
        return new ProxyLookup(context, Lookups.fixed(prj));
    }

    Map<String,Map<String, Object>> parseTasks() {
        FileObject tasksJsonFile = prj.getProjectDirectory().getFileObject(".vscode/tasks.json");

        if (tasksJsonFile == null) {
            return Map.of();
        }
        try (InputStream in = tasksJsonFile.getInputStream();
             Reader r = new InputStreamReader(in)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tasksJson = GSON.fromJson(r, HashMap.class);
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksJson.getOrDefault("tasks", List.of());

            for (Map<String, Object> task : tasks) {
                fixupLaunchConfig(task);
            }

            Map<String, Map<String, Object>> name2Task = new HashMap<>();

            tasks.forEach(task -> name2Task.put((String) task.getOrDefault("label", ""), task));

            return name2Task;
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return Map.of();
        }
    }

    private void fixupLaunchConfig(Map<String, Object> task) {
        if (!task.containsKey("label")) {
            //TODO: correct?
            task.put("label", task.get("type") + ": " + task.get("script"));
        }
        if (task.containsKey("problemMatcher")) {
            List<Object> problemMatchers = (List<Object>) task.get("problemMatcher");
            List<Object> expandedProblemMatchers = new ArrayList<>();

            for (Object problemMatcher : problemMatchers) {
                if (problemMatcher instanceof String variable && variable.startsWith("$")) {
                    expandedProblemMatchers.add(name2Matcher.get(variable.substring(1)));
                } else if (problemMatcher instanceof Map matcher) {
                    resolveInPlace(matcher);
                    expandedProblemMatchers.add(matcher);
                } else {
                    //we cannot handle this.
                }
            }

            task.put("problemMatcher", expandedProblemMatchers);
        }
    }

    private static boolean resolveInPlace(Map<String, Object> matcher) {
        String baseName = (String) matcher.remove("base");

        if (baseName == null) {
            return false;
        }

        Map<String, Object> base = name2Matcher.get(baseName.substring(1));

        if (base == null) {
            //TODO: error?
            return false;
        }

        boolean modified = false;

        for (Entry<String, Object> e : base.entrySet()) {
            if (!matcher.containsKey(e.getKey())) {
                matcher.put(e.getKey(), e.getValue());
                modified = true;
            }
        }

        return modified;
    }

    private Object expandVariables(Object value, Map<String, Union2<String, Supplier<String>>> variableName2Value) {
        if (value instanceof String str) {
            for (Map.Entry<String, Union2<String, Supplier<String>>> e : variableName2Value.entrySet()) {
                String key = "${" + e.getKey() + "}";
                if (str.contains(key)) {
                    String propertyValue = e.getValue().hasFirst() ? e.getValue().first() : e.getValue().second().get();
                    return str.replace(key, propertyValue);
                }
            }

            return value;
        } else if (value instanceof List<?> lst) {
            return lst.stream()
                      .map(v -> expandVariables(v, variableName2Value))
                      .toList();
        } else if (value instanceof Map<?, ?> map) {
            Map<Object, Object> newMap = new HashMap<>();

            for (Map.Entry<?, ?> e : map.entrySet()) {
                newMap.put(e.getKey(), expandVariables(e.getValue(), variableName2Value));
            }

            return newMap;
        } else {
            return value;
        }
    }

    //copied from: https://github.com/eamodio/vscode-tsl-problem-matcher
    private static final String MATCHERS =
            """
            [
                {
                    "name": "ts-webpack",
                    "label": "TypeScript Webpack problems (ts-loader)",
                    "owner": "typescript",
                    "source": "ts",
                    "applyTo": "closedDocuments",
                    "fileLocation": "absolute",
                    "severity": "error",
                    "pattern": [
                        {
                            "regexp": "\\\\[tsl\\\\] (ERROR|WARNING) in (.*)?\\\\((\\\\d+),(\\\\d+)\\\\)",
                            "severity": 1,
                            "file": 2,
                            "line": 3,
                            "column": 4
                        },
                        {
                            "regexp": "\\\\s*TS(\\\\d+):\\\\s*(.*)$",
                            "code": 1,
                            "message": 2
                        }
                    ]
                },
                {
                    "base": "$ts-webpack",
                    "name": "ts-webpack-watch",
                    "label": "TypeScript Webpack problems (ts-loader watch mode)",
                    "applyTo": "closedDocuments",
                    "fileLocation": "absolute",
                    "background": {
                        "activeOnStart": true,
                        "beginsPattern": {
                            "regexp": "[Cc]ompiling.*?|[Cc]ompil(ation|er) .*?starting"
                        },
                        "endsPattern": {
                            "regexp": "[Cc]ompiled (.*?successfully|with .*?(error|warning))|[Cc]ompil(ation|er) .*?finished"
                        }
                    }
                },
                {
                    "name": "ts-checker-webpack",
                    "label": "TypeScript Webpack problems (fork-ts-checker)",
                    "owner": "typescript",
                    "source": "ts",
                    "applyTo": "closedDocuments",
                    "fileLocation": [
                        "relative",
                        "${cwd}"
                    ],
                    "severity": "error",
                    "pattern": [
                        {
                            "kind": "location",
                            "regexp": "(ERROR|WARNING) in (.*?):(\\\\d+):(\\\\d+)",
                            "severity": 1,
                            "file": 2,
                            "line": 3,
                            "column": 4
                        },
                        {
                            "regexp": "\\\\s*TS(\\\\d+):\\\\s*(.*)$",
                            "code": 1,
                            "message": 2
                        }
                    ]
                },
                {
                    "base": "$ts-checker-webpack",
                    "name": "ts-checker-webpack-watch",
                    "label": "TypeScript Webpack problems (fork-ts-checker watch mode)",
                    "applyTo": "closedDocuments",
                    "fileLocation": [
                        "relative",
                        "${cwd}"
                    ],
                    "background": {
                        "activeOnStart": true,
                        "beginsPattern": {
                            "regexp": "[Cc]ompiling.*?|[Cc]ompil(ation|er) .*?starting"
                        },
                        "endsPattern": {
                            "regexp": "[Cc]ompiled (.*?successfully|with .*?(error|warning))|[Cc]ompil(ation|er) .*?finished"
                        }
                    }
                },
                {
                    "name": "ts-checker-eslint-webpack",
                    "label": "ESLint Webpack problems (fork-ts-checker)",
                    "owner": "typescript",
                    "source": "eslint",
                    "applyTo": "closedDocuments",
                    "fileLocation": [
                        "relative",
                        "${cwd}"
                    ],
                    "severity": "warning",
                    "pattern": [
                        {
                            "kind": "location",
                            "regexp": "(ERROR|WARNING) in (.*?):(\\\\d+):(\\\\d+)",
                            "severity": 1,
                            "file": 2,
                            "line": 3,
                            "column": 4
                        },
                        {
                            "regexp": "\\\\s*(@typescript-eslint\\\\/.+):\\\\s*(.*)$",
                            "code": 1,
                            "message": 2
                        }
                    ]
                },
                {
                    "base": "$ts-checker-eslint-webpack",
                    "name": "ts-checker-eslint-webpack-watch",
                    "label": "ESLint Webpack problems (fork-ts-checker watch mode)",
                    "applyTo": "closedDocuments",
                    "fileLocation": [
                        "relative",
                        "${cwd}"
                    ],
                    "background": {
                        "activeOnStart": true,
                        "beginsPattern": {
                            "regexp": "[Cc]ompiling.*?|[Cc]ompil(ation|er) .*?starting"
                        },
                        "endsPattern": {
                            "regexp": "[Cc]ompiled (.*?successfully|with .*?(error|warning))|[Cc]ompil(ation|er) .*?finished"
                        }
                    }
                },
                {
                    "name": "tslint-webpack",
                    "label": "TSLint Webpack problems (tslint-loader)",
                    "owner": "typescript",
                    "source": "tslint",
                    "applyTo": "closedDocuments",
                    "fileLocation": [
                        "relative",
                        "${cwd}"
                    ],
                    "severity": "warning",
                    "pattern": [
                        {
                            "regexp": "WARNING in (.*)",
                            "file": 1
                        },
                        {
                            "regexp": "Module Warning \\\\(.*?tslint-loader.*?\\\\):"
                        },
                        {
                            "regexp": "\\\\[(\\\\d+), (\\\\d+)\\\\]: (.*)",
                            "line": 1,
                            "column": 2,
                            "message": 3,
                            "loop": true
                        }
                    ]
                },
                {
                    "base": "$tslint-webpack",
                    "name": "tslint-webpack-watch",
                    "label": "TSLint Webpack problems (tslint-loader watch mode)",
                    "applyTo": "closedDocuments",
                    "fileLocation": [
                        "relative",
                        "${cwd}"
                    ],
                    "background": {
                        "activeOnStart": true,
                        "beginsPattern": {
                            "regexp": "[Cc]ompiling.*?|[Cc]ompil(ation|er) .*?starting"
                        },
                        "endsPattern": {
                            "regexp": "[Cc]ompiled (.*?successfully|with .*?(error|warning))|[Cc]ompil(ation|er) .*?finished"
                        }
                    }
                }
            ]
            """;
    //end of copied part

    //TODO: matchers from: https://github.com/microsoft/vscode/blob/930498f060256585f24848d78f2d8e5c5070d2ad/extensions/typescript-language-features/package.json#L3079

    private static final Map<String, Map<String, Object>> name2Matcher;
    static {
        name2Matcher = new HashMap<>();
        List<Map<String, Object>> matchers = GSON.fromJson(MATCHERS, ArrayList.class);

        for (Map<String, Object> matcher : matchers) {
            name2Matcher.put((String) matcher.get("name"), matcher);
        }

        boolean modified = true;

        while (modified) {
            modified = false;
            for (Map<String, Object> matcher : name2Matcher.values()) {
                modified |= resolveInPlace(matcher);
            }
        }
    }

    private BackgroundTask runBackgroundTask(String label, Map<String, Object> task) {
        BackgroundTask existingTask = name2RunningTask.get(label);

        if (existingTask != null && !existingTask.running.isDone()) {
            return existingTask;
        }

        List<String> command = new ArrayList<>();
        switch ((String) task.getOrDefault("type", "")) {
            case "npm" -> {
                command.add("npm");
                command.add("run");
                command.add((String) task.getOrDefault("script", ""));
            }
            default -> throw new UnsupportedOperationException();
        }
        BackgroundTask backgroundTask = new BackgroundTask();
        List<Map<String, Object>> problemMatchers;
        if (task.get("problemMatcher") instanceof List) {
            problemMatchers = (List<Map<String, Object>>) task.get("problemMatcher");
        } else {
            problemMatchers = List.of((Map<String, Object>) task.get("problemMatcher"));
        }
        List<Pattern> beginPatterns = new ArrayList<>();
        List<Pattern> endPatterns = new ArrayList<>();
        for (Map<String, Object> matcher : problemMatchers) {
            Map<String, Object> background = (Map<String, Object>) matcher.get("background");
            if (background == null) {
                continue;
            }
            //TODO: activeOnStart
            beginPatterns.add(getPatterns(background, "beginsPattern"));
            endPatterns.add(getPatterns(background, "endsPattern"));
            break; //TODO: multiple background matchers??
        }
        ExecutionDescriptor.LineConvertorFactory f = new ExecutionDescriptor.LineConvertorFactory() {
            @Override
            public LineConvertor newLineConvertor() {
                return new LineConvertor() {
                    @Override
                    public List<ConvertedLine> convert(String line) {
                        if (anyMatches(beginPatterns, line)) {
                            backgroundTask.workStarted();
                        } else if (anyMatches(endPatterns, line)) {
                            //TODO; success/failure detection
                            backgroundTask.workFinished();
                        }
                        return List.of(ConvertedLine.forText(line, null));
                    }
                };
            }
        };
        ExecutionDescriptor desc = new ExecutionDescriptor()
                .errConvertorFactory(f)
                .outConvertorFactory(f)
                .controllable(false);
        Callable<Process> runProcess = () -> {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(FileUtil.toFile(prj.getProjectDirectory()));
            return builder.start();
        };
        ExecutionService exec = org.netbeans.api.extexecution.ExecutionService.newService(runProcess, desc, (String) task.getOrDefault("label", ""));
        backgroundTask.setRunning(exec.run());
        name2RunningTask.put(label, backgroundTask);
        return backgroundTask;
    }

    private Pattern getPatterns(Map<String, Object> background, String key) {
        Map<String, Object> patternSpec = (Map<String, Object>) background.getOrDefault(key, Map.of());

        return Pattern.compile((String) patternSpec.getOrDefault("regexp", "")); //TODO: proper conversion!
    }

    private boolean anyMatches(List<Pattern> patterns, String line) {
        for (Pattern p : patterns) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static String getVSCodeLocation() {
        //TODO: would be better if modules could provide their settings via an API:
        String absolutePath = "org.netbeans.modules.vscode.debug";
        Preferences settings = NbPreferences.root().node(absolutePath.replace('.','/'));//NOI18N

        return settings.get("vscode.path", "");
    }

    private static final class BackgroundTask {
        private Future<Integer> running;
        private TaskStatus status = TaskStatus.BUSY;
        private int errorCount;

        public BackgroundTask() {}

        public synchronized void setRunning(Future<Integer> running) {
            this.running = running;
        }

        public synchronized void workStarted() {
            errorCount = 0;
            this.status = TaskStatus.BUSY;
            this.notifyAll();
        }

        public synchronized void workFinished() {
            this.status = errorCount == 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
            this.notifyAll();
        }

        public synchronized void reportError() {
            errorCount++;
        }

        public TaskStatus getStatus() {
            return status;
        }

    }
    private enum TaskStatus {
        SUCCESS,
        FAILURE,
        BUSY;
    }
}
