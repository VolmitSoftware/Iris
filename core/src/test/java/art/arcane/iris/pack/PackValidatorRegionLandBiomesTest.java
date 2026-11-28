package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorRegionLandBiomesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void regionWithoutLandBiomesIsBlockingAndNamesTheRegion() throws IOException {
        List<String> errors = validate("{\"seaBiomes\":[\"ocean\"],\"shoreBiomes\":[\"ocean\"]}");

        assertEquals(List.of("Region 'island' has no landBiomes; every region needs at least one land biome."), errors);
    }

    @Test
    public void regionWithOnlyMissingLandBiomesIsBlocking() throws IOException {
        List<String> errors = validate("{\"landBiomes\":[\"absent\"],\"seaBiomes\":[\"ocean\"]}");

        assertEquals(List.of("Region 'island' has no landBiomes; every region needs at least one land biome."), errors);
    }

    @Test
    public void regionWithLandButNoSeaOrShoreBiomesIsValid() throws IOException {
        List<String> errors = validate("{\"landBiomes\":[\"ocean\"]}");

        assertTrue(errors.toString(), errors.isEmpty());
    }

    private List<String> validate(String region) throws IOException {
        File pack = temporaryFolder.newFolder();
        File dimensions = new File(pack, PackValidator.DIMENSIONS_FOLDER);
        File regions = new File(pack, "regions");
        File biomes = new File(pack, "biomes");
        assertTrue(dimensions.mkdirs() && regions.mkdirs() && biomes.mkdirs());
        File dimension = new File(dimensions, "main.json");
        Files.writeString(dimension.toPath(), "{\"regions\":[\"island\"]}");
        Files.writeString(new File(regions, "island.json").toPath(), region);
        Files.writeString(new File(biomes, "ocean.json").toPath(), "{}");
        List<String> errors = new ArrayList<>();
        PackDimensionValidator.validateDimensions(pack, new File[]{dimension}, errors, new ArrayList<>());
        return errors;
    }
}
