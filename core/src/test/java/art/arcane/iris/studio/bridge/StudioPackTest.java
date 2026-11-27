package art.arcane.iris.studio.bridge;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class StudioPackTest {
    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    @Test
    public void aPackIsNamedAndVersionedByItsDimensionFile() throws IOException {
        dimension("natureplanet", "{\"name\": \"Etopia\", \"version\": 100}");

        assertEquals(List.of(new StudioPack("natureplanet", "Etopia", 100)), StudioPack.inWorkspace(workspace.getRoot()));
    }

    @Test
    public void commentsInTheDimensionFileAreRead() throws IOException {
        dimension("overworld", "{\n  // the pack's name\n  \"name\": \"Overworld\",\n  \"version\": 3\n}");

        assertEquals(List.of(new StudioPack("overworld", "Overworld", 3)), StudioPack.inWorkspace(workspace.getRoot()));
    }

    @Test
    public void aDimensionWithoutNameOrVersionFallsBackToItsFolderAndTheFirstVersion() throws IOException {
        dimension("bare", "{}");

        assertEquals(List.of(new StudioPack("bare", "bare", 1)), StudioPack.inWorkspace(workspace.getRoot()));
    }

    @Test
    public void foldersWithoutTheirDimensionFileAreNoPacks() throws IOException {
        workspace.newFolder("notes");
        dimension("broken", "{\"name\": ");
        dimension("zeta", "{\"name\": \"zeta\"}");
        dimension("alpha", "{\"name\": \"Alpha\"}");

        assertEquals(List.of("Alpha", "zeta"), StudioPack.inWorkspace(workspace.getRoot()).stream().map(StudioPack::name).toList());
    }

    private void dimension(String pack, String json) throws IOException {
        File dimensions = new File(workspace.getRoot(), pack + "/dimensions");
        Files.createDirectories(dimensions.toPath());
        Files.writeString(new File(dimensions, pack + ".json").toPath(), json, StandardCharsets.UTF_8);
    }
}
