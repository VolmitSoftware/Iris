package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorDimensionStackTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsCanonicalTopToBottomStack() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "spacer": 24,
                    "blend": {
                      "amplitude": 8,
                      "style": {"style": "SIMPLEX", "zoom": 128}
                    }
                  }
                }
                """);

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void rejectsMissingBlendStyleSnippet() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "blend": {"style": "snippet/style/missing"}
                  }
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "references missing style snippet 'snippet/style/missing'"));
    }

    @Test
    public void rejectsWrongBlendSnippetType() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "blend": {"style": "snippet/generator/stack-blend"}
                  }
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "blend.style must be an object or style snippet reference"));
    }

    @Test
    public void rejectsMissingLayerAndWrongLowestDimension() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["missing", "sky"],
                    "spacer": 24
                  }
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "dimensions[0] references missing dimension 'missing'"));
        assertTrue(contains(result, "dimensions must end with its host dimension key 'main'"));
    }

    @Test
    public void rejectsUpperDimensionAndUnsafeNumericValues() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "upperDimension": "sky",
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "spacer": 24.5,
                    "blend": {
                      "amplitude": 257,
                      "style": {}
                    }
                  }
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "dimensionStack cannot be used with upperDimension"));
        assertTrue(contains(result, "spacer must be an integer between 0 and 256"));
        assertTrue(contains(result, "amplitude must be an integer between 0 and 256"));
    }

    @Test
    public void rejectsShortStackAndMalformedBlendStyle() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["main"],
                    "blend": {"style": 42}
                  }
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "dimensions must contain at least two dimension keys"));
        assertTrue(contains(result, "blend.style must be an object or style snippet reference"));
    }

    @Test
    public void acceptsBlendStyleSnippetReference() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "blend": {"style": "snippet/style/stack-blend"}
                  }
                }
                """, "{\"style\":\"SIMPLEX\",\"zoom\":128}");

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void rejectsMalformedBlendStyleSnippet() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "blend": {"style": "snippet/style/stack-blend"}
                  }
                }
                """, "{");

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "references invalid style snippet 'snippet/style/stack-blend'"));
    }

    @Test
    public void acceptsNestedDimensionKeys() throws Exception {
        File pack = temporaryFolder.newFolder("nested-pack");
        write(pack, "dimensions/root/main.json", """
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky/clouds", "root/main"]
                  }
                }
                """);
        write(pack, "dimensions/sky/clouds.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");

        PackValidationResult result = PackValidator.validateForPackaging(pack);

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void acceptsEmptyBlendWithDefaults() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "main"],
                    "blend": {}
                  }
                }
                """);

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void acceptsRepeatedNonHostDimensions() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["sky", "sky", "main"]
                  }
                }
                """);

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void rejectsDuplicateHostDimension() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": {
                    "dimensions": ["main", "sky", "main"]
                  }
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "host dimension key 'main' exactly once"));
    }

    @Test
    public void rejectsExplicitNullConfiguration() throws Exception {
        PackValidationResult result = validate("""
                {
                  "regions": ["region"],
                  "dimensionStack": null
                }
                """);

        assertFalse(result.isLoadable());
        assertTrue(contains(result, "dimensionStack must be an object"));
    }

    private PackValidationResult validate(String mainDimension) throws Exception {
        return validate(mainDimension, null);
    }

    private PackValidationResult validate(String mainDimension, String styleSnippet) throws Exception {
        File pack = temporaryFolder.newFolder("pack-" + System.nanoTime());
        write(pack, "dimensions/main.json", mainDimension);
        write(pack, "dimensions/sky.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        if (styleSnippet != null) {
            write(pack, "snippet/style/stack-blend.json", styleSnippet);
        }
        return PackValidator.validateForPackaging(pack);
    }

    private static boolean contains(PackValidationResult result, String fragment) {
        return result.getBlockingErrors().stream().anyMatch(error -> error.contains(fragment));
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
