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
import com.google.gson.Strictness;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.api.project.Project;
import org.netbeans.modules.launch.support.spi.LaunchProjectConfiguration;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectConfigurationProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.*;

@ProjectServiceProvider(projectType="org-netbeans-modules-web-clientproject", service=ProjectConfigurationProvider.class)
public class ProjectConfigurationProviderImpl implements ProjectConfigurationProvider<LaunchProjectConfiguration> {

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.LENIENT).create();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Project prj;
    private List<LaunchProjectConfiguration> configurations = null;
    private LaunchProjectConfiguration activeConfiguration = null;

    public ProjectConfigurationProviderImpl(Project prj) {
        this.prj = prj;
    }

    @Override
    public Collection<LaunchProjectConfiguration> getConfigurations() {
        if (configurations == null) {
            List<LaunchProjectConfiguration> parsedConfigurations = new ArrayList<>();
            FileObject file = prj.getProjectDirectory().getFileObject(".vscode/launch.json");
            if (file != null) {
                try (InputStream in = file.getInputStream();
                     Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    List<Map<String, Object>> configurationsSettings = (List<Map<String, Object>>) GSON.fromJson(r, HashMap.class).getOrDefault("configurations", List.of());

                    for (Map<String, Object> settings : configurationsSettings) {
                        String name = (String) settings.getOrDefault("name", null);

                        if (name != null) {
                            parsedConfigurations.add(new LaunchProjectConfiguration(name, settings));
                        }
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            configurations = parsedConfigurations;
        }
        return configurations;
    }

    @Override
    public LaunchProjectConfiguration getActiveConfiguration() {
        return activeConfiguration;
    }

    @Override
    public void setActiveConfiguration(LaunchProjectConfiguration configuration) throws IllegalArgumentException, IOException {
        this.activeConfiguration = configuration;
    }

    @Override
    public boolean hasCustomizer() {
        return false;
    }

    @Override
    public void customize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean configurationsAffectAction(String command) {
        return switch (command) {
            case ActionProvider.COMMAND_RUN, ActionProvider.COMMAND_DEBUG -> true;
            default -> false;
        };
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener lst) {
        pcs.addPropertyChangeListener(lst);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener lst) {
        pcs.removePropertyChangeListener(lst);
    }
    
}
