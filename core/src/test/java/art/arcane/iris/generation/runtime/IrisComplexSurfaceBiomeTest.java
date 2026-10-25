package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisComplexSurfaceBiomeTest {
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
    public void shoreBandTakesTheInheritedPolicyShoreBiomeWithinItsWidth() throws Exception {
        Path pack = HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), "{\"shoreBiomes\":[\"sand\"]}", null);
        try (HydrologyFixture fixture = new HydrologyFixture(pack)) {
            int shore = 0;
            int inland = 0;
            for (int chunkX = 0; chunkX < 4; chunkX++) {
                for (int chunkZ = 0; chunkZ < 4; chunkZ++) {
                    ChunkWaterRaster raster = fixture.hydrology.runtime.raster(chunkX, chunkZ);
                    for (int index = 0; index < 256; index++) {
                        int localX = index & 15;
                        int localZ = index >> 4;
                        int distance = raster.shoreDistance(localX, localZ);
                        IrisBiome biome = fixture.complex.getTrueBiomeStream().get((chunkX << 4) + localX, (chunkZ << 4) + localZ);
                        if (!raster.empty() && distance >= 1 && distance <= 6) {
                            shore++;
                            assertEquals("sand", biome.getLoadKey());
                            assertEquals(InferredType.SHORE, biome.getInferredType());
                        } else {
                            inland++;
                            assertEquals("plain", biome.getLoadKey());
                        }
                    }
                }
            }
            assertTrue(shore > 0);
            assertTrue(inland > 0);
            assertEquals("plain", fixture.complex.getTrueBiomeStream().get(8, 8).getLoadKey());
            assertEquals(WaterRole.POND, fixture.hydrology.runtime.raster(0, 0).role(8, 8));
        }
    }

    @Test(timeout = 120_000L)
    public void biomePolicyOverridesTheShoreBiomesAndAnEmptyListClearsThem() throws Exception {
        Path cleared = HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), "{\"shoreBiomes\":[\"sand\"]}",
                "{\"shoreBiomes\":[]}");
        try (HydrologyFixture fixture = new HydrologyFixture(cleared)) {
            int column = shoreColumn(fixture, 1);
            assertEquals("plain", fixture.complex.getTrueBiomeStream().get(column, 8).getLoadKey());
        }
        Path inherited = HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null);
        try (HydrologyFixture fixture = new HydrologyFixture(inherited)) {
            int column = shoreColumn(fixture, 1);
            assertEquals("dune", fixture.complex.getTrueBiomeStream().get(column, 8).getLoadKey());
        }
    }

    @Test(timeout = 120_000L)
    public void mainThreadProvisionalShoreAnswersNaturallyAndIsNeverCached() throws Exception {
        Path pack = HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), "{\"shoreBiomes\":[\"sand\"]}", null);
        int column;
        try (HydrologyFixture fixture = new HydrologyFixture(pack)) {
            column = shoreColumn(fixture, 1);
        }
        try (HydrologyFixture cold = new HydrologyFixture(pack)) {
            cold.hydrology.hold();
            cold.mainThread.set(true);
            assertThrows(ProvisionalSampling.Unmemoizable.class,
                    () -> ProvisionalSampling.memoizable(() -> cold.complex.getTrueBiomeStream().get(column, 8)));
            assertEquals("plain", cold.complex.getTrueBiomeStream().get(column, 8).getLoadKey());
            cold.hydrology.release();
            cold.hydrology.awaitSnapshot(column >> 4, 0);
            assertEquals("sand", cold.complex.getTrueBiomeStream().get(column, 8).getLoadKey());
            cold.mainThread.set(false);
            assertEquals("sand", cold.complex.getTrueBiomeStream().get(column, 8).getLoadKey());
        }
    }

    @Test
    public void shorePolicyInheritsBiomeThenRegionThenDimension() {
        ShorePolicyPack pack = new ShorePolicyPack();
        assertEquals("dune", shoreSelect(pack, 1).getLoadKey());
        pack.region.setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>("sand")));
        assertEquals("sand", shoreSelect(pack, 4).getLoadKey());
        assertNull(shoreSelect(pack, 5));
        pack.inland.setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>("gravel")).setShoreBiomeWidth(8));
        assertEquals("gravel", shoreSelect(pack, 8).getLoadKey());
        assertSame(InferredType.SHORE, shoreSelect(pack, 8).getInferredType());
    }

    @Test
    public void shorePolicyEmptyListOrZeroWidthPlacesNoShoreBiome() {
        ShorePolicyPack pack = new ShorePolicyPack();
        pack.region.setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>()));
        assertNull(shoreSelect(pack, 1));
        pack.region.setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomeWidth(0));
        assertNull(new HydrologyShoreBiomes(pack.dimension, pack.data, 7L).select(pack.inland, pack.region, 0, 0, 1));
        assertNull(new HydrologyShoreBiomes(new IrisDimension(), pack.data, 7L).select(pack.inland, new IrisRegion(), 0, 0, 1));
    }

    @Test
    public void severalShoreBiomesPickOnePerPatchDeterministically() {
        ShorePolicyPack pack = new ShorePolicyPack();
        pack.region.setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>("sand", "dune", "gravel")));
        HydrologyShoreBiomes shores = new HydrologyShoreBiomes(pack.dimension, pack.data, 7L);
        HydrologyShoreBiomes again = new HydrologyShoreBiomes(pack.dimension, pack.data, 7L);
        Set<String> picked = new HashSet<>();
        for (int patch = -8; patch < 8; patch++) {
            IrisBiome first = shores.select(pack.inland, pack.region, patch * 64, 0, 2);
            assertEquals(first.getLoadKey(), shores.select(pack.inland, pack.region, patch * 64 + 63, 63, 2).getLoadKey());
            assertEquals(first.getLoadKey(), again.select(pack.inland, pack.region, patch * 64 + 17, 40, 1).getLoadKey());
            picked.add(first.getLoadKey());
        }
        assertTrue(picked.toString(), picked.size() > 1);
    }

    @Test
    public void missingShoreBiomeFailsNamingIt() {
        ShorePolicyPack pack = new ShorePolicyPack();
        pack.region.setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>("ghost")));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> shoreSelect(pack, 1));
        assertTrue(failure.getMessage(), failure.getMessage().contains("'ghost'") && failure.getMessage().contains("coast"));
    }

    private static IrisBiome shoreSelect(ShorePolicyPack pack, int distance) {
        return new HydrologyShoreBiomes(pack.dimension, pack.data, 7L).select(pack.inland, pack.region, 10, 20, distance);
    }

    private static final class ShorePolicyPack {
        private final IrisData data = mock(IrisData.class);
        private final IrisDimension dimension = new IrisDimension().setHydrologyPolicy(new IrisHydrologyPolicy()
                .setShoreBiomes(new KList<>("dune")).setShoreBiomeWidth(4));
        private final IrisRegion region = new IrisRegion();
        private final IrisBiome inland;

        @SuppressWarnings("unchecked")
        private ShorePolicyPack() {
            ResourceLoader<IrisBiome> biomes = mock(ResourceLoader.class);
            when(data.getBiomeLoader()).thenReturn(biomes);
            inland = biome("plain");
            when(biomes.load("plain")).thenReturn(inland);
            when(biomes.load("sand")).thenReturn(biome("sand"));
            when(biomes.load("dune")).thenReturn(biome("dune"));
            when(biomes.load("gravel")).thenReturn(biome("gravel"));
            region.setLoadKey("coast");
        }

        private IrisBiome biome(String key) {
            IrisBiome biome = new IrisBiome();
            biome.setLoadKey(key);
            biome.setLoader(data);
            return biome;
        }
    }

    private static int shoreColumn(HydrologyFixture fixture, int distance) {
        for (int x = 0; x < 64; x++) {
            ChunkWaterRaster raster = fixture.hydrology.runtime.raster(x >> 4, 0);
            if (!raster.empty() && raster.shoreDistance(x & 15, 8) == distance) {
                return x;
            }
        }
        throw new AssertionError("No column at shore distance " + distance + " along z=8");
    }

    @Test
    public void naturalSlopeMatchesTheRangeThreeStreamFormula() {
        assertEquals(5D, IrisComplex.calculateNaturalSlope(10D, 13D, 14D), 0D);
    }

    @Test
    public void elevatedRiverBankUsesTheLocalWaterHead() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome sea = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(false).when(base).isAquatic();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                68D,
                base,
                region,
                12D,
                18D,
                70D,
                constant(base),
                constant(sea),
                constant(base),
                constantSlope(0D)
        );

        assertSame(sea, resolved);
    }

    @Test
    public void shorelineHeightSelectsShoreBiome() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome shore = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                63D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(base),
                constant(base),
                constant(shore),
                constantSlope(0D)
        );

        assertSame(shore, resolved);
    }

    @Test
    public void raisedAquaticBiomeReturnsToLand() {
        IrisBiome aquatic = mock(IrisBiome.class);
        IrisBiome land = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(aquatic).isShore();
        doReturn(false).when(aquatic).isLand();
        doReturn(1D).when(region).getShoreHeight(4D, 7D);
        doReturn(0.5D).when(region).getShoreHeightMin();
        doReturn(1.5D).when(region).getShoreHeightMax();

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                66D,
                aquatic,
                region,
                4D,
                7D,
                63D,
                constant(land),
                constant(aquatic),
                constant(aquatic),
                constantSlope(0D)
        );

        assertSame(land, resolved);
    }

    @Test
    public void zeroShoreMinimumWidthLeavesTheVerticalBandAlone() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome land = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(false).when(base).isLand();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();
        doReturn(0D).when(region).getShoreMinimumWidth();

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                72D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(land),
                constant(base),
                constant(base),
                unsampledSlopes()
        );

        assertSame(land, resolved);
    }

    @Test
    public void shoreMinimumWidthWidensTheBandOnSteepGround() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome shore = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();
        doReturn(3D).when(region).getShoreMinimumWidth();

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                74D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(base),
                constant(base),
                constant(shore),
                constantSlope(9D)
        );

        assertSame(shore, resolved);
    }

    @Test
    public void shoreMinimumWidthDoesNothingOnFlatGround() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome land = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(false).when(base).isLand();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();
        doReturn(3D).when(region).getShoreMinimumWidth();

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                67D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(land),
                constant(base),
                constant(base),
                constantSlope(0D)
        );

        assertSame(land, resolved);
    }

    @Test
    public void widenedBandStillLeavesDeepWaterAndHighGroundAlone() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome sea = mock(IrisBiome.class);
        IrisBiome land = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(false).when(base).isLand();
        doReturn(false).when(base).isAquatic();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();
        doReturn(3D).when(region).getShoreMinimumWidth();

        assertSame(sea, IrisComplex.resolveSurfaceBiome(
                60D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(land),
                constant(sea),
                constant(base),
                unsampledSlopes()
        ));
        assertSame(land, IrisComplex.resolveSurfaceBiome(
                200D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(land),
                constant(sea),
                constant(base),
                unsampledSlopes()
        ));
    }

    @Test
    public void shoreBandTopTradesHorizontalWidthForHeightOnSlopes() {
        IrisRegion region = mock(IrisRegion.class);
        doReturn(3D).when(region).getShoreHeight(12D, 18D);
        doReturn(1D).when(region).getShoreHeightMin();
        doReturn(5D).when(region).getShoreHeightMax();
        doReturn(3D).when(region).getShoreMinimumWidth();

        assertEquals(66D, IrisComplex.shoreBandTop(67D, region, 12D, 18D, 63D, constantSlope(0D)), 0D);
        assertEquals(75D, IrisComplex.shoreBandTop(74D, region, 12D, 18D, 63D, constantSlope(9D)), 0D);
        assertEquals(75D, IrisComplex.shoreBandTop(74D, region, 12D, 18D, 63D, constantSlope(120D)), 0D);
        assertEquals(66D, IrisComplex.shoreBandTop(80D, region, 12D, 18D, 63D, constantSlope(120D)), 0D);
        assertEquals(66D, IrisComplex.shoreBandTop(64D, region, 12D, 18D, 63D, unsampledSlopes()), 0D);
    }

    private static ProceduralStream<Double> constantSlope(double slope) {
        return ProceduralStream.ofDouble((x, z) -> slope);
    }

    private static ProceduralStream<Double> unsampledSlopes() {
        return ProceduralStream.ofDouble((x, z) -> {
            throw new AssertionError("The shore band sampled the slope for a column that cannot be widened");
        });
    }

    private static ProceduralStream<IrisBiome> constant(IrisBiome biome) {
        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> stream = mock(ProceduralStream.class);
        doReturn(biome).when(stream).get(anyDouble(), anyDouble());
        return stream;
    }

    @Test
    public void regionShoreMinimumWidthDefaultsToZeroSoExistingPacksAreUnchanged() {
        assertEquals(0D, new IrisRegion().getShoreMinimumWidth(), 0D);
    }
}
