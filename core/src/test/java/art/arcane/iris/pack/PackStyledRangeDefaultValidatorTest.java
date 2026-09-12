package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackStyledRangeDefaultValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void flagsEmptyDensityStyleAsSharedDefault() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/x.json", "{\"objects\":[{\"place\":[\"o\"],\"densityStyle\":{}}]}");

        PackStyledRangeDefaultValidator.Validation validation = PackStyledRangeDefaultValidator.validate(pack);

        assertEquals(1, validation.errors().size());
        assertTrue(validation.errors().get(0).startsWith(
                "Biome 'x' objects[0].densityStyle omits both min and max"));
        assertTrue(validation.warnings().isEmpty());
    }

    @Test
    public void acceptsExplicitDensityStyle() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/x.json",
                "{\"objects\":[{\"place\":[\"o\"],\"densityStyle\":{\"min\":1,\"max\":3,\"style\":{\"style\":\"SIMPLEX\"}}}]}");

        PackStyledRangeDefaultValidator.Validation validation = PackStyledRangeDefaultValidator.validate(pack);

        assertTrue(validation.errors().isEmpty());
        assertTrue(validation.warnings().isEmpty());
    }

    @Test
    public void warnsOnPartialDensityStyle() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/x.json", "{\"objects\":[{\"place\":[\"o\"],\"densityStyle\":{\"min\":1}}]}");

        PackStyledRangeDefaultValidator.Validation validation = PackStyledRangeDefaultValidator.validate(pack);

        assertTrue(validation.errors().isEmpty());
        assertEquals(1, validation.warnings().size());
        assertTrue(validation.warnings().get(0).contains("omits max"));
        assertTrue(validation.warnings().get(0).contains("32"));
    }

    @Test
    public void resolvesSnippetReferences() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/x.json", "{\"objects\":[{\"place\":[\"o\"],\"densityStyle\":\"snippet/style-range/empty\"}]}");
        write(pack, "snippet/style-range/empty.json", "{}");

        PackStyledRangeDefaultValidator.Validation validation = PackStyledRangeDefaultValidator.validate(pack);

        assertEquals(1, validation.errors().size());
        assertTrue(validation.errors().get(0).contains("via snippet 'snippet/style-range/empty'"));
    }

    @Test
    public void flagsEmptyCaveDensityThreshold() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"caveProfile\":{\"densityThreshold\":{}}}");

        PackStyledRangeDefaultValidator.Validation validation = PackStyledRangeDefaultValidator.validate(pack);

        assertEquals(1, validation.errors().size());
        assertTrue(validation.errors().get(0).startsWith(
                "Dimension 'main' caveProfile.densityThreshold omits both min and max"));
    }

    @Test
    public void emptyDensityStyleBlocksFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"objects\":[{\"place\":[\"o\"],\"densityStyle\":{}}]}");

        PackValidationResult result = PackValidator.validate(pack);

        assertFalse(result.isLoadable());
    }

    private void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
