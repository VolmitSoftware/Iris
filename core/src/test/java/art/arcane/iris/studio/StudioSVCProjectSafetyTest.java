package art.arcane.iris.studio;

import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class StudioSVCProjectSafetyTest {
    @Test
    public void createsLocalStarterWithoutARepositoryDownload() throws IOException {
        File workspace = temporaryFolder.newFolder("starter-workspace");

        StudioSVC.createStarterProject(workspace, "new_pack");

        Path project = workspace.toPath().resolve("new_pack");
        assertTrue(Files.isRegularFile(project.resolve("dimensions/new_pack.json")));
        assertTrue(Files.isRegularFile(project.resolve("regions/starter.json")));
        assertTrue(Files.isRegularFile(project.resolve("biomes/starter.json")));
        assertTrue(Files.isRegularFile(project.resolve("generators/flat.json")));
        assertTrue(Files.isRegularFile(project.resolve("new_pack.code-workspace")));
        PackValidationResult validation = PackValidator.validate(project.toFile());
        assertTrue(validation.getBlockingErrors().toString(), validation.isLoadable());
    }

    @Test
    public void starterCreationNeverDeletesAnExistingTarget() throws IOException {
        File workspace = temporaryFolder.newFolder("occupied-starter-workspace");
        Path occupied = workspace.toPath().resolve("occupied");
        Files.createDirectories(occupied);
        Path marker = occupied.resolve("marker.txt");
        Files.writeString(marker, "owned");

        assertThrows(IOException.class, () -> StudioSVC.createStarterProject(workspace, "occupied"));

        assertTrue(Files.isRegularFile(marker));
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void normalizesSafeProjectNames() throws IOException {
        assertEquals("my_pack-2", StudioSVC.normalizeProjectName("  MY_PACK-2  "));
    }

    @Test
    public void rejectsUnsafeProjectNames() {
        List<String> invalidNames = List.of(
                "",
                ".",
                "..",
                "../escape",
                "nested/project",
                "nested\\project",
                "pack.name",
                "pack name"
        );

        for (String invalidName : invalidNames) {
            assertThrows(IOException.class, () -> StudioSVC.normalizeProjectName(invalidName));
        }
    }

    @Test
    public void choosesAnUnusedDefaultProjectWithoutReplacingExistingProjects() throws IOException {
        File workspace = temporaryFolder.newFolder("packs");
        Files.createDirectory(workspace.toPath().resolve("studio"));
        Files.createDirectory(workspace.toPath().resolve("studio2"));

        assertEquals("studio3", StudioSVC.nextAvailableProjectName(workspace, "studio"));
    }

    @Test
    public void rejectsSymbolicLinkWorkspace() throws IOException {
        Path realWorkspace = temporaryFolder.newFolder("real-packs").toPath();
        Path linkedWorkspace = realWorkspace.getParent().resolve("linked-packs");
        try {
            Files.createSymbolicLink(linkedWorkspace, realWorkspace.getFileName());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        }

        assertThrows(IOException.class, () -> StudioSVC.requireSafeWorkspace(linkedWorkspace.toFile()));
    }
}
