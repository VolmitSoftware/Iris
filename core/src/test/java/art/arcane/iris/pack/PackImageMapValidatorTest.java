package art.arcane.iris.pack;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PackImageMapValidatorTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;

    @Before
    public void isolatePlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void acceptsCompiledTypedBindingsThroughPackValidator() throws Exception {
        File pack = createPack("valid", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 16},
                  "imageMaps": [
                    {
                      "key": "terrain",
                      "map": "terrain",
                      "application": "TERRAIN_HEIGHT",
                      "masks": [{"map": "weight", "operation": "MULTIPLY"}]
                    },
                    {"key": "weight", "map": "weight", "application": "MASK"}
                  ]
                }
                """);
        writeGray(pack, "terrain", 16, 16);
        writeGray(pack, "weight", 16, 16);
        write(pack, "image-maps/terrain.json", """
                {
                  "source": "terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "origin": {"x": -8, "z": -8},
                  "outOfBounds": "ERROR"
                }
                """);
        write(pack, "image-maps/weight.json", """
                {
                  "source": "weight",
                  "type": "GRAYSCALE_MASK",
                  "origin": {"x": -8, "z": -8},
                  "outOfBounds": "CLAMP"
                }
                """);

        PackValidationResult result = PackValidator.validateForDatapackBootstrap(pack);

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
        assertFalse(result.getWarnings().toString(), contains(result.getWarnings(), "image-map"));
    }

    @Test
    public void enforcesErrorCoverageAndWarnsForRecoverablePolicies() throws Exception {
        File noBoundary = createPack("no-boundary", dimensionWithTerrain(null));
        writeGray(noBoundary, "terrain", 2, 2);
        write(noBoundary, "image-maps/terrain.json", heightMap("ERROR"));

        PackImageMapValidator.Validation missing = validate(noBoundary, false);

        assertTrue(missing.errors().toString(), contains(missing.errors(),
                "outOfBounds=ERROR and requires a configured worldBoundary"));

        File uncoveredError = createPack("uncovered-error", dimensionWithTerrain(16));
        writeGray(uncoveredError, "terrain", 2, 2);
        write(uncoveredError, "image-maps/terrain.json", heightMap("ERROR"));

        PackImageMapValidator.Validation errorCoverage = validate(uncoveredError, false);

        assertTrue(errorCoverage.errors().toString(), contains(errorCoverage.errors(),
                "source footprint does not cover the configured worldBoundary"));

        File uncoveredClamp = createPack("uncovered-clamp", dimensionWithTerrain(16));
        writeGray(uncoveredClamp, "terrain", 2, 2);
        write(uncoveredClamp, "image-maps/terrain.json", heightMap("CLAMP"));

        PackImageMapValidator.Validation clampCoverage = validate(uncoveredClamp, false);

        assertTrue(clampCoverage.errors().toString(), clampCoverage.errors().isEmpty());
        assertTrue(clampCoverage.warnings().toString(), contains(clampCoverage.warnings(),
                "source footprint does not cover the configured worldBoundary"));
    }

    @Test
    public void reportsBindingReferenceAndApplicationFailuresDeterministically() throws Exception {
        File pack = createPack("bindings", """
                {
                  "regions": ["region"],
                  "imageMaps": [
                    {
                      "key": "duplicate",
                      "map": "height",
                      "application": "TERRAIN_HEIGHT",
                      "masks": [
                        {"map": "duplicate", "operation": "MULTIPLY"},
                        {"map": "missing-mask", "operation": "MULTIPLY"}
                      ]
                    },
                    {"key": "duplicate", "map": "height-two", "application": "TERRAIN_HEIGHT"},
                    {"key": "biome", "map": "height", "application": "BIOME"},
                    {"key": "missing-map", "map": "absent", "application": "CUSTOM"},
                    {
                      "key": "mask",
                      "map": "mask",
                      "application": "MASK",
                      "masks": [{"map": "mask", "operation": "MULTIPLY"}]
                    }
                  ]
                }
                """);
        writeGray(pack, "height", 2, 2);
        writeGray(pack, "height-two", 2, 2);
        writeGray(pack, "mask", 2, 2);
        write(pack, "image-maps/height.json", heightMap("CLAMP"));
        write(pack, "image-maps/height-two.json", """
                {"source": "height-two", "type": "GRAYSCALE_HEIGHT", "outOfBounds": "CLAMP"}
                """);
        write(pack, "image-maps/mask.json", """
                {"source": "mask", "type": "GRAYSCALE_MASK", "outOfBounds": "CLAMP"}
                """);

        PackImageMapValidator.Validation first = validate(pack, false);
        PackImageMapValidator.Validation second = validate(pack, false);

        assertEquals(first, second);
        assertTrue(first.errors().toString(), contains(first.errors(), "duplicate image-map key 'duplicate'"));
        assertTrue(first.errors().toString(), contains(first.errors(),
                "more than one TERRAIN_HEIGHT image-map binding"));
        assertTrue(first.errors().toString(), contains(first.errors(), "incompatible with BIOME"));
        assertTrue(first.errors().toString(), contains(first.errors(),
                "references missing image-map resource 'absent'"));
        assertTrue(first.errors().toString(), contains(first.errors(), "which is not a MASK binding"));
        assertTrue(first.errors().toString(), contains(first.errors(),
                "references missing MASK binding 'missing-mask'"));
        assertTrue(first.errors().toString(), contains(first.errors(),
                "is a MASK binding and cannot reference additional masks"));
    }

    @Test
    public void validatesEveryFirstClassResourceBeforeItIsBound() throws Exception {
        File pack = createPack("resources", "{\"regions\":[\"region\"]}");
        writeGray(pack, "grayscale", 2, 2);
        writeRgb(pack, "not-png", 1, 1, "jpeg");
        Path corrupt = pack.toPath().resolve("images/corrupt.png");
        Files.createDirectories(corrupt.getParent());
        Files.write(corrupt, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02
        });
        write(pack, "image-maps/wrong-type.json", """
                {"source": "grayscale", "type": "RGB_HEIGHT", "outOfBounds": "CLAMP"}
                """);
        write(pack, "image-maps/wrong-format.json", """
                {"source": "not-png", "type": "RGB_HEIGHT", "outOfBounds": "CLAMP"}
                """);
        write(pack, "image-maps/missing-source.json", """
                {"source": "absent", "type": "GRAYSCALE_HEIGHT", "outOfBounds": "CLAMP"}
                """);
        write(pack, "image-maps/corrupt-source.json", """
                {"source": "corrupt", "type": "GRAYSCALE_HEIGHT", "outOfBounds": "CLAMP"}
                """);

        PackImageMapValidator.Validation validation = validate(pack, false);

        assertTrue(validation.errors().toString(), contains(validation.errors(),
                "RGB_HEIGHT requires an RGB or RGBA PNG"));
        assertTrue(validation.errors().toString(), contains(validation.errors(), "is not a PNG file"));
        assertTrue(validation.errors().toString(), contains(validation.errors(),
                "references missing PNG source 'absent'"));
        assertTrue(validation.errors().toString(), contains(validation.errors(), "is corrupt or unsupported"));
    }

    @Test
    public void validatesLegendFilesMappedRegionRolesAndLiveSurfaceBlocks() throws Exception {
        File pack = createPack("legends", """
                {
                  "regions": ["region"],
                  "imageMaps": [
                    {"key": "biome", "map": "biome-map", "application": "BIOME"},
                    {"key": "region", "map": "region-map", "application": "REGION"},
                    {"key": "surface", "map": "surface-map", "application": "SURFACE_BLOCK"}
                  ]
                }
                """);
        writeRgb(pack, "biome", 1, 1, "png");
        writeRgb(pack, "region-map", 1, 1, "png");
        writeRgb(pack, "surface", 1, 1, "png");
        write(pack, "biomes/mapped-biome.json", "{\"name\":\"Mapped\"}");
        write(pack, "regions/mapped-region.json", "{\"landBiomes\":[\"mapped-biome\"]}");
        write(pack, "image-maps/biome-map.json", colorMap("biome", "mapped-biome"));
        write(pack, "image-maps/region-map.json", colorMap("region-map", "mapped-region"));
        write(pack, "image-maps/surface-map.json", colorMap("surface", "minecraft:not_real"));
        PlatformRegistries registries = mock(PlatformRegistries.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.blockKeys()).thenReturn(List.of("minecraft:stone"));
        when(registries.blockOrNull("minecraft:not_real", false)).thenReturn(null);
        when(registries.blockStateProperties()).thenReturn(Map.of());
        IrisPlatforms.bind(platform);

        PackImageMapValidator.Validation validation = validate(pack, true);

        assertFalse(validation.errors().toString(), contains(validation.errors(),
                "biome target 'mapped-biome' must occur"));
        assertTrue(validation.errors().toString(), contains(validation.errors(),
                "unknown surface block target 'minecraft:not_real'"));
    }

    @Test
    public void validatesReachableGeneratorStyleMapReferences() throws Exception {
        File missingPack = createPack("generator-style-missing", "{\"regions\":[\"region\"]}");
        write(missingPack, "biomes/biome.json", """
                {"name":"Biome","generators":[{"generator":"mapped","min":0,"max":1}]}
                """);
        write(missingPack, "generators/mapped.json", """
                {"composite":[{"style":{"imageMap":"absent"}}]}
                """);

        PackImageMapValidator.Validation missing = validate(missingPack, false);

        assertTrue(missing.errors().toString(), contains(missing.errors(),
                "generator style references missing image-map resource 'absent'"));

        File colorPack = createPack("generator-style-color", """
                {"regions":["region"],"regionStyle":{"imageMap":"colors"}}
                """);
        writeRgb(colorPack, "colors", 1, 1, "png");
        write(colorPack, "image-maps/colors.json", colorMap("colors", "biome"));

        PackImageMapValidator.Validation color = validate(colorPack, false);

        assertTrue(color.errors().toString(), contains(color.errors(),
                "must use a scalar map type, not COLOR_MAP"));

        File errorPack = createPack("generator-style-error", """
                {"regions":["region"],"regionStyle":{"imageMap":"height"}}
                """);
        writeGray(errorPack, "height", 4, 4);
        write(errorPack, "image-maps/height.json", """
                {"source":"height","type":"GRAYSCALE_HEIGHT","outOfBounds":"ERROR"}
                """);

        PackImageMapValidator.Validation error = validate(errorPack, false);

        assertTrue(error.errors().toString(), contains(error.errors(),
                "cannot use outOfBounds=ERROR because its transformed sampling domain cannot be proven finite"));
    }

    @Test
    public void validatesUpperDimensionCoverageAgainstParentBoundary() throws Exception {
        File pack = createPack("upper-parent-boundary", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 16},
                  "upperDimension": "upper"
                }
                """);
        write(pack, "dimensions/upper.json", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 2},
                  "imageMaps": [
                    {"key": "terrain", "map": "upper-terrain", "application": "TERRAIN_HEIGHT"}
                  ]
                }
                """);
        writeGray(pack, "upper-terrain", 4, 4);
        write(pack, "image-maps/upper-terrain.json", """
                {
                  "source": "upper-terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "origin": {"x": -2, "z": -2},
                  "outOfBounds": "ERROR"
                }
                """);

        PackImageMapValidator.Validation validation = validate(pack, false);

        assertTrue(validation.errors().toString(), contains(validation.errors(),
                "Dimension 'main' upper dimension 'upper' image-map 'terrain' source footprint does not cover"));
        assertFalse(validation.errors().toString(), contains(validation.errors(),
                "Dimension 'upper' image-map 'terrain' source footprint does not cover"));
    }

    @Test
    public void validatesStackLayerCoverageAgainstHostBoundary() throws Exception {
        File pack = createPack("stack-host-boundary", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 16},
                  "dimensionStack": {
                    "dimensions": ["upper", "upper", "main"],
                    "spacer": 24
                  }
                }
                """);
        write(pack, "dimensions/upper.json", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 2},
                  "imageMaps": [
                    {"key": "terrain", "map": "upper-terrain", "application": "TERRAIN_HEIGHT"}
                  ]
                }
                """);
        writeGray(pack, "upper-terrain", 4, 4);
        write(pack, "image-maps/upper-terrain.json", """
                {
                  "source": "upper-terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "origin": {"x": -2, "z": -2},
                  "outOfBounds": "ERROR"
                }
                """);

        PackImageMapValidator.Validation validation = validate(pack, false);

        assertTrue(validation.errors().toString(), contains(validation.errors(),
                "Dimension 'main' dimension stack layer 'upper' image-map 'terrain' source footprint does not cover"));
        assertEquals(1L, validation.errors().stream()
                .filter(error -> error.contains("Dimension 'main' dimension stack layer 'upper'"))
                .count());
        assertFalse(validation.errors().toString(), contains(validation.errors(),
                "Dimension 'upper' image-map 'terrain' source footprint does not cover"));
    }

    @Test
    public void acceptsUpperDimensionWithoutStandaloneBoundaryWhenParentIsCovered() throws Exception {
        File pack = createPack("upper-parent-only-boundary", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 4},
                  "upperDimension": "upper"
                }
                """);
        write(pack, "dimensions/upper.json", """
                {
                  "regions": ["region"],
                  "imageMaps": [
                    {"key": "terrain", "map": "upper-terrain", "application": "TERRAIN_HEIGHT"}
                  ]
                }
                """);
        writeGray(pack, "upper-terrain", 4, 4);
        write(pack, "image-maps/upper-terrain.json", """
                {
                  "source": "upper-terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "origin": {"x": -2, "z": -2},
                  "outOfBounds": "ERROR"
                }
                """);

        PackImageMapValidator.Validation validation = validate(pack, false);

        assertFalse(validation.errors().toString(), contains(validation.errors(),
                "requires a configured worldBoundary"));
        assertFalse(validation.errors().toString(), contains(validation.errors(),
                "source footprint does not cover"));
    }

    @Test
    public void validatesDeclaredUpperBoundaryAlongsideParentBoundary() throws Exception {
        File pack = createPack("upper-own-and-parent-boundary", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 4},
                  "upperDimension": "upper"
                }
                """);
        write(pack, "dimensions/upper.json", """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 16},
                  "imageMaps": [
                    {"key": "terrain", "map": "upper-terrain", "application": "TERRAIN_HEIGHT"}
                  ]
                }
                """);
        writeGray(pack, "upper-terrain", 4, 4);
        write(pack, "image-maps/upper-terrain.json", """
                {
                  "source": "upper-terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "origin": {"x": -2, "z": -2},
                  "outOfBounds": "ERROR"
                }
                """);

        PackImageMapValidator.Validation validation = validate(pack, false);

        assertTrue(validation.errors().toString(), contains(validation.errors(),
                "Dimension 'upper' image-map 'terrain' source footprint does not cover"));
        assertFalse(validation.errors().toString(), contains(validation.errors(),
                "Dimension 'main' upper dimension 'upper' image-map 'terrain' source footprint does not cover"));
    }

    @Test
    public void enforcesTerrainHeightRangeAfterOffsetAndClamp() throws Exception {
        File unclamped = createPack("height-unclamped", """
                {
                  "regions": ["region"],
                  "dimensionHeight": {"min": 0, "max": 64},
                  "imageMaps": [
                    {"key": "terrain", "map": "terrain", "application": "TERRAIN_HEIGHT"}
                  ]
                }
                """);
        writeGray(unclamped, "terrain", 2, 2);
        write(unclamped, "image-maps/terrain.json", """
                {
                  "source": "terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "minimumHeight": 0,
                  "maximumHeight": 64,
                  "verticalOffset": 10,
                  "clamp": false,
                  "outOfBounds": "CLAMP"
                }
                """);

        PackImageMapValidator.Validation outside = validate(unclamped, false);

        assertTrue(outside.errors().toString(), contains(outside.errors(),
                "produces world Y 10.0..74.0 after verticalOffset and clamp"));

        File clamped = createPack("height-clamped", """
                {
                  "regions": ["region"],
                  "dimensionHeight": {"min": 0, "max": 64},
                  "imageMaps": [
                    {"key": "terrain", "map": "terrain", "application": "TERRAIN_HEIGHT"}
                  ]
                }
                """);
        writeGray(clamped, "terrain", 2, 2);
        write(clamped, "image-maps/terrain.json", """
                {
                  "source": "terrain",
                  "type": "GRAYSCALE_HEIGHT",
                  "minimumHeight": 0,
                  "maximumHeight": 64,
                  "verticalOffset": 10,
                  "clamp": true,
                  "outOfBounds": "CLAMP"
                }
                """);

        PackImageMapValidator.Validation inside = validate(clamped, false);

        assertFalse(inside.errors().toString(), contains(inside.errors(), "produces world Y"));
    }

    private PackImageMapValidator.Validation validate(File pack, boolean liveRegistries) {
        File dimensions = new File(pack, "dimensions");
        File[] dimensionFiles = dimensions.listFiles((File file) -> file.isFile()
                && file.getName().endsWith(".json"));
        assertTrue(dimensionFiles != null && dimensionFiles.length > 0);
        return PackImageMapValidator.validate(pack, dimensionFiles, liveRegistries);
    }

    private File createPack(String name, String dimension) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", dimension);
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        return pack;
    }

    private String dimensionWithTerrain(Integer boundarySize) {
        String boundary = boundarySize == null
                ? ""
                : ",\"worldBoundary\":{\"center\":{\"x\":0,\"z\":0},\"size\":" + boundarySize + "}";
        return "{\"regions\":[\"region\"]" + boundary
                + ",\"imageMaps\":[{\"key\":\"terrain\",\"map\":\"terrain\","
                + "\"application\":\"TERRAIN_HEIGHT\"}]}";
    }

    private String heightMap(String outOfBounds) {
        return "{\"source\":\"terrain\",\"type\":\"GRAYSCALE_HEIGHT\","
                + "\"outOfBounds\":\"" + outOfBounds + "\"}";
    }

    private String colorMap(String source, String target) {
        return "{\"source\":\"" + source + "\",\"type\":\"COLOR_MAP\","
                + "\"outOfBounds\":\"CLAMP\",\"unknownColor\":\"IGNORE\","
                + "\"colors\":{\"#FF0000\":\"" + target + "\"}}";
    }

    private boolean contains(List<String> messages, String fragment) {
        return messages.stream().anyMatch((String message) -> message.contains(fragment));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void writeGray(File pack, String key, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        File file = imageFile(pack, key);
        assertTrue(ImageIO.write(image, "png", file));
    }

    private void writeRgb(File pack, String key, int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, 0xFFFF0000);
            }
        }
        File file = imageFile(pack, key);
        assertTrue(ImageIO.write(image, format, file));
    }

    private File imageFile(File pack, String key) throws Exception {
        File images = new File(pack, "images");
        Files.createDirectories(images.toPath());
        return new File(images, key + ".png");
    }
}
