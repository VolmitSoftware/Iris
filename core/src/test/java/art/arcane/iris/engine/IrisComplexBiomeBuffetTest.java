package art.arcane.iris.engine;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.StudioMode;
import art.arcane.iris.engine.platform.studio.BiomeBuffetLayout;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.data.B;
import org.junit.ClassRule;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisComplexBiomeBuffetTest {
    @ClassRule public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisSettings previous;

    @Before
    public void prepareRuntime() {
        previous = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
    }

    @After
    public void releaseRuntime() {
        IrisPlatforms.unbind();
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previous;
    }

    @Test
    public void neighboringCellsUseTheirOwnRealBiomeStreamsWithoutChangingAuthoredFocus() throws Exception {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
        Path pack = fixture();
        IrisData data = IrisData.openRuntime(pack.toFile());
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            PlatformBlockState block = mock(PlatformBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getState(anyString())).thenReturn(block);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            IrisDimension dimension = data.getDimensionLoader().load("main");
            Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
            doReturn(data).when(engine).getData();
            doReturn(dimension).when(engine).getDimension();
            doReturn(data.getBiomeLoader().load("alpha")).when(engine).getFocus();
            doReturn(true).when(engine).isStudio();
            doReturn(384).when(engine).getHeight();
            doReturn(-64).when(engine).getMinHeight();
            doReturn(320).when(engine).getMaxHeight();
            doReturn(mock(EnginePlatformHooks.class)).when(engine).getPlatformHooks();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            IrisComplex complex = new IrisComplex(engine);
            doReturn(complex).when(engine).getComplex();
            try {
                assertEquals("alpha", complex.getTrueBiomeStream().get(15, 0).getLoadKey());
                assertEquals("beta", complex.getTrueBiomeStream().get(16, 0).getLoadKey());
                assertEquals("gamma", complex.getTrueBiomeStream().get(0, 16).getLoadKey());
                assertEquals(137D, complex.getBaseTerrainHeightStream().getDouble(15, 0), 0D);
                assertEquals(147D, complex.getBaseTerrainHeightStream().getDouble(16, 0), 0D);
                assertEquals(157D, complex.getBaseTerrainHeightStream().getDouble(0, 16), 0D);
                assertEquals("owned", complex.getRegionStream().get(16, 0).getLoadKey());
                assertEquals("iris-focus/", complex.getRegionStream().get(0, 16).getLoadKey().substring(0, 11));
                assertEquals("alpha", dimension.getFocus());
                try (ExecutorService readers = Executors.newFixedThreadPool(2)) {
                    List<Callable<String>> queries = List.of(
                            () -> complex.getTrueBiomeStream().get(15, 0).getLoadKey(),
                            () -> complex.getTrueBiomeStream().get(16, 0).getLoadKey());
                    List<Future<String>> values = readers.invokeAll(queries);
                    assertEquals("alpha", values.get(0).get());
                    assertEquals("beta", values.get(1).get());
                }
                doReturn(false).when(engine).isStudio();
                IrisComplex production = new IrisComplex(engine);
                try {
                    assertNull(production.getBiomeBuffet());
                    assertEquals("alpha", production.getTrueBiomeStream().get(16, 0).getLoadKey());
                } finally {
                    production.close();
                }
            } finally {
                complex.close();
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void everyBuffetSizeHasStableCellsAndNoWrappedOrNegativeCells() throws Exception {
        IrisData data = IrisData.openRuntime(fixture().toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load("main");
            for (StudioMode mode : StudioMode.values()) {
                int size = mode.biomeSizeChunks();
                if (size == 0) {
                    continue;
                }
                dimension.setStudioMode(mode);
                BiomeBuffetLayout layout = new BiomeBuffetLayout(dimension, () -> data);
                assertEquals("alpha", layout.chunk(size - 1, size - 1).biome().getLoadKey());
                assertEquals("beta", layout.chunk(size, 0).biome().getLoadKey());
                assertEquals("gamma", layout.chunk(0, size).biome().getLoadKey());
                assertNotNull(layout.chunk(size, size));
                assertNull(layout.chunk(size * 2, 0));
                assertNull(layout.chunk(0, size * 2));
                assertNull(layout.chunk(-1, 0));
                assertNull(layout.chunk(0, -1));
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void unrelatedBuffetBiomesDoNotChangeTheFocusedBiomesGeneratorNoise() throws Exception {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
        Path pack = fixture();
        Files.writeString(pack.resolve("biomes/alpha.json"),
                "{\"children\":[\"beta\"],\"generators\":[{\"generator\":\"flat\",\"min\":0,\"max\":40}]}");
        Files.writeString(pack.resolve("biomes/gamma.json"),
                "{\"generators\":[{\"generator\":\"unrelated\",\"min\":0,\"max\":40}]}");
        Files.writeString(pack.resolve("generators/unrelated.json"),
                "{\"composite\":[{\"enabled\":false,\"offsetY\":1}]}");
        IrisData data = IrisData.openRuntime(pack.toFile());
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            PlatformBlockState block = mock(PlatformBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            IrisDimension dimension = data.getDimensionLoader().load("main");
            Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
            doReturn(data).when(engine).getData();
            doReturn(dimension).when(engine).getDimension();
            doReturn(data.getBiomeLoader().load("alpha")).when(engine).getFocus();
            doReturn(true).when(engine).isStudio();
            doReturn(384).when(engine).getHeight();
            doReturn(-64).when(engine).getMinHeight();
            doReturn(320).when(engine).getMaxHeight();
            doReturn(mock(EnginePlatformHooks.class)).when(engine).getPlatformHooks();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            IrisComplex buffet = new IrisComplex(engine);
            doReturn(false).when(engine).isStudio();
            IrisComplex focused = new IrisComplex(engine);
            try {
                assertEquals(127D, focused.getBaseTerrainHeightStream().getDouble(0, 0), 0D);
                assertEquals(focused.getBaseTerrainHeightStream().getDouble(0, 0),
                        buffet.getBaseTerrainHeightStream().getDouble(0, 0), 0D);
                assertEquals(167D, buffet.getBaseTerrainHeightStream().getDouble(0, 16), 0D);
            } finally {
                focused.close();
                buffet.close();
            }
        } finally {
            data.close();
        }
    }

    private Path fixture() throws Exception {
        Path pack = temporaryFolder.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"focus":"alpha","regions":["owned"],"studioMode":"BIOME_BUFFET_1x1",
                 "landChance":1,"carvingEnabled":false,"hydrology":{"rivers":{"enabled":false}}}
                """);
        Files.writeString(pack.resolve("regions/owned.json"), "{\"landBiomes\":[\"alpha\"]}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        String[] names = {"alpha", "beta", "gamma", "zeta"};
        for (int index = 0; index < names.length; index++) {
            int height = (index + 1) * 10;
            Files.writeString(pack.resolve("biomes/" + names[index] + ".json"),
                    "{\"generators\":[{\"generator\":\"flat\",\"min\":" + height + ",\"max\":" + height + "}]"
                            + (index == 0 ? ",\"children\":[\"beta\"]" : "") + "}");
        }
        return pack;
    }
}
