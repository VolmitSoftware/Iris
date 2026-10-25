package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

final class EngineFixture implements AutoCloseable {
    static final long SEED = 1337L;
    static final int HEIGHT = 384;

    final Engine engine = mock(Engine.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
    final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class, withSettings().stubOnly());
    final IrisData data;
    final IrisDimension dimension;
    final IrisComplex complex;

    EngineFixture(Path pack) {
        data = IrisData.openRuntime(pack.toFile());
        dimension = data.getDimensionLoader().load("main");
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(HEIGHT).when(engine).getHeight();
        doReturn(-64).when(engine).getMinHeight();
        doReturn(320).when(engine).getMaxHeight();
        when(hooks.isMainThread()).thenReturn(false);
        doReturn(hooks).when(engine).getPlatformHooks();
        doReturn(new SeedManager(SEED)).when(engine).getSeedManager();
        doReturn(IrisWorld.builder().name("fixture-world").minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        complex = new IrisComplex(engine);
        doReturn(complex).when(engine).getComplex();
    }

    static Environment environment() {
        return new Environment();
    }

    static Path pack(Path pack) throws IOException {
        return pack(pack, true);
    }

    static Path pack(Path pack, boolean terrain3D) throws IOException {
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"regions":["alpha","beta"],"landChance":0.55,"regionZoom":0.2,"biomeZoom":0.15,
                 "carvingEnabled":false,
                 "hydrologyPolicy":{"rainfall":0.5}}
                """);
        Files.writeString(pack.resolve("regions/alpha.json"), """
                {"landBiomes":["peaks","meadow"%s],"seaBiomes":["sea"],"shoreBiomes":["beach"],"rarity":1,
                 "hydrologyPolicy":{"pondDensity":3,"streams":"BLOCK","fluid":"water"}}
                """.formatted(terrain3D ? ",\"ledges\"" : ""));
        Files.writeString(pack.resolve("regions/beta.json"),
                "{\"landBiomes\":[\"meadow\",\"spires\"],\"seaBiomes\":[\"sea\"],\"shoreBiomes\":[\"beach\"],\"rarity\":2}");
        generator(pack, "mountain", "BILINEAR_STARCAST_9", 52, 0.5D, """
                [{"style":{"style":"FRACTAL_SMOKE","zoom":1},"seed":3356}]""");
        generator(pack, "plain", "BILINEAR_STARCAST_9", 12, 0.5D, """
                [{"style":{"style":"IRIS_DOUBLE","zoom":0.87,"fracture":{"style":"NOWHERE","zoom":0.195,"multiplier":18}},"seed":10056,"bezier":true}]""");
        generator(pack, "spikes", "STARCAST_12", 42, 0D, """
                [{"style":{"style":"CELLULAR","zoom":0.5},"seed":4}]""");
        biome(pack, "peaks", "[{\"generator\":\"mountain\",\"min\":40,\"max\":120},{\"generator\":\"spikes\",\"min\":0,\"max\":20}]",
                ",\"children\":[\"meadow\"],\"childShrinkFactor\":1.5");
        biome(pack, "meadow", "[{\"generator\":\"plain\",\"min\":2,\"max\":14}]", ",\"hydrologyPolicy\":{\"rainfall\":2,\"ponds\":\"BLOCK\"}");
        biome(pack, "ledges", "[{\"generator\":\"plain\",\"min\":20,\"max\":40}]", """
                ,"terrain3D":{"amplitude":64,"horizontalScale":96,"verticalScale":16,"minimumSlope":0,"slopeFade":1,
                 "fluidClearance":0,"fluidFade":1,"crackDepth":0}""");
        biome(pack, "spires", "[{\"generator\":\"spikes\",\"min\":10,\"max\":60},{\"generator\":\"plain\",\"min\":1,\"max\":4}]", "");
        biome(pack, "sea", "[{\"generator\":\"mountain\",\"min\":-32,\"max\":-10}]", "");
        biome(pack, "beach", "[{\"generator\":\"plain\",\"min\":0,\"max\":2}]", "");
        return pack;
    }

    private static void generator(Path pack, String key, String function, double scale, double detail, String composite) throws IOException {
        Files.writeString(pack.resolve("generators/" + key + ".json"), "{\"surfaceDetail\":" + detail
                + ",\"interpolator\":{\"function\":\"" + function + "\",\"horizontalScale\":" + scale + "}"
                + ",\"seed\":" + Math.abs(key.hashCode()) + ",\"composite\":" + composite.strip() + "}");
    }

    private static void biome(Path pack, String key, String generators, String extra) throws IOException {
        Files.writeString(pack.resolve("biomes/" + key + ".json"), "{\"layers\":[{\"palette\":[{\"block\":\"minecraft:stone\"}]}],\"generators\":"
                + generators + extra.strip() + "}");
    }

    @Override
    public void close() {
        complex.close();
        data.close();
        Mockito.framework().clearInlineMock(engine);
    }

    static final class Environment implements AutoCloseable {
        final IrisPlatform platform = mock(IrisPlatform.class);
        private final IrisSettings previousSettings = IrisSettings.settings;
        private final MockedStatic<B> blocks;
        private final MockedStatic<IrisImageMapRuntime> imageRuntimes;

        private Environment() {
            IrisSettings.settings = new IrisSettings();
            IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
            when(platform.platformName()).thenReturn("bukkit");
            IrisPlatforms.bind(platform);
            blocks = mockStatic(B.class);
            imageRuntimes = mockStatic(IrisImageMapRuntime.class);
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getState(anyString())).thenReturn(block);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            IrisImageMapRuntime images = mock(IrisImageMapRuntime.class);
            when(images.has(IrisImageMapApplication.BIOME)).thenReturn(false);
            when(images.sampleTerrainHeight(anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> invocation.<Double>getArgument(2));
            imageRuntimes.when(() -> IrisImageMapRuntime.compile(any(Engine.class))).thenReturn(images);
        }

        @Override
        public void close() {
            imageRuntimes.close();
            blocks.close();
            IrisPlatforms.unbind();
            IrisServices.remove(PreservationRegistry.class);
            IrisSettings.settings = previousSettings;
        }
    }
}
