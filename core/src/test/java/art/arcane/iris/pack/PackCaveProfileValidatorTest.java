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

public class PackCaveProfileValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsLegacyWaterFieldsAcrossEveryCaveProfileLocation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"caveProfile\":{\"allowWater\":false}}");
        write(pack, "regions/nested/region.json", "{\"caveProfile\":{\"waterMinDepthBelowSurface\":20}}");
        write(pack, "biomes/nested/biome.json", "{\"caveProfile\":{\"waterRequiresFloor\":true}}");
        write(pack, "snippet/cave-profile/nested/profile.json", "{\"allowWater\":true}");

        assertEquals(List.of(
                "Dimension 'main' caveProfile.allowWater was removed; use caveProfile.allowFluid. Cave aquifers use the dimension fluidPalette, which defaults to water.",
                "Region 'nested/region' caveProfile.waterMinDepthBelowSurface was removed; use caveProfile.fluidMinDepthBelowSurface. Cave aquifers use the dimension fluidPalette, which defaults to water.",
                "Biome 'nested/biome' caveProfile.waterRequiresFloor was removed; use caveProfile.fluidRequiresFloor. Cave aquifers use the dimension fluidPalette, which defaults to water.",
                "Cave-profile snippet 'nested/profile' allowWater was removed; use allowFluid. Cave aquifers use the dimension fluidPalette, which defaults to water."
        ), PackCaveProfileValidator.validateLegacyFields(pack));
    }

    @Test
    public void acceptsGenericFluidFields() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"caveProfile\":{\"allowFluid\":true,"
                + "\"fluidMinDepthBelowSurface\":20,\"fluidRequiresFloor\":true}}");
        write(pack, "snippet/cave-profile/profile.json", "{\"allowFluid\":true,"
                + "\"fluidMinDepthBelowSurface\":20,\"fluidRequiresFloor\":true}");

        assertTrue(PackCaveProfileValidator.validateLegacyFields(pack).isEmpty());
    }

    @Test
    public void legacyFieldBlocksFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"caveProfile\":{\"allowWater\":false}}");

        PackValidationResult result = PackValidator.validate(pack);

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Biome 'biome' caveProfile.allowWater was removed; use caveProfile.allowFluid. Cave aquifers use the dimension fluidPalette, which defaults to water."));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
