package art.arcane.iris.core.pack;

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

public class PackValidatorHostFilesParseTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsEachDimensionRegionAndBiomeFileThatIsNotJson() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{");
        write(pack, "regions/nested/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}]}");
        write(pack, "biomes/nested/biome.json", "not json");

        List<String> errors = PackObjectSurfaceValidator.validateHostFilesParse(pack);

        assertEquals(2, errors.size());
        assertTrue(errors.get(0), errors.get(0).startsWith("Dimension 'main' has invalid JSON: "));
        assertTrue(errors.get(1), errors.get(1).startsWith("Biome 'nested/biome' has invalid JSON: "));
    }

    @Test
    public void structureTransformFieldsAreNoLongerSpecialCased() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"test\"],\"rotation\":{}}]}");

        assertTrue(PackObjectSurfaceValidator.validateHostFilesParse(pack).isEmpty());
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
