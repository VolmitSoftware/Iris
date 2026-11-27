package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PackValidationIoRunTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void secondValidationSeesFileEditedAfterFirst() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        assertTrue(PackValidator.validate(pack).isLoadable());

        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"fluidBodies\":{}}");
        PackValidationResult edited = PackValidator.validate(pack);

        assertFalse(edited.isLoadable());
        assertTrue(edited.getBlockingErrors().toString().contains("Biome 'biome' declares removed field 'fluidBodies'"));
    }

    @Test
    public void parsedJsonIsSharedOnlyWithinOneRun() throws Exception {
        File pack = temporaryFolder.newFolder("shared");
        File biome = write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");

        JSONObject[] withinRun = PackValidationIo.withinValidationRun(
                () -> new JSONObject[]{PackValidationIo.readJson(biome), PackValidationIo.readJson(biome)});

        assertSame(withinRun[0], withinRun[1]);
        assertNotSame(PackValidationIo.readJson(biome), PackValidationIo.readJson(biome));
    }

    @Test
    public void listingWithinRunIsACopyPerCaller() throws Exception {
        File pack = temporaryFolder.newFolder("listing");
        write(pack, "biomes/a.json", "{}");
        write(pack, "biomes/b.json", "{}");
        File biomes = new File(pack, "biomes");

        int secondListingSize = PackValidationIo.withinValidationRun(() -> {
            List<File> first = PackValidationIo.listJsonRecursive(biomes);
            first.clear();
            return PackValidationIo.listJsonRecursive(biomes).size();
        });

        assertEquals(2, secondListingSize);
    }

    private File write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toFile();
    }
}
