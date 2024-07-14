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
package org.netbeans.modules.java.generic.project.ui.wizard;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.templates.TemplateRegistration;
import org.netbeans.modules.java.generic.project.BuildConfiguration;
import org.netbeans.modules.java.generic.project.GenericJavaProject;
import org.netbeans.modules.java.generic.project.ui.Build;
import org.netbeans.modules.java.generic.project.ui.Editor;
import org.openide.WizardDescriptor;
import org.openide.WizardDescriptor.Panel;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author lahvac
 */
@TemplateRegistration(
    folder="Project/Java",
    position=1000000,
    displayName="#template_generic_java",
    iconBase="org/netbeans/modules/java/generic/project/resources/project.png",
    description="GenericJavaProjectDescription.html"
)
@Messages({"template_generic_java=Generic Java Project",
           "CAP_ProjectPath=Location",
           "CAP_Editor=Editor",
           "CAP_Build=Build"
})
public class GenericJavaProjectWizardIterator implements WizardDescriptor.InstantiatingIterator<WizardDescriptor> {

    private final Panel[] panels = new Panel[] {
        new GenericJavaProjectPathPanel.PanelImpl(),
        new EditorPanelImpl(),
        new BuildPanelImpl()
    };
    
    private WizardDescriptor wizard;
    private int idx;

    @Override
    public Set instantiate() throws IOException {
        GenericJavaProjectSettings settings = GenericJavaProjectSettings.get(wizard);
        FileObject projectDirectory = FileUtil.toFileObject(new File(settings.getProjectPath()));
        Preferences prefs = GenericJavaProject.getRootPreferences(projectDirectory);
        prefs.putBoolean(GenericJavaProject.KEY_IS_PROJECT, true);
        settings.getBuildConfig().save(GenericJavaProject.getBuildPreferences(projectDirectory));
        prefs.put(GenericJavaProject.KEY_PROJECT_CONFIGURATION, settings.getEditorConfigPath());
        return Collections.singleton(projectDirectory);
    }

    @Override
    public void initialize(WizardDescriptor wizard) {
        this.wizard = wizard;
        this.idx = 0;
        int i = 0;
        String[] captions = new String[]{
            Bundle.CAP_ProjectPath(),
            Bundle.CAP_Editor(),
            Bundle.CAP_Build()
        };
        for (Panel p : panels) {
            ((JComponent) p.getComponent()).putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i++);
            ((JComponent) p.getComponent()).putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, captions);
        }
    }

    @Override
    public void uninitialize(WizardDescriptor wizard) {
        this.wizard = null;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return panels[idx];
    }

    @Override
    public String name() {
        return "TODO - wizard name";
    }

    @Override
    public boolean hasNext() {
        return idx < panels.length - 1;
    }

    @Override
    public boolean hasPrevious() {
        return idx > 0;
    }

    @Override
    public void nextPanel() {
        idx++;
    }

    @Override
    public void previousPanel() {
        idx--;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
    
    public static final class GenericJavaProjectSettings {
        public static GenericJavaProjectSettings get(WizardDescriptor desc) {
            GenericJavaProjectSettings setting = (GenericJavaProjectSettings) desc.getProperty(GenericJavaProjectSettings.class.getName());
            if (setting == null) {
                desc.putProperty(GenericJavaProjectSettings.class.getName(), setting = new GenericJavaProjectSettings());
            }
            return setting;
        }
        private String projectPath;
        private String editorConfigPath;
        private BuildConfiguration buildConfig;

        public String getProjectPath() {
            return projectPath;
        }

        public void setProjectPath(String projectPath) {
            this.projectPath = projectPath;
        }

        public String getEditorConfigPath() {
            if (editorConfigPath != null) {
                return editorConfigPath;
            }
            return "";
        }

        public void setEditorConfigPath(String editorConfigPath) {
            this.editorConfigPath = editorConfigPath;
        }

        public BuildConfiguration getBuildConfig() {
            if (buildConfig != null) {
                return buildConfig;
            }
            return new BuildConfiguration("", Collections.emptyMap());
        }

        public void setBuildConfig(BuildConfiguration buildConfig) {
            this.buildConfig = buildConfig;
        }
        
    }

    private static class EditorPanelImpl implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

        private Editor panel;

        @Override
        public Editor getComponent() {
            if (panel == null) {
                panel = new Editor();
            }
            return panel;
        }

        @Override
        public HelpCtx getHelp() {
            return HelpCtx.DEFAULT_HELP;
        }

        @Override
        public void readSettings(WizardDescriptor settings) {
            getComponent().load(GenericJavaProjectSettings.get(settings).getEditorConfigPath());
        }

        @Override
        public void storeSettings(WizardDescriptor settings) {
            if (panel != null) {
                GenericJavaProjectSettings.get(settings).setEditorConfigPath(panel.save());
            }
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void addChangeListener(ChangeListener l) {
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
        }

        @Override
        public boolean isFinishPanel() {
            return true;
        }
        
    }

    private static class BuildPanelImpl implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

        private Build panel;

        @Override
        public Build getComponent() {
            if (panel == null) {
                panel = new Build();
            }
            return panel;
        }

        @Override
        public HelpCtx getHelp() {
            return HelpCtx.DEFAULT_HELP;
        }

        @Override
        public void readSettings(WizardDescriptor settings) {
            getComponent().load(GenericJavaProjectSettings.get(settings).getBuildConfig());
        }

        @Override
        public void storeSettings(WizardDescriptor settings) {
            if (panel != null) {
                GenericJavaProjectSettings.get(settings).setBuildConfig(panel.save());
            }
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void addChangeListener(ChangeListener l) {
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
        }

        @Override
        public boolean isFinishPanel() {
            return true;
        }
        
    }
}
