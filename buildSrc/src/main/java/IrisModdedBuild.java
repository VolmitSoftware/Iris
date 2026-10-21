import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.util.Map;

public abstract class IrisModdedBuild extends DefaultTask {
    public IrisModdedBuild() {
        getEnvironment().convention(Map.of());
    }

    @Input
    public abstract Property<String> getLabel();

    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    @Input
    public abstract ListProperty<String> getCommandLine();

    @Input
    public abstract MapProperty<String, String> getEnvironment();

    @Input
    public abstract ListProperty<String> getRequiredEntries();

    @Input
    public abstract SetProperty<String> getForbiddenCoreClasses();

    @Input
    public abstract Property<Integer> getJavaRuntime();

    @OutputFile
    public abstract RegularFileProperty getArtifactFile();

    @LocalState
    public abstract RegularFileProperty getLogFile();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void build() {
        getWorkerExecutor().noIsolation().submit(IrisModdedBuildAction.class, parameters -> {
            parameters.getLabel().set(getLabel());
            parameters.getWorkingDirectory().set(getWorkingDirectory());
            parameters.getCommandLine().set(getCommandLine());
            parameters.getEnvironment().set(getEnvironment());
            parameters.getRequiredEntries().set(getRequiredEntries());
            parameters.getForbiddenCoreClasses().set(getForbiddenCoreClasses());
            parameters.getJavaRuntime().set(getJavaRuntime());
            parameters.getArtifactFile().set(getArtifactFile());
            parameters.getLogFile().set(getLogFile());
        });
    }
}
