/*
 *  Copyright 2018 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.idea.devserver.ui;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.teavm.idea.devserver.TeaVMDevServerConfiguration;

public class TeaVMDevServerSettingsPanel extends JPanel {
    private final JrePathEditor jrePathEditor;

    private final LabeledComponent<ModuleDescriptionsComboBox> moduleField;
    private final ConfigurationModuleSelector moduleSelector;

    private LabeledComponent<EditorTextFieldWithBrowseButton> mainClassField;

    private LabeledComponent<JFormattedTextField> portField;
    private LabeledComponent<JTextField> pathToFileField;
    private LabeledComponent<JTextField> fileNameField;
    private LabeledComponent<JCheckBox> indicatorField;
    private LabeledComponent<JCheckBox> autoReloadField;
    private LabeledComponent<JFormattedTextField> maxHeapField;

    public TeaVMDevServerSettingsPanel(Project project) {
        moduleField = LabeledComponent.create(new ModuleDescriptionsComboBox(), "Use classpath of module:");
        moduleSelector = new ConfigurationModuleSelector(project, moduleField.getComponent());

        JavaCodeFragment.VisibilityChecker visibilityChecker = (declaration, place) -> {
            if (declaration instanceof PsiClass) {
                PsiClass cls = (PsiClass) declaration;
                if (ConfigurationUtil.MAIN_CLASS.value(cls) && PsiMethodUtil.findMainMethod(cls) != null
                        || place.getParent() != null && moduleSelector.findClass(cls.getQualifiedName()) != null) {
                    return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
                }
            }
            return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
        };
        mainClassField = LabeledComponent.create(new EditorTextFieldWithBrowseButton(project, true, visibilityChecker),
                "Main class:");
        mainClassField.getComponent().setButtonEnabled(true);

        jrePathEditor = new JrePathEditor(DefaultJreSelector.fromSourceRootsDependencies(moduleField.getComponent(),
                mainClassField.getComponent()));

        portField = LabeledComponent.create(new JFormattedTextField(new DecimalFormat("#0")), "Port:");
        fileNameField = LabeledComponent.create(new JTextField(), "File name:");
        pathToFileField = LabeledComponent.create(new JTextField(), "Path to file:");
        indicatorField = LabeledComponent.create(new JCheckBox(), "Display indicator on a web page:");
        autoReloadField = LabeledComponent.create(new JCheckBox(), "Reload page automatically:");
        maxHeapField = LabeledComponent.create(new JFormattedTextField(new DecimalFormat("#0")), "Server heap limit:");

        initLayout();
    }

    private void initLayout() {
        setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets.left = 10;
        constraints.insets.right = 10;
        constraints.insets.top = 4;
        constraints.insets.bottom = 4;

        add(mainClassField, constraints);
        add(moduleField, constraints);
        add(jrePathEditor.getComponent(), constraints);

        add(portField, constraints);
        add(fileNameField, constraints);
        add(pathToFileField, constraints);
        add(indicatorField, constraints);
        add(autoReloadField, constraints);
        add(maxHeapField, constraints);
    }

    public void load(TeaVMDevServerConfiguration configuration) {
        mainClassField.getComponent().setText(configuration.getMainClass());
        moduleSelector.reset(configuration);
        jrePathEditor.setPathOrName(configuration.getJdkPath(), false);
        fileNameField.getComponent().setText(configuration.getFileName());
        pathToFileField.getComponent().setText(configuration.getPathToFile());
        indicatorField.getComponent().setSelected(configuration.isIndicator());
        autoReloadField.getComponent().setSelected(configuration.isAutomaticallyReloaded());
        maxHeapField.getComponent().setText(Integer.toString(configuration.getMaxHeap()));
        portField.getComponent().setText(Integer.toString(configuration.getPort()));
    }

    public void save(TeaVMDevServerConfiguration configuration) {
        configuration.setMainClass(mainClassField.getComponent().getText());
        moduleSelector.applyTo(configuration);
        configuration.setJdkPath(jrePathEditor.getJrePathOrName());
        configuration.setFileName(fileNameField.getComponent().getText());
        configuration.setPathToFile(pathToFileField.getComponent().getText());
        configuration.setIndicator(indicatorField.getComponent().isSelected());
        configuration.setAutomaticallyReloaded(autoReloadField.getComponent().isSelected());
        configuration.setMaxHeap(Integer.parseInt(maxHeapField.getComponent().getText()));
        configuration.setPort(Integer.parseInt(portField.getComponent().getText()));
    }
}
