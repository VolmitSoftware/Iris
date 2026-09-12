package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructurePackageClosureLimitsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void boundedCollectionStopsAtResourceLimit() throws Exception {
        Path root = temporaryFolder.newFolder("resources").toPath();
        write(root, "structures/village.json", "{\"startPool\":\"village/start\"}");
        write(root, "jigsaw-pools/village/start.json",
                "{\"pieces\":[{\"piece\":\"village/house\"}]}");
        write(root, "jigsaw-pieces/village/house.json",
                "{\"object\":\"village/house\",\"connectors\":[]}");
        write(root, "objects/village/house.iob", "object");

        StructurePackageClosure closure = StructurePackageClosure.collect(
                root.toFile(),
                List.of("village"),
                new StructurePackageClosure.Limits(2, 1024));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().stream().anyMatch(error ->
                error.contains("exceeds 2 resources")));
    }

    @Test
    public void boundedCollectionRejectsOversizedJsonBeforeParsing() throws Exception {
        Path root = temporaryFolder.newFolder("json").toPath();
        write(root, "structures/village.json", "{\"startPool\":\"village/start\"}");

        StructurePackageClosure closure = StructurePackageClosure.collect(
                root.toFile(),
                List.of("village"),
                new StructurePackageClosure.Limits(100, 8));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().stream().anyMatch(error ->
                error.contains("exceeds 8 bytes")));
    }

    private void write(Path root, String relativePath, String content) throws Exception {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
