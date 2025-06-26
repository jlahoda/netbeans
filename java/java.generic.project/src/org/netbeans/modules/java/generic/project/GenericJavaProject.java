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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.ImageIcon;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager.Result;
import org.netbeans.modules.java.generic.project.ui.customizer.CustomizerProviderImpl;
import org.netbeans.spi.java.project.support.CommandLineBasedProject;
import org.netbeans.spi.java.project.support.CommandLineBasedProject.Configuration;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectFactory2;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author jlahoda
 */
public class GenericJavaProject implements Project {

    public static final String PROJECT_KEY = "org-netbeans-modules-java-generic-GenericJavaProject";
    public static final String KEY_PROJECT_CONFIGURATION = "project-configuration";
    public static final String KEY_NEXT_MARK = "next-mark";
    public static final String KEY_IS_PROJECT = "is-project";

    private final FileObject projectDir;
    private final Lookup lookup;
    private final CommandLineBasedConfigurationUpdater roots;
    private final AtomicReference<BuildConfiguration> buildConfigurations = new AtomicReference<>();

    public GenericJavaProject(FileObject projectDir) {
        this.projectDir = projectDir;
        Configuration configuration = new Configuration(this);
        this.roots = new CommandLineBasedConfigurationUpdater(this, configuration);
        Lookup projectSpecificLookup =
                Lookups.fixed(new LogicalViewProviderImpl(this),
                              new ActionProviderImpl(this),
                              new CustomizerProviderImpl(this),
                              this); //XXX
        this.lookup = new ProxyLookup(projectSpecificLookup, CommandLineBasedProject.projectLookupBase(configuration));
        buildConfigurations.set(BuildConfiguration.read(getBuildPreferences(projectDir)));
    }

    @Override
    public FileObject getProjectDirectory() {
        return projectDir;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    public CommandLineBasedConfigurationUpdater getRoots() {
        return roots;
    }

    public JavaPlatform getProjectJavaPlatform() {
        return JavaPlatform.getDefault();
    }

    public BuildConfiguration getActiveBuildConfiguration() {
        return buildConfigurations.get();
    }

    public void setActiveBuildConfiguration(BuildConfiguration config) {
        buildConfigurations.set(config);
    }

    public String getConfigurationFile() {
        return getRootPreferences(projectDir).get(KEY_PROJECT_CONFIGURATION, "");
    }

    public void setConfigurationFile(String compileCommands) {
        getRootPreferences(projectDir).put(KEY_PROJECT_CONFIGURATION, compileCommands);
    }

    public static Preferences getRootPreferences(FileObject root) {
        return getRootPreferences(root, true);
    }

    private static Preferences getRootPreferences(FileObject root, boolean create) {
        String encoded = root.toURI().toString().replace("/", ".");
        Preferences projectsRoot = NbPreferences.forModule(GenericJavaProject.class).node("projects");
        try {
            for (String key : projectsRoot.keys()) {
                if (encoded.equals(projectsRoot.get(key, null))) {
                    return projectsRoot.node(key);
                }
            }
        } catch (BackingStoreException ex) {
        }
        if (!create) {
            return null;
        }
        synchronized (GenericJavaProject.class) {
            int mark = projectsRoot.getInt(KEY_NEXT_MARK, 0);
            String key = Integer.toHexString(mark);
            projectsRoot.putInt(KEY_NEXT_MARK, mark + 1);
            projectsRoot.put(key, encoded);
            return projectsRoot.node(key);
        }
    }

    public static Preferences getBuildPreferences(FileObject root) {
        return getRootPreferences(root).node("build");
    }

    @ServiceProvider(service=ProjectFactory.class)
    public static final class ProjectFactoryImpl implements ProjectFactory2 {

        private static final ImageIcon ICON = ImageUtilities.loadImageIcon("org/netbeans/modules/java/generic/project/resources/project.png", false);
        private static final Result RESULT = new Result(ICON);

        @Override
        public boolean isProject(FileObject fo) {
            Preferences prefs = getRootPreferences(fo, false);

            return prefs != null && prefs.getBoolean(KEY_IS_PROJECT, false);
        }

        @Override
        public Result isProject2(FileObject fo) {
            if (isProject(fo)) {
                return RESULT;
            }
            return null;
        }
        
        @Override
        public Project loadProject(FileObject fo, ProjectState ps) throws IOException {
            if (isProject(fo))
                return new GenericJavaProject(fo);
            return null;
        }

        @Override
        public void saveProject(Project prjct) throws IOException, ClassCastException {
        }

    }
}
