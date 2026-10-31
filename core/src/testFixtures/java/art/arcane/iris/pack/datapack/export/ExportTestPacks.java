package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.CompatTestPlatform;
import org.junit.rules.ExternalResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.doReturn;

public final class ExportTestPacks {
    public static final String DIMENSION = "main";
    private static final List<String> BLOCKS = List.of("minecraft:air", "minecraft:stone", "minecraft:water", "minecraft:grass_block");
    private static final String UNSUPPORTED_RUNTIME_VERSION = "0.9";

    private ExportTestPacks() {
    }

    public static File basic(File pack) throws IOException {
        return withDimension(pack, "{\"regions\":[\"region\"]}");
    }

    public static File withDimension(File pack, String dimensionJson) throws IOException {
        CompatTestPlatform.write(pack, "dimensions/" + DIMENSION + ".json", dimensionJson);
        CompatTestPlatform.write(pack, "regions/region.json", "{\"landBiomes\":[\"Forest Land\",\"mapped\"],\"seaBiomes\":[\"ocean\"]}");
        CompatTestPlatform.write(pack, "biomes/Forest Land.json", "{\"name\":\"Forest Land\"}");
        CompatTestPlatform.write(pack, "biomes/mapped.json", "{\"name\":\"Mapped\"}");
        CompatTestPlatform.write(pack, "biomes/ocean.json", "{\"name\":\"Ocean\"}");
        return pack;
    }

    public static File withImageMaps(File pack) throws IOException {
        withDimension(pack, """
                {
                  "regions": ["region"],
                  "worldBoundary": {"center": {"x": 0, "z": 0}, "size": 16},
                  "imageMaps": [{"key": "terrain", "map": "terrain", "application": "TERRAIN_HEIGHT"}]
                }
                """);
        CompatTestPlatform.write(pack, "biomes/mapped.json", "{\"name\":\"Mapped\",\"childStyle\":{\"imageMap\":\"terrain\"}}");
        CompatTestPlatform.write(pack, "image-maps/terrain.json", """
                {"source": "terrain", "type": "GRAYSCALE_HEIGHT", "origin": {"x": -8, "z": -8}, "outOfBounds": "CLAMP"}
                """);
        File images = new File(pack, "images");
        if (!images.isDirectory() && !images.mkdirs()) {
            throw new IOException("Could not create " + images);
        }
        if (!ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY), "png", new File(images, "terrain.png"))) {
            throw new IOException("No PNG writer available");
        }
        return pack;
    }

    public static PackCompileContext context(IrisData data, DataVersion target) {
        return contextBuilder(data, target).build();
    }

    public static PackCompileContext.Builder contextBuilder(IrisData data, DataVersion target) {
        IrisDimension dimension = data.getDimensionLoader().load(DIMENSION, false);
        if (dimension == null) {
            throw new IllegalStateException("Test pack has no dimension '" + DIMENSION + "'");
        }
        return PackCompileContext.builder()
                .data(data)
                .dimension(dimension)
                .capabilities(ExportCapabilities.forTarget(target))
                .namespace(WorldgenExportRequest.DEFAULT_NAMESPACE)
                .mode(WorldgenExportMode.PRESET);
    }

    public static final class BoundPlatform extends ExternalResource {
        private IrisSettings previousSettings;
        private CompatTestPlatform platform;
        private Path folder;

        @Override
        protected void before() throws IOException {
            previousSettings = IrisSettings.settings;
            IrisSettings.settings = new IrisSettings();
            folder = Files.createTempDirectory("iris-export-platform");
            platform = CompatTestPlatform.bind(folder.toFile(), BLOCKS);
            doReturn(UNSUPPORTED_RUNTIME_VERSION).when(IrisPlatforms.get()).minecraftVersion();
        }

        @Override
        protected void after() {
            platform.unbind();
            IrisSettings.settings = previousSettings;
            try (Stream<Path> paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not remove test platform folder " + folder, exception);
            }
        }
    }
}
