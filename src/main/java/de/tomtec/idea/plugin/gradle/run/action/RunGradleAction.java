/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.tomtec.idea.plugin.gradle.run.action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.externalSystem.action.ExternalSystemAction;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import de.tomtec.idea.plugin.gradle.run.service.RunGradleTaskHistoryService;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsConverter;
import org.jetbrains.plugins.gradle.service.task.ExecuteGradleTaskHistoryService;
import org.jetbrains.plugins.gradle.service.task.GradleRunTaskDialog;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Action to invoke the gradle run task dialog depending on the module context.
 * This closely mimics the functionality of the {@link org.jetbrains.plugins.gradle.action.GradleExecuteTaskAction}
 * but uses the deprecated pop-up dialog instead of the run-anywhere functionality.
 *
 * @see org.jetbrains.plugins.gradle.action.GradleExecuteTaskAction
 * @author rdoboni
 * @author Vladislav.Soroka
 */
public class RunGradleAction extends ExternalSystemAction
{

  @Override
  protected boolean isVisible(@NotNull AnActionEvent e)
  {
    if (!super.isVisible(e)) return false;

    return isGradleModule(getModule(e));
  }

  @Override
  public void update(@NotNull final AnActionEvent e)
  {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(isVisible(e));
    presentation.setEnabled(true);
  }


  @Override
  public void actionPerformed(@NotNull final AnActionEvent event)
  {
    final Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    RunGradleTaskHistoryService historyService = RunGradleTaskHistoryService.getInstance(project);

    RunGradleTaskDialog dialog = new RunGradleTaskDialog(project, historyService.getHistory());

    dialog.setWorkDirectory(obtainWorkingDirectory(event));

    if (StringUtil.isEmptyOrSpaces(historyService.getCanceledCommand()))
    {
      if (historyService.getHistory().size() > 0)
      {
        dialog.setCommandLine(historyService.getHistory().get(0));
      }
    }
    else {
      dialog.setCommandLine(historyService.getCanceledCommand());
    }

    if (!dialog.showAndGet()) {
      historyService.setCanceledCommand(dialog.getCommandLine());
      return;
    }

    historyService.setCanceledCommand(null);

    String fullCommandLine = dialog.getCommandLine().trim();
    String workDirectory = dialog.getWorkDirectory();

    historyService.addCommand(fullCommandLine, workDirectory);

    runGradle(project, fullCommandLine, workDirectory, getExecutor());

  }

  private void runGradle(@NotNull Project project,
                         @NotNull String fullCommandLine,
                         @NotNull String workDirectory,
                         @Nullable Executor executor) {
    final ExternalTaskExecutionInfo taskExecutionInfo;

    try {
      taskExecutionInfo = buildTaskInfo(workDirectory, fullCommandLine, executor); //TODO: add support for debug executor
    }
    catch (CommandLineArgumentException ex)
    {
      final NotificationData notificationData = new NotificationData(
        "<b>Command-line arguments cannot be parsed</b>",
        "<i>" + fullCommandLine + "</i> \n" + ex.getMessage(),
        NotificationCategory.WARNING, NotificationSource.TASK_EXECUTION
      );
      notificationData.setBalloonNotification(true);
      ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notificationData);
      return;
    }

    ExternalSystemUtil.runTask(taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), project, GradleConstants.SYSTEM_ID);

    RunnerAndConfigurationSettings configuration =
      ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(taskExecutionInfo.getSettings(),
        project, GradleConstants.SYSTEM_ID);

    if (configuration == null) return;

    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings existingConfiguration = runManager.findConfigurationByName(configuration.getName());
    if(existingConfiguration == null) {
      runManager.setTemporaryConfiguration(configuration);
    } else {
      runManager.setSelectedConfiguration(existingConfiguration);
    }
  }

  private String obtainWorkingDirectory(@NotNull final AnActionEvent event)
  {
    final Module currentModule = getModule(event);

    String projectPath = ExternalSystemApiUtil.getExternalProjectPath(currentModule);
    return projectPath == null ? "" : projectPath;
  }

  private static ExternalTaskExecutionInfo buildTaskInfo(@NotNull String projectPath, @NotNull String fullCommandLine,
                                                         @Nullable Executor executor)
    throws CommandLineArgumentException
  {
    CommandLineParser gradleCmdParser = new CommandLineParser();

    GradleCommandLineOptionsConverter commandLineConverter = new GradleCommandLineOptionsConverter();
    commandLineConverter.configure(gradleCmdParser);
    ParsedCommandLine parsedCommandLine = gradleCmdParser.parse(ParametersListUtil.parse(fullCommandLine, true, true));

    final Map<String, List<String>> optionsMap =
      commandLineConverter.convert(parsedCommandLine, new HashMap<>());

    final List<String> systemProperties = optionsMap.remove("system-prop");
    final String vmOptions = systemProperties == null ? "" : StringUtil.join(systemProperties, entry -> "-D" + entry, " ");

    final String scriptParameters = StringUtil.join(optionsMap.entrySet(), entry -> {
      final List<String> values = entry.getValue();
      final String longOptionName = entry.getKey();
      if (values != null && !values.isEmpty()) {
        return StringUtil.join(values, entry1 -> "--" + longOptionName + ' ' + entry1, " ");
      }
      else {
        return "--" + longOptionName;
      }
    }, " ");

    final List<String> tasks = parsedCommandLine.getExtraArguments();

    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExternalProjectPath(projectPath);
    settings.setTaskNames(tasks);
    settings.setScriptParameters(scriptParameters);
    settings.setVmOptions(vmOptions);
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
    return new ExternalTaskExecutionInfo(settings, executor == null ? DefaultRunExecutor.EXECUTOR_ID : executor.getId());
  }

  private boolean isGradleModule(Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;

    return ExternalSystemApiUtil.getExternalProjectId(module) != null;
  }

  @Nullable
  private Module getModule(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    return module != null ? module : e.getData(LangDataKeys.MODULE_CONTEXT);
  }

  private Executor getExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
    //ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)
  }

}
