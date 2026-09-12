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

public class PackValidatorSurfaceSupportTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsSurfaceSupportSettingsInsideTheirRanges() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json",
                "{\"requireObjectSurfaceSupport\":true,\"objectSurfaceSupportBuffer\":4}");
        write(pack, "biomes/plains.json",
                "{\"objects\":[{\"place\":[\"a\"],\"surfaceSupportBuffer\":16,\"surfaceSupportDepth\":1,"
                        + "\"requireSurfaceSupport\":false}]}");

        assertEquals(List.of(), PackObjectSurfaceValidator.validateObjectSurfaceSupport(pack));
    }

    @Test
    public void rejectsOutOfRangeAndMistypedSurfaceSupportSettings() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json",
                "{\"requireObjectSurfaceSupport\":\"yes\",\"objectSurfaceSupportBuffer\":17}");
        write(pack, "biomes/plains.json",
                "{\"objects\":[{\"place\":[\"a\"],\"surfaceSupportBuffer\":-1,\"surfaceSupportDepth\":0,"
                        + "\"requireSurfaceSupport\":1}]}");

        List<String> errors = PackObjectSurfaceValidator.validateObjectSurfaceSupport(pack);

        assertTrue(errors.contains("Dimension 'main'.objectSurfaceSupportBuffer must be at most 16."));
        assertTrue(errors.contains("Dimension 'main'.requireObjectSurfaceSupport must be a boolean."));
        assertTrue(errors.contains("Biome 'plains'.objects[0].surfaceSupportBuffer must be at least 0."));
        assertTrue(errors.contains("Biome 'plains'.objects[0].surfaceSupportDepth must be at least 1."));
        assertTrue(errors.contains("Biome 'plains'.objects[0].requireSurfaceSupport must be a boolean."));
    }

    @Test
    public void rejectsRemovedSurfaceOpeningClearanceField() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "regions/forests.json",
                "{\"objects\":[{\"place\":[\"a\"],\"surfaceOpeningClearance\":3}]}");

        assertEquals(List.of(
                "Region 'forests'.objects[0] declares removed field 'surfaceOpeningClearance'. "
                        + "Use surfaceSupportBuffer instead."
        ), PackObjectSurfaceValidator.validateObjectSurfaceSupport(pack));
    }

    @Test
    public void surfaceSupportErrorsBlockFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json",
                "{\"regions\":[\"region\"],\"objectSurfaceSupportBuffer\":99}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.getBlockingErrors().contains(
                "Dimension 'main'.objectSurfaceSupportBuffer must be at most 16."));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
