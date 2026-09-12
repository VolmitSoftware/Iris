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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorStructureTransformTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsUnsupportedTransformsAcrossEveryStructureHost() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"test\"],\"rotation\":{}}]}");
        write(pack, "regions/nested/region.json", "{\"structures\":[{\"structures\":[\"test\"],\"translate\":{}}]}");
        write(pack, "biomes/nested/biome.json", "{\"structures\":[{\"structures\":[\"test\"],\"scale\":{}}]}");

        List<String> errors = PackObjectSurfaceValidator.validateUnsupportedStructureTransforms(pack);

        assertEquals(List.of(
                "Dimension 'main' structures[0] declares unsupported field 'rotation'. Structure placement transforms are not supported; remove the field.",
                "Region 'nested/region' structures[0] declares unsupported field 'translate'. Structure placement transforms are not supported; remove the field.",
                "Biome 'nested/biome' structures[0] declares unsupported field 'scale'. Structure placement transforms are not supported; remove the field."
        ), errors);
    }

    @Test
    public void ignoresTransformsOutsideStructurePlacementArrays() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/biome.json", "{\"objects\":[{\"rotation\":{},\"translate\":{},\"scale\":{}}],\"structures\":[{\"structures\":[\"test\"]}]}");

        assertTrue(PackObjectSurfaceValidator.validateUnsupportedStructureTransforms(pack).isEmpty());
    }

    @Test
    public void unsupportedTransformBlocksFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"],\"structures\":[{\"structures\":[\"test\"],\"rotation\":null}]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");

        PackValidationResult result = PackValidator.validate(pack);

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Dimension 'main' structures[0] declares unsupported field 'rotation'. Structure placement transforms are not supported; remove the field."));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
