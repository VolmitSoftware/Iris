package art.arcane.iris.core.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackTerrain3DValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsInlineProfilesAndReferencedProfileAndStyleSnippets() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "biomes/mountains.json", """
                {"terrain3D":{"amplitude":64,"verticalScale":16,"densityStyle":{"style":"SIMPLEX"}}}
                """);
        write(pack, "biomes/cliffs.json", """
                {"terrain3D":"snippet/terrain-3d/cliffs"}
                """);
        write(pack, "snippet/terrain-3d/cliffs.json", """
                {"$schema":"../../schema.json","crackDepth":32,"densityStyle":"snippet/style/terrain"}
                """);
        write(pack, "snippet/style/terrain.json", """
                {"style":"SIMPLEX","fracture":"snippet/style/warp"}
                """);
        write(pack, "snippet/style/warp.json", """
                {"style":"SIMPLEX","zoom":2,"multiplier":12}
                """);

        assertTrue(PackTerrain3DValidator.validate(pack).toString(), PackTerrain3DValidator.validate(pack).isEmpty());
    }

    @Test
    public void acceptsOmissionAndNullWithoutEnablingProfiles() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "biomes/plain.json", "{}");
        write(pack, "biomes/ocean.json", "{\"terrain3D\":null}");
        write(pack, "biomes/disabled.json", "{\"terrain3D\":{\"enabled\":false}}");

        assertTrue(PackTerrain3DValidator.validate(pack).isEmpty());
    }

    @Test
    public void rejectsUnknownFieldsAndNonBooleanEnableFlags() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "biomes/unknown.json", "{\"terrain3D\":{\"amplitdue\":64}}");
        write(pack, "biomes/boolean.json", "{\"terrain3D\":{\"enabled\":\"true\"}}");
        write(pack, "biomes/array.json", "{\"terrain3D\":[]}");
        write(pack, "biomes/numeric.json", "{\"terrain3D\":{\"amplitude\":\"64\"}}");

        List<String> errors = PackTerrain3DValidator.validate(pack);
        assertTrue(errors.stream().anyMatch(error -> error.contains("amplitdue is not a terrain3D field")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("enabled must be a JSON boolean")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("terrain3D must be an object or snippet reference")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("amplitude must be a finite JSON number")));
    }

    @Test
    public void appliesModelBoundsToInlineAndDisabledSnippetProfiles() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "biomes/amplitude.json", "{\"terrain3D\":{\"amplitude\":129}}");
        write(pack, "snippet/terrain-3d/disabled.json", "{\"enabled\":false,\"verticalScale\":0}");
        write(pack, "biomes/nonfinite.json", "{\"terrain3D\":{\"crackDepth\":1e999}}");
        write(pack, "biomes/seed.json", "{\"terrain3D\":{\"seed\":1.5}}");
        write(pack, "biomes/huge-seed.json", "{\"terrain3D\":{\"seed\":9223372036854775808}}");

        List<String> errors = PackTerrain3DValidator.validate(pack);
        assertTrue(errors.stream().anyMatch(error -> error.contains("amplitude must be finite and between")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("verticalScale must be finite and between")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("crackDepth must be a finite JSON number")));
        assertTrue(errors.stream().filter(error -> error.contains("seed must be an integer")).count() == 2);
    }

    @Test
    public void rejectsUnavailableAndUnreadableCurrentSnippets() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "biomes/missing.json", "{\"terrain3D\":\"snippet/terrain-3d/missing\"}");
        write(pack, "biomes/style.json", "{\"terrain3D\":{\"densityStyle\":\"snippet/style/missing\"}}");
        write(pack, "snippet/terrain-3d/broken.json", "{\"amplitude\":");
        write(pack, "biomes/path.json", "{\"terrain3D\":\"snippet/terrain-3d/../../outside\"}");

        List<String> errors = PackTerrain3DValidator.validate(pack);
        assertTrue(errors.stream().anyMatch(error -> error.contains("unavailable terrain-3d snippet")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("unavailable style snippet")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("contains unreadable JSON")));
    }

    @Test
    public void rejectsMalformedNestedStylesAndCyclicStyleReferences() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "biomes/shape.json", "{\"terrain3D\":{\"densityStyle\":true}}");
        write(pack, "biomes/style.json", "{\"terrain3D\":{\"densityStyle\":{\"style\":\"UNKNOWN\"}}}");
        write(pack, "biomes/nested.json", """
                {"terrain3D":{"densityStyle":{"fracture":{"style":"SIMPLEX","zoom":0,"wrong":1}}}}
                """);
        write(pack, "biomes/cycle.json", "{\"terrain3D\":{\"densityStyle\":\"snippet/style/cycle\"}}");
        write(pack, "snippet/style/cycle.json", "{\"fracture\":\"snippet/style/cycle\"}");

        List<String> errors = PackTerrain3DValidator.validate(pack);
        assertTrue(errors.stream().anyMatch(error -> error.contains("densityStyle must be an object or snippet reference")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("is not a known noise style")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("fracture.zoom must be between")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("wrong is not a generator style field")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("maximum style nesting depth")));
    }

    @Test
    public void invalidTerrainProfileBlocksFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"terrain3D\":{\"amplitude\":129}}");

        PackValidationResult result = PackValidator.validate(pack);
        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().stream()
                .anyMatch(error -> error.contains("terrain3D.amplitude must be finite and between")));
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
