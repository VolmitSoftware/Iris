package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorSpawnerEntityTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsNestedEntitiesReferencedByBothSpawnLists() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "spawners/frozen/passive.json", """
                {
                  "spawns": [{"entity": "standard/passive/cod"}],
                  "initialSpawns": [{"entity": "standard/passive/camel"}]
                }
                """);
        write(pack, "entities/standard/passive/cod.json", "{\"type\":\"COD\",\"surface\":\"WATER\"}");
        write(pack, "entities/standard/passive/camel.json", "{\"type\":\"CAMEL\"}");

        List<String> errors = validate(pack);

        assertTrue(errors.isEmpty());
    }

    @Test
    public void rejectsMalformedContainersAndEntries() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "spawners/broken.json", """
                {
                  "spawns": null,
                  "initialSpawns": [7, {}, {"entity": 9}, {"entity": " "}]
                }
                """);

        List<String> errors = validate(pack);

        assertEquals(5, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("spawns must be an array")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("non-object entry")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("without an entity reference")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("must be a string")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("blank entity reference")));
    }

    @Test
    public void rejectsMissingAndMalformedReferencedEntities() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "spawners/broken.json", """
                {
                  "spawns": [
                    {"entity": "standard/hostile/missing"},
                    {"entity": "standard/hostile/broken"}
                  ]
                }
                """);
        write(pack, "entities/standard/hostile/broken.json", "not-json");

        List<String> errors = validate(pack);

        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("references missing entity 'standard/hostile/missing'")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("references malformed entity 'standard/hostile/broken'")));
    }

    @Test
    public void rejectsMalformedSpawnerJson() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "spawners/broken.json", "not-json");

        List<String> errors = validate(pack);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Spawner 'broken' has invalid JSON"));
    }

    @Test
    public void rejectsEntityReferenceThatEscapesPack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "spawners/broken.json", "{\"spawns\":[{\"entity\":\"../outside\"}]}");
        write(pack, "outside.json", "{\"type\":\"COW\"}");

        List<String> errors = validate(pack);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("unsafe entity reference '../outside'"));
    }

    @Test
    public void ignoresMalformedEntityThatNoSpawnerReferences() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "spawners/passive.json", "{\"spawns\":[{\"entity\":\"standard/passive/cow\"}]}");
        write(pack, "entities/standard/passive/cow.json", "{\"type\":\"COW\"}");
        write(pack, "entities/unique/unused.json", "not-json");

        List<String> errors = validate(pack);

        assertTrue(errors.isEmpty());
    }

    private List<String> validate(File pack) {
        return PackSpawnValidator.validateSpawnerEntityReferences(
                new File(pack, "spawners"), new File(pack, "entities"));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
