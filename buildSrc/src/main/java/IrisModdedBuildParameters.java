import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.workers.WorkParameters;

public interface IrisModdedBuildParameters extends WorkParameters {
    Property<String> getLabel();

    DirectoryProperty getWorkingDirectory();

    ListProperty<String> getCommandLine();

    MapProperty<String, String> getEnvironment();

    ListProperty<String> getRequiredEntries();

    SetProperty<String> getForbiddenCoreClasses();

    Property<Integer> getJavaRuntime();

    RegularFileProperty getArtifactFile();

    RegularFileProperty getLogFile();
}
