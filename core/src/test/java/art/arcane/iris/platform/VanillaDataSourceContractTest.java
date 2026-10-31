package art.arcane.iris.platform;

import art.arcane.iris.spi.VanillaDataSource;
import art.arcane.iris.testsupport.FakeVanillaDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public final class VanillaDataSourceContractTest {
    private static final String PLAINS = "data/minecraft/worldgen/biome/plains.json";
    private static final String BIOME_TAGS = "data/minecraft/tags/worldgen/biome";
    private static final String OVERWORLD_TAG = BIOME_TAGS + "/is_overworld.json";
    private static final String SWAMP_HUT_TAG = BIOME_TAGS + "/has_structure/swamp_hut.json";
    private static final Map<String, String> FILES = Map.of(
            PLAINS, "{\"temperature\":0.8}",
            OVERWORLD_TAG, "{\"values\":[\"minecraft:plains\"]}",
            SWAMP_HUT_TAG, "{\"values\":[\"minecraft:swamp\"]}",
            "data/minecraft/tags/worldgen/biome_marker.json", "{}",
            "data/minecraft/worldgen/noise/temperature.json", "{}");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Parameterized.Parameter
    public Kind kind;

    private URLClassLoader loader;
    private VanillaDataSource source;

    @Parameterized.Parameters(name = "{0}")
    public static List<Kind> kinds() {
        return List.of(Kind.values());
    }

    @Before
    public void createSource() throws Exception {
        source = switch (kind) {
            case DIRECTORY -> classpath(VanillaDataRoots.directory(folder.newFolder("root").toPath(), FILES));
            case JAR -> classpath(VanillaDataRoots.jar(folder.getRoot().toPath().resolve("vanilla.jar"), FILES));
            case FAKE -> FakeVanillaDataSource.of(FILES);
        };
    }

    @After
    public void closeLoader() throws IOException {
        if (loader != null) {
            loader.close();
        }
    }

    @Test
    public void readsFileBytes() {
        assertArrayEquals(FILES.get(PLAINS).getBytes(StandardCharsets.UTF_8), source.read(PLAINS).orElseThrow());
    }

    @Test
    public void missingFilesAndDirectoriesReadEmpty() {
        assertTrue(source.read("data/minecraft/worldgen/biome/missing.json").isEmpty());
        assertTrue(source.read("data/minecraft/worldgen/biome").isEmpty());
    }

    @Test
    public void listsFilesBeneathPrefixInPathOrder() {
        assertEquals(List.of(SWAMP_HUT_TAG, OVERWORLD_TAG), source.list(BIOME_TAGS));
        assertEquals(List.of(SWAMP_HUT_TAG, OVERWORLD_TAG), source.list(BIOME_TAGS + "/"));
    }

    @Test
    public void missingDirectoriesAndFilesListNothing() {
        assertEquals(List.of(), source.list("data/minecraft/tags/worldgen/structure"));
        assertEquals(List.of(), source.list(PLAINS));
    }

    @Test
    public void rejectsPathsOutsideTheDataTree() {
        List<String> invalid = List.of("", "data", "data/", "/data/minecraft/x.json", "assets/minecraft/x.json",
                "../data/minecraft/x.json", "data/../x.json", "data/./x.json", "data/minecraft//x.json",
                "data\\minecraft\\x.json");
        for (String path : invalid) {
            assertThrows(path, IllegalArgumentException.class, () -> source.read(path));
            assertThrows(path, IllegalArgumentException.class, () -> source.list(path));
        }
    }

    private VanillaDataSource classpath(URLClassLoader created) throws ClassNotFoundException {
        loader = created;
        Class<?> anchor = VanillaDataRoots.anchor(created);
        return new ClasspathVanillaDataSource(() -> anchor);
    }

    public enum Kind {
        DIRECTORY,
        JAR,
        FAKE
    }
}
