import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisModdedBuildTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void capturesTheCompleteTargetConfiguration() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(temporary.newFolder()).build();
        IrisModdedBuild task = project.getTasks().create("verifyFabric", IrisModdedBuild.class);
        task.getLabel().set("Fabric 26.3");
        task.getWorkingDirectory().set(project.getLayout().getProjectDirectory());
        task.getCommandLine().set(List.of("gradlew", "assemble", "test"));
        task.getRequiredEntries().set(List.of("fabric.mod.json"));
        task.getForbiddenCoreClasses().set(Set.of("org/bukkit/Test"));
        task.getJavaRuntime().set(25);
        task.getArtifactFile().set(project.getLayout().getProjectDirectory().file("build/mod.jar"));
        task.getLogFile().set(project.getLayout().getProjectDirectory().file("build/reports/matrix/fabric.log"));

        assertEquals(Map.of(), task.getEnvironment().get());
        assertEquals(List.of("gradlew", "assemble", "test"), task.getCommandLine().get());
        assertEquals(List.of("fabric.mod.json"), task.getRequiredEntries().get());
        assertEquals(Set.of("org/bukkit/Test"), task.getForbiddenCoreClasses().get());
        assertEquals(Integer.valueOf(25), task.getJavaRuntime().get());
        assertEquals(Set.of(task.getArtifactFile().get().getAsFile()), task.getOutputs().getFiles().getFiles());
        assertFalse(task.getOutputs().getFiles().contains(task.getLogFile().get().getAsFile()));
    }

    @Test
    public void failureSummaryRetainsTheFinalLinesWithoutReadingAnEntireLog() throws Exception {
        Path log = temporary.newFile().toPath();
        StringBuilder content = new StringBuilder("discarded first line\n");
        for (int index = 0; index < 2000; index++) {
            content.append("task output ").append(index).append('\n');
        }
        content.append("final failure\n");
        Files.writeString(log, content);

        String tail = IrisModdedBuildAction.logTail(log);

        assertEquals(40, tail.lines().count());
        assertTrue(tail.endsWith("final failure"));
        assertFalse(tail.contains("discarded first line"));
        assertTrue(tail.length() <= 16 * 1024);
    }

    @Test
    public void failureSummaryAcceptsEmptyAndUnterminatedLogs() throws Exception {
        Path log = temporary.newFile().toPath();
        assertEquals("", IrisModdedBuildAction.logTail(log));
        Files.writeString(log, "first\r\nlast");
        assertEquals("first" + System.lineSeparator() + "last", IrisModdedBuildAction.logTail(log));
    }

    @Test
    public void failureSummaryBoundsAnUnbrokenFailureLine() throws Exception {
        Path log = temporary.newFile().toPath();
        Files.writeString(log, "x".repeat(100_000) + "final failure");

        String tail = IrisModdedBuildAction.logTail(log);

        assertEquals(16 * 1024, tail.length());
        assertTrue(tail.endsWith("final failure"));
    }
}
