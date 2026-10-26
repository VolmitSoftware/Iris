package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkedDoubleDataCache;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.TileType;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisComplexHeightStreamTest {
    private static final HydrologySettings RASTER_SETTINGS = FakeHydrology.settings(HydrologyFixture.SEA_LEVEL);

    @ClassRule public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void sharedCornersRemainUnallocatedUntilTerrainSamplesThem() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            assertNull(fixture.complex.getSharedCornerBounds());
            fixture.complex.getBaseTerrainHeightStream().getDouble(0, 0);
            assertNotNull(fixture.complex.getSharedCornerBounds());
        }
    }

    @Test
    public void mappedBiomesSkipProceduralSelectionAndNaturalHeight() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            IrisBiome mapped = new IrisBiome();
            when(fixture.images.sampleBiome(anyDouble(), anyDouble())).thenReturn(mapped);
            assertSame(mapped, fixture.complex.getBaseBiomeStream().get(-16, 32));
            assertSame(mapped, fixture.complex.getBaseBiomeStream().get(-16.75, 10, 32.5));
            assertSame(mapped, fixture.complex.getNaturalTrueBiomeStream().get(-16, 32));
            assertSame(mapped, fixture.complex.getNaturalTrueBiomeStream().get(-16.75, 10, 32.5));
            assertNull(fixture.complex.getSharedCornerBounds());

            when(fixture.images.sampleBiome(anyDouble(), anyDouble())).thenReturn(null);
            assertEquals("plain", fixture.complex.getBaseBiomeStream().get(16, 32).getLoadKey());
            assertEquals("plain", fixture.complex.getNaturalTrueBiomeStream().get(16, 32).getLoadKey());
            assertNotNull(fixture.complex.getSharedCornerBounds());
        }
    }

    @Test
    public void workerPrefillUsesOneThreadCheckAndRetainsRoundedValues() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            AtomicInteger samples = fixture.installNaturalHeight();
            ChunkedDoubleDataCache cache = new ChunkedDoubleDataCache(fixture.complex.getRawHeightStream(), -16, 32);
            int[] rounded = new int[256];
            clearInvocations(fixture.hooks);
            cache.fillRounded(rounded);
            verify(fixture.hooks, atMost(2)).isMainThread();
            assertEquals(256, samples.get());
            for (int index = 0; index < 256; index++) {
                int x = index & 15;
                int z = index >> 4;
                double expected = height(-16 + x, 32 + z);
                assertBits(expected, cache.getDouble(x, z));
                assertEquals((int) Math.round(expected), rounded[index]);
            }
            clearInvocations(fixture.hooks);
            cache.fill();
            verify(fixture.hooks, atMost(2)).isMainThread();
            assertEquals(256, samples.get());
        }
    }

    @Test(timeout = 120_000L)
    public void rasterColumnsTakeTheRasterTerrainAndRiverlessColumnsKeepTheNaturalHeight() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            ProceduralStream<Double> heights = fixture.complex.getRawHeightStream();
            ChunkWaterRaster raster = fixture.hydrology.runtime.raster(0, 0);
            assertFalse(raster.empty());
            assertEquals(WaterRole.POND, raster.role(8, 8));
            assertTrue(raster.terrainHeight(8, 8) < (int) Math.round(HydrologyFixture.NATURAL));
            assertBits(raster.terrainHeight(8, 8), heights.getDouble(8, 8));
            double[] values = new double[256];
            ((ChunkFillableDoubleStream2D) heights).fillChunkDoubles(0, 0, values);
            int carved = 0;
            for (int index = 0; index < 256; index++) {
                int terrain = raster.terrainHeight(index & 15, index >> 4);
                boolean natural = terrain == (int) Math.round(HydrologyFixture.NATURAL);
                carved += natural ? 0 : 1;
                assertBits(natural ? HydrologyFixture.NATURAL : terrain, values[index]);
            }
            assertTrue(carved > 0);
            assertTrue(fixture.hydrology.runtime.raster(40, 40).empty());
            ((ChunkFillableDoubleStream2D) heights).fillChunkDoubles(640, 640, values);
            for (double value : values) {
                assertBits(HydrologyFixture.NATURAL, value);
            }
        }
    }

    @Test(timeout = 120_000L)
    public void mainThreadProvisionalRasterAnswersNaturallyAndIsNeverCached() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            fixture.hydrology.hold();
            ProceduralStream<Double> heights = fixture.complex.getRawHeightStream();
            ChunkFillableDoubleStream2D bulk = (ChunkFillableDoubleStream2D) heights;
            double[] values = new double[256];
            fixture.mainThread.set(true);
            assertThrows(ProvisionalSampling.Unmemoizable.class, () -> ProvisionalSampling.memoizable(() -> {
                bulk.fillChunkDoubles(0, 0, values);
                return values[0];
            }));
            for (double value : values) {
                assertBits(HydrologyFixture.NATURAL, value);
            }
            assertThrows(ProvisionalSampling.Unmemoizable.class,
                    () -> ProvisionalSampling.memoizable(() -> fixture.complex.isHydrologyPlanned(8, 8)));
            assertFalse(fixture.complex.isHydrologyPlanned(8, 8));
            assertBits(HydrologyFixture.NATURAL, heights.getDouble(8, 10, 8));
            assertBits(HydrologyFixture.SEA_LEVEL, fixture.complex.getWaterHeadStream().getDouble(8, 8));
            assertSame(WaterRole.NONE, fixture.complex.getWaterRoleStream().get(8, 8));
            assertBits(HydrologyFixture.NATURAL, fixture.complex.getHeightFluidStream().getDouble(8, 8));

            fixture.hydrology.release();
            ChunkWaterRaster landed = fixture.hydrology.awaitSnapshot(0, 0);
            assertTrue(fixture.complex.isHydrologyPlanned(8, 8));
            bulk.fillChunkDoubles(0, 0, values);
            assertBits(landed.terrainHeight(8, 8), values[8 << 4 | 8]);
            assertBits(HydrologyFixture.POND_LEVEL, fixture.complex.getHeightFluidStream().getDouble(8, 8));
            fixture.mainThread.set(false);
            bulk.fillChunkDoubles(0, 0, values);
            assertBits(landed.terrainHeight(8, 8), values[8 << 4 | 8]);
            assertBits(landed.terrainHeight(8, 8), heights.getDouble(8, 8));
        }
    }

    @Test(timeout = 15_000L)
    public void mapHeightPreviewReturnsWhilePlanningAndRefreshesToFinalTerrain() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            fixture.hydrology.hold();
            MapTileRenderer renderer = new MapTileRenderer(fixture.engine);
            MapLayer layer = MapLayer.of(TileType.HEIGHT);
            long before = ProvisionalSampling.revision();

            MapTile preview = renderer.renderTile(0D, 0D, 1D, 16, layer, true, () -> false);

            assertEquals(HydrologyFixture.NATURAL, preview.heightAt(8, 8), 0D);
            assertNotEquals(before, ProvisionalSampling.revision());
            fixture.hydrology.release();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
            MapTile settled;
            do {
                assertTrue("Map height preview never settled", System.nanoTime() < deadline);
                Thread.sleep(10L);
                before = ProvisionalSampling.revision();
                settled = renderer.renderTile(0D, 0D, 1D, 16, layer, true, () -> false);
            } while (before != ProvisionalSampling.revision());

            double terrain = fixture.complex.getHeightStream().getDouble(8D, 8D);
            assertNotEquals(HydrologyFixture.NATURAL, terrain, 0D);
            assertEquals(terrain, settled.heightAt(8, 8), 0D);
            assertEquals(before, ProvisionalSampling.revision());
        }
    }

    @Test(timeout = 120_000L)
    public void waterStreamsFollowTheRaster() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            ChunkWaterRaster raster = fixture.hydrology.runtime.raster(0, 0);
            assertBits(HydrologyFixture.POND_LEVEL, fixture.complex.getWaterHeadStream().getDouble(8, 8));
            assertSame(WaterRole.POND, fixture.complex.getWaterRoleStream().get(8, 8));
            assertSame(raster.role(15, 15), fixture.complex.getWaterRoleStream().get(15.5, 15.5));
            assertBits(HydrologyFixture.SEA_LEVEL, fixture.complex.getWaterHeadStream().getDouble(640, 640));
            assertSame(WaterRole.NONE, fixture.complex.getWaterRoleStream().get(640, 640));
            EngineMantle mantle = mock(EngineMantle.class, CALLS_REAL_METHODS);
            doReturn(fixture.complex).when(mantle).getComplex();
            assertEquals(HydrologyFixture.POND_LEVEL, mantle.getFluidHeight(8, 8));
            assertEquals(HydrologyFixture.SEA_LEVEL, mantle.getFluidHeight(640, 640));
        }
    }

    @Test(timeout = 120_000L)
    public void objectPlacersSeeTheEffectiveWaterHead() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            IObjectPlacer placer = mock(IObjectPlacer.class, CALLS_REAL_METHODS);
            doReturn(fixture.engine).when(placer).getEngine();
            doReturn(fixture.engine.getDimension().getFluidHeight()).when(placer).getFluidHeight();

            assertEquals(HydrologyFixture.POND_LEVEL, placer.getFluidHeight(8, 8));
            assertEquals(HydrologyFixture.SEA_LEVEL, placer.getFluidHeight(640, 640));
        }
    }

    @Test(timeout = 120_000L)
    public void objectsStayOffWaterShoresAndDamsButMayStandOnBanks() throws Exception {
        Set<WaterRole> blocked = EnumSet.of(WaterRole.CHANNEL, WaterRole.POND, WaterRole.MOUTH, WaterRole.CASCADE,
                WaterRole.WATERFALL, WaterRole.INLET, WaterRole.SHORE, WaterRole.POND_SHORE, WaterRole.DAM);
        Set<WaterRole> allowed = EnumSet.of(WaterRole.NONE, WaterRole.BANK);
        assertEquals(EnumSet.allOf(WaterRole.class), union(blocked, allowed));
        assertTrue(Collections.disjoint(blocked, allowed));
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            assertTrue(fixture.complex.hasWaterOrShore(8, 8));
            assertFalse(fixture.complex.hasWaterOrShore(640, 640));
            ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
            when(raster.waterHead(anyInt(), anyInt())).thenReturn(Integer.MIN_VALUE);
            for (WaterRole role : WaterRole.values()) {
                when(raster.role(5, 9)).thenReturn(role);
                when(raster.role(15, 0)).thenReturn(role);
                IrisComplex complex = installRaster(fixture, raster, true);
                assertEquals(role.name(), blocked.contains(role), complex.hasWaterOrShore(21, -7));
                assertEquals(role.name() + " at the chunk corner", blocked.contains(role), complex.hasWaterOrShore(31, -16));
            }
            when(raster.role(5, 9)).thenReturn(WaterRole.CHANNEL);
            assertFalse(installRaster(fixture, raster, false).hasWaterOrShore(21, -7));
        }
    }

    private static Set<WaterRole> union(Set<WaterRole> first, Set<WaterRole> second) {
        Set<WaterRole> union = EnumSet.noneOf(WaterRole.class);
        union.addAll(first);
        union.addAll(second);
        return union;
    }

    @Test(timeout = 120_000L)
    public void waterFluidAndMaterialsComeFromTheRasterColumn() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(enabledHydrologyPack())) {
            IrisHydrologyMaterials ponds = fixture.hydrology.runtime.settings().ponds().materials();
            ChunkWaterRaster raster = fixture.hydrology.runtime.raster(0, 0);
            assertSame(WaterRole.POND, raster.role(8, 8));
            assertSame(fixture.complex.getWaterFluidStreams().get("water").get(8, 8), fixture.complex.resolveWaterFluid(8, 8));
            assertSame(ponds, fixture.complex.resolveWaterMaterials(8, 8));
            int pondFamily = 0;
            for (int chunk = 0; chunk < 9; chunk++) {
                ChunkWaterRaster near = fixture.hydrology.runtime.raster(chunk % 3, chunk / 3);
                for (int index = 0; index < 256; index++) {
                    WaterRole role = near.role(index & 15, index >> 4);
                    if (role != WaterRole.NONE && !role.wet()) {
                        pondFamily++;
                        assertSame(role.name(), ponds, fixture.complex.resolveWaterMaterials((chunk % 3 << 4) + (index & 15),
                                (chunk / 3 << 4) + (index >> 4)));
                    }
                }
            }
            assertTrue(pondFamily > 0);
            assertNull(fixture.complex.resolveWaterMaterials(640, 640));
            assertThrows(IllegalStateException.class, () -> fixture.complex.resolveWaterFluid(640, 640));
            assertSame(fixture.complex.getWaterFluidStreams().get("water").get(640, 640), fixture.complex.resolveWaterFluid("water", 640, 640));
            assertThrows(IllegalArgumentException.class, () -> fixture.complex.resolveWaterFluid("lava", 8, 8));
        }
    }

    @Test(timeout = 120_000L)
    public void legacyCaveCellFluidKeysFallBackToTheDimensionFluidWithAWarningPerKey() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(enabledHydrologyPack());
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertSame(fixture.complex.getWaterFluidStreams().get("water").get(4, 4), fixture.complex.resolveCaveCellFluid("water", 4, 4));
            assertSame(fixture.complex.resolveSeaFluid(7, 4), fixture.complex.resolveCaveCellFluid(HydrologyCaveCell.SEA_FLUID, 7, 4));
            logging.verifyNoInteractions();
            assertSame(fixture.complex.getFluidStream().get(4, 4), fixture.complex.resolveCaveCellFluid("river", 4, 4));
            assertSame(fixture.complex.getFluidStream().get(5, 4), fixture.complex.resolveCaveCellFluid("river", 5, 4));
            assertSame(fixture.complex.getFluidStream().get(6, 4), fixture.complex.resolveCaveCellFluid("deep_lava", 6, 4));
            logging.verify(() -> IrisLogging.warnOnce(eq("hydrology-cave-cell-fluid:river"), anyString(), eq("river")), times(2));
            logging.verify(() -> IrisLogging.warnOnce(eq("hydrology-cave-cell-fluid:deep_lava"), anyString(), eq("deep_lava")));
        }
    }

    @Test(timeout = 120_000L)
    public void materialsFollowTheEffectiveRoleAndChannelColumnsTakeTheirTier() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(enabledHydrologyPack())) {
            IrisHydrologyMaterials ponds = RASTER_SETTINGS.ponds().materials();
            ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
            IrisHydrologyMaterials tierMaterials = new IrisHydrologyMaterials();
            HydrologyTier tier = new HydrologyTier("river", 0f, 1f, 2f, 1f, 0.4f, 1, true, 6, 0, IrisBankStyle.SMOOTH, false,
                    tierMaterials, "water", List.of());
            when(raster.waterHead(anyInt(), anyInt())).thenReturn(Integer.MIN_VALUE);
            when(raster.tier(5, 9)).thenReturn(tier);
            when(raster.fluid(5, 9)).thenReturn("molten");
            for (WaterRole role : List.of(WaterRole.CHANNEL, WaterRole.MOUTH, WaterRole.CASCADE, WaterRole.WATERFALL, WaterRole.SHORE,
                    WaterRole.BANK)) {
                when(raster.role(5, 9)).thenReturn(role);
                assertSame(role.name(), tierMaterials, installRaster(fixture, raster).resolveWaterMaterials(21, -7));
            }
            when(raster.tier(5, 9)).thenReturn(null);
            assertSame(ponds, installRaster(fixture, raster).resolveWaterMaterials(21, -7));
            when(raster.role(5, 9)).thenReturn(WaterRole.NONE);
            assertNull(installRaster(fixture, raster).resolveWaterMaterials(21, -7));
            when(raster.role(5, 9)).thenReturn(WaterRole.POND);
            when(raster.waterHead(5, 9)).thenReturn(156);
            when(raster.terrainHeight(5, 9)).thenReturn(150);
            assertSame(ponds, installRaster(fixture, raster).resolveWaterMaterials(21, -7));
            when(raster.terrainHeight(5, 9)).thenReturn(156);
            IrisComplex dried = installRaster(fixture, raster);
            assertSame(WaterRole.NONE, dried.resolveWaterRole(21, -7));
            assertNull(dried.resolveWaterMaterials(21, -7));
            assertThrows(IllegalArgumentException.class, () -> dried.resolveWaterFluid(21, -7));
        }
    }

    @Test(timeout = 120_000L)
    public void hydrologyFluidsAndPondMaterialsAreCompiledFromThePack() throws Exception {
        Path pack = HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null, true);
        try (HydrologyFixture fixture = new HydrologyFixture(pack, temporaryFolder.newFolder())) {
            assertEquals(Set.of("water"), fixture.complex.getWaterFluidStreams().keySet());
            assertTrue(fixture.complex.getWaterFluidStreams().get("water").get(3, 4).isFluid());
            IrisHydrologyMaterials configured = fixture.engine.getDimension().getHydrology().getPonds().getMaterials();
            assertSame(fixture.complex.getHydrologyRuntime().settings().ponds().materials(), fixture.complex.getPondMaterials());
            assertNotSame(configured, fixture.complex.getPondMaterials());
            assertEquals(configured, fixture.complex.getPondMaterials());
        }
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack(), temporaryFolder.newFolder())) {
            assertTrue(fixture.complex.getWaterFluidStreams().isEmpty());
            assertNull(fixture.complex.getPondMaterials());
        }
    }

    @Test(timeout = 120_000L)
    public void plannerFailureAndClosedRuntimeFailTheColumnInsteadOfDryingIt() throws Exception {
        try (HydrologyFixture fixture = new HydrologyFixture(hydrologyPack())) {
            fixture.hydrology.failing.set(true);
            ProceduralStream<Double> heights = fixture.complex.getRawHeightStream();
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> heights.getDouble(8, 8));
            assertTrue(causes(failure).contains(FakeHydrology.FAILURE));
            assertThrows(IllegalStateException.class, () -> fixture.complex.getTrueBiomeStream().get(9, 9));
            fixture.hydrology.failing.set(false);
            assertBits(fixture.hydrology.runtime.raster(0, 0).terrainHeight(8, 8), heights.getDouble(8, 8));
            fixture.hydrology.runtime.close();
            assertThrows(CancellationException.class, () -> heights.getDouble(200, 200));
            assertThrows(CancellationException.class, () -> fixture.complex.getWaterHeadStream().getDouble(200, 200));
        }
    }

    @Test
    public void unalignedPrefillAndThreeDimensionalReadsRetainColumnCoordinates() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            fixture.installNaturalHeight();
            ProceduralStream<Double> stream = fixture.complex.getRawHeightStream();
            double[] values = new double[256];
            ((ChunkFillableDoubleStream2D) stream).fillChunkDoubles(-17, 31, values);
            for (int index = 0; index < 256; index++) {
                assertBits(height(-17 + (index & 15), 31 + (index >> 4)), values[index]);
            }
            assertBits(height(-16, 32), stream.getDouble(-16.75, 10, 32.5));
            assertBits(height(-16, 32), stream.get(-16.75, 10, 32.5));
        }
    }

    @Test
    public void workerPrefillDoesNotMemoizeProvisionalNaturalValues() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            AtomicBoolean ready = new AtomicBoolean();
            AtomicInteger samples = new AtomicInteger();
            fixture.complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> {
                samples.incrementAndGet();
                if (!ready.get()) {
                    ProvisionalSampling.mark();
                    return -1D;
                }
                return height(x, z);
            }));
            ChunkFillableDoubleStream2D stream = (ChunkFillableDoubleStream2D) fixture.complex.getRawHeightStream();
            double[] values = new double[256];
            stream.fillChunkDoubles(-16, 32, values);
            for (double value : values) {
                assertBits(-1D, value);
            }
            ready.set(true);
            int before = samples.get();
            stream.fillChunkDoubles(-16, 32, values);
            assertEquals(before + 256, samples.get());
            for (int index = 0; index < 256; index++) {
                assertBits(height(-16 + (index & 15), 32 + (index >> 4)), values[index]);
            }
            stream.fillChunkDoubles(-16, 32, values);
            assertEquals(before + 256, samples.get());
        }
    }

    private Path pack() throws Exception {
        Path pack = temporaryFolder.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"regions":["plain"],"landChance":1,
                 "carvingEnabled":false}
                """);
        Files.writeString(pack.resolve("regions/plain.json"), "{\"landBiomes\":[\"plain\"]}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        Files.writeString(pack.resolve("biomes/plain.json"),
                "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]}");
        return pack;
    }

    private Path hydrologyPack() throws Exception {
        return HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null);
    }

    private Path enabledHydrologyPack() throws Exception {
        return HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null, true);
    }

    private static IrisComplex installRaster(HydrologyFixture fixture, ChunkWaterRaster raster) {
        return installRaster(fixture, raster, true);
    }

    private static IrisComplex installRaster(HydrologyFixture fixture, ChunkWaterRaster raster, boolean enabled) {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(runtime.enabled()).thenReturn(enabled);
        when(runtime.settings()).thenReturn(enabled ? RASTER_SETTINGS : null);
        when(runtime.raster(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        when(runtime.raster(1, -1)).thenReturn(raster);
        when(runtime.rasterAt(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        when(runtime.rasterAt(21, -7)).thenReturn(raster);
        return fixture.complex(runtime);
    }

    private static String causes(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    private static double height(double x, double z) {
        return x * 0.125D - z * 0.03125D;
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        private final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        private final AtomicBoolean mainThread = new AtomicBoolean();
        private final MockedStatic<B> blocks = mockStatic(B.class);
        private final MockedStatic<IrisImageMapRuntime> imageRuntimes = mockStatic(IrisImageMapRuntime.class);
        private final IrisImageMapRuntime images = mock(IrisImageMapRuntime.class);
        private final IrisData data;
        private final IrisComplex complex;

        private Fixture(Path pack) {
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getState(anyString())).thenReturn(block);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            imageRuntimes.when(() -> IrisImageMapRuntime.compile(engine)).thenReturn(images);
            when(images.has(IrisImageMapApplication.BIOME)).thenReturn(true);
            when(images.sampleTerrainHeight(anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> invocation.<Double>getArgument(2));
            data = IrisData.openRuntime(pack.toFile());
            IrisDimension dimension = data.getDimensionLoader().load("main");
            doReturn(data).when(engine).getData();
            doReturn(dimension).when(engine).getDimension();
            doReturn(384).when(engine).getHeight();
            doReturn(-64).when(engine).getMinHeight();
            doReturn(320).when(engine).getMaxHeight();
            when(hooks.isMainThread()).thenAnswer(ignored -> mainThread.get());
            doReturn(hooks).when(engine).getPlatformHooks();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            complex = new IrisComplex(engine);
            doReturn(complex).when(engine).getComplex();
        }

        private AtomicInteger installNaturalHeight() {
            AtomicInteger samples = new AtomicInteger();
            complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> {
                samples.incrementAndGet();
                return height(x, z);
            }));
            return samples;
        }

        @Override
        public void close() {
            try {
                complex.close();
                data.close();
            } finally {
                imageRuntimes.close();
                blocks.close();
                Mockito.framework().clearInlineMock(engine);
                Mockito.framework().clearInlineMock(hooks);
            }
        }
    }
}
