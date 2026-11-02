package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisComplexHydrologyTest {
    private static final int SEA_LEVEL = 85;
    private static final int GROUND = SEA_LEVEL + 2;
    private static final int RIVER_Z = 8;
    private static final int MOUTH_Z = 40;
    private static final int COLUMN_X = 8;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisSettings previousSettings;

    @Before
    public void prepareRuntime() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
    }

    @After
    public void releaseRuntime() {
        IrisPlatforms.unbind();
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test(timeout = 120_000L)
    public void seaLevelInlandRiversKeepTheLandBiome() throws Exception {
        Path pack = HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null);
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"regions":["plain"],"landChance":1,"fluidHeight":%d,"carvingEnabled":false}
                """.formatted(SEA_LEVEL));
        Files.writeString(pack.resolve("regions/plain.json"), "{\"landBiomes\":[\"plain\"],\"seaBiomes\":[\"ocean\"]}");
        Files.writeString(pack.resolve("biomes/ocean.json"), "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]}");
        HydrologyTier stream = FakeHydrology.settings(SEA_LEVEL).tiers().get(0);
        Reach river = reach(901L, stream, RIVER_Z, ReachStyle.RUN);
        Reach mouth = reach(902L, stream, MOUTH_Z, ReachStyle.MOUTH);
        try (HydrologyFixture fixture = new HydrologyFixture(pack);
             FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> GROUND, SEA_LEVEL, List.of(river, mouth), List.of())) {
            IrisComplex complex = fixture.complex(hydrology.runtime);
            complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> GROUND));
            ChunkWaterRaster channel = hydrology.runtime.raster(0, RIVER_Z >> 4);
            assertEquals("the inland river column is channel water", WaterRole.CHANNEL, channel.role(COLUMN_X, RIVER_Z & 15));
            assertEquals("the inland river holds the sea surface", SEA_LEVEL, channel.waterHead(COLUMN_X, RIVER_Z & 15));
            assertTrue("the inland river bed lies below the sea surface", channel.terrainHeight(COLUMN_X, RIVER_Z & 15) < SEA_LEVEL);
            assertEquals("a sea-level river on land keeps the land biome", "plain",
                    complex.getTrueBiomeStream().get(COLUMN_X, RIVER_Z).getLoadKey());
            ChunkWaterRaster sea = hydrology.runtime.raster(0, MOUTH_Z >> 4);
            assertEquals("the mouth column is mouth water", WaterRole.MOUTH, sea.role(COLUMN_X, MOUTH_Z & 15));
            assertEquals("a river mouth takes the sea biome", "ocean", complex.getTrueBiomeStream().get(COLUMN_X, MOUTH_Z).getLoadKey());
        }
    }

    private static Reach reach(long id, HydrologyTier tier, int z, ReachStyle style) {
        float[] xs = {-32f, 0f, 32f, 64f};
        float[] zs = new float[xs.length];
        int[] grade = new int[xs.length];
        float[] halfWidth = new float[xs.length];
        float[] depth = new float[xs.length];
        ReachStyle[] styles = new ReachStyle[xs.length];
        Arrays.fill(zs, z);
        Arrays.fill(grade, SEA_LEVEL);
        Arrays.fill(halfWidth, 3f);
        Arrays.fill(depth, 2f);
        Arrays.fill(styles, style);
        return new Reach(id, tier, 0L, xs, zs, grade, halfWidth, depth, styles, 5000f);
    }
}
