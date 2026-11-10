package art.arcane.iris.core.project;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackHotloadGuardTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File write(String relativePath, String content) throws IOException {
        File file = new File(temporaryFolder.getRoot(), relativePath);
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    public void allowsAHotloadWhenEveryTouchedFileParses() throws IOException {
        PackHotloadGuard guard = new PackHotloadGuard(temporaryFolder.getRoot());

        assertTrue(guard.allowsHotload(List.of(write("biomes/plains.json", "{}"))));
    }

    @Test
    public void skipsAHotloadWhileATouchedFileIsBroken() throws IOException {
        PackHotloadGuard guard = new PackHotloadGuard(temporaryFolder.getRoot());

        assertFalse(guard.allowsHotload(List.of(write("biomes/plains.json", "{"))));
    }

    @Test
    public void keepsSkippingOtherSavesUntilTheBrokenFileIsFixed() throws IOException {
        PackHotloadGuard guard = new PackHotloadGuard(temporaryFolder.getRoot());
        write("biomes/plains.json", "{");
        guard.allowsHotload(List.of(new File(temporaryFolder.getRoot(), "biomes/plains.json")));
        File region = write("regions/hills.json", "{}");

        assertFalse(guard.allowsHotload(List.of(region)));

        write("biomes/plains.json", "{}");
        assertTrue(guard.allowsHotload(List.of(region)));
    }

    @Test
    public void touchedFilesAreTheCreatedOnesFollowedByTheChangedOnes() {
        File created = new File("created.json");
        File changed = new File("changed.json");

        assertEquals(List.of(created, changed), PackHotloadGuard.touched(List.of(created), List.of(changed)));
    }
}
