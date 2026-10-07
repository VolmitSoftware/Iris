package art.arcane.iris.pack;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisLogging;
import org.mockito.MockedStatic;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class PackValidatorReadOnlyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validationDoesNotMoveOrRewritePackFiles() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        write(pack, "generators/unused.json", "{\"name\":\"Unused\"}");
        Map<String, byte[]> before = snapshot(pack.toPath());

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.isLoadable());
        assertEquals(before.keySet(), snapshot(pack.toPath()).keySet());
        Map<String, byte[]> after = snapshot(pack.toPath());
        for (Map.Entry<String, byte[]> entry : before.entrySet()) {
            assertArrayEquals(entry.getValue(), after.get(entry.getKey()));
        }
        assertFalse(new File(pack, ".iris-trash").exists());
    }

    @Test
    public void contentGateInitializationFailureBlocksPackPublication() throws Exception {
        File pack = temporaryFolder.newFolder("failed-gate");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        assertTrue(PackValidator.validateForPackaging(pack).isLoadable());
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.minecraftVersion()).thenReturn("26.3");
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             MockedStatic<IrisData> data = mockStatic(IrisData.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            platforms.when(IrisPlatforms::isBound).thenReturn(true);
            platforms.when(IrisPlatforms::get).thenReturn(platform);
            data.when(() -> IrisData.openRuntime(pack)).thenThrow(new IllegalStateException("Registry unavailable"));

            PackValidationResult result = PackValidator.validate(pack);

            assertFalse(result.isLoadable());
            assertTrue(result.getBlockingErrors().toString().contains("Registry unavailable"));
        }
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private Map<String, byte[]> snapshot(Path root) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                files.put(root.relativize(path).toString(), Files.readAllBytes(path));
            }
        }
        return files;
    }
}
