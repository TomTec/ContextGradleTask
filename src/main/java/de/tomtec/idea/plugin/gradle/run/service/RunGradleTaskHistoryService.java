package de.tomtec.idea.plugin.gradle.run.service;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.task.ExecuteGradleTaskHistoryService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * original class taken from org.jetbrains.plugins.gradle.service.task.RunGradleTaskHistoryService
 * @author Vladislav.Soroka
 */
@State(
        name = "runGradleTaskHistory",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class RunGradleTaskHistoryService implements PersistentStateComponent<String[]> {
    private static final int MAX_HISTORY_LENGTH = 20;
    private final LinkedList<String> myHistory = new LinkedList<>();
    private String myWorkDirectory = "";
    private String myCanceledCommand;

    public static RunGradleTaskHistoryService getInstance(@NotNull Project project) {
        return project.getService(RunGradleTaskHistoryService.class);
    }

    @Nullable
    public String getCanceledCommand() {
        return myCanceledCommand;
    }

    public void setCanceledCommand(@Nullable String canceledCommand) {
        myCanceledCommand = canceledCommand;
    }

    public void addCommand(@NotNull String command, @NotNull String projectPath) {
        myWorkDirectory = projectPath.trim();

        command = command.trim();

        if (command.length() == 0) return;

        myHistory.remove(command);
        myHistory.addFirst(command);

        while (myHistory.size() > MAX_HISTORY_LENGTH) {
            myHistory.removeLast();
        }
    }

    public List<String> getHistory() {
        return new ArrayList<>(myHistory);
    }

    @NotNull
    public String getWorkDirectory() {
        return myWorkDirectory;
    }

    @Nullable
    @Override
    public String[] getState() {
        String[] res = new String[myHistory.size() + 1];
        res[0] = myWorkDirectory;

        int i = 1;
        for (String goal : myHistory) {
            res[i++] = goal;
        }

        return res;
    }

    @Override
    public void loadState(@NotNull String[] state) {
        if (state.length == 0) {
            myWorkDirectory = "";
            myHistory.clear();
        }
        else {
            myWorkDirectory = state[0];
            myHistory.addAll(Arrays.asList(state).subList(1, state.length));
        }
    }
}
