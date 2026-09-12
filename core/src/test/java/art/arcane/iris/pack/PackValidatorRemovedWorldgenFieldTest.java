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

public class PackValidatorRemovedWorldgenFieldTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsFluidBodiesAcrossEveryFormerHost() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"fluidBodies\":{}}");
        write(pack, "regions/nested/region.json", "{\"fluidBodies\":null}");
        write(pack, "biomes/nested/biome.json", "{\"fluidBodies\":{\"lakes\":[]}}");

        assertEquals(List.of(
                "Dimension 'main' declares removed field 'fluidBodies'. Remove it because fluid-body generation is not supported.",
                "Region 'nested/region' declares removed field 'fluidBodies'. Remove it because fluid-body generation is not supported.",
                "Biome 'nested/biome' declares removed field 'fluidBodies'. Remove it because fluid-body generation is not supported."
        ), PackObjectSurfaceValidator.validateRemovedWorldgenFields(pack));
    }

    @Test
    public void removedFluidBodiesBlocksFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"fluidBodies\":{}}");

        PackValidationResult result = PackValidator.validate(pack);

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Biome 'biome' declares removed field 'fluidBodies'. Remove it because fluid-body generation is not supported."));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
