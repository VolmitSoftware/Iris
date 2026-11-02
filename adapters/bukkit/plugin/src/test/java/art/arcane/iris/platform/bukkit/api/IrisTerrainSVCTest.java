package art.arcane.iris.platform.bukkit.api;

import art.arcane.iris.api.terrain.IrisColumnField;
import art.arcane.iris.api.terrain.IrisColumnQuery;
import art.arcane.iris.api.terrain.IrisRiverState;
import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.iris.api.terrain.IrisColumnSample;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class IrisTerrainSVCTest {
    private static final IrisColumnQuery SMALL = IrisColumnQuery.rect(
            0, 0, 15, 15, 4, EnumSet.of(IrisColumnField.SURFACE_HEIGHT, IrisColumnField.SURFACE_KIND));

    @Test
    public void theServiceImplementsBothContracts() throws NoSuchMethodException {
        assertTrue(IrisService.class.isAssignableFrom(IrisTerrainSVC.class));
        assertTrue(IrisTerrainService.class.isAssignableFrom(IrisTerrainSVC.class));
        IrisTerrainSVC.class.getDeclaredConstructor();
    }

    @Test
    public void aQueryIsOnlyAnswerableWhileEnabledAndAgainstARealWorld() {
        assertTrue(IrisTerrainSVC.answerable(true, true));
        assertFalse("a disabled service must never resolve a generator",
                IrisTerrainSVC.answerable(false, true));
        assertFalse(IrisTerrainSVC.answerable(true, false));
        assertFalse(IrisTerrainSVC.answerable(false, false));
    }

    @Test
    public void aServiceThatIsNotEnabledAnswersAbsenceInsteadOfThrowing() {
        IrisTerrainSVC service = new IrisTerrainSVC();

        assertFalse(service.isIrisWorld(null));
        assertTrue(service.worldInfo(null).isEmpty());
        assertEquals(OptionalInt.empty(), service.surfaceHeight(null, 0, 0));
        assertEquals(IrisSurfaceKind.UNKNOWN, service.surfaceKind(null, 0, 0));
        assertTrue(service.surfaceBiomeKey(null, 0, 0).isEmpty());
        assertTrue(service.surfaceBiomeName(null, 0, 0).isEmpty());
        assertTrue(service.surfaceBiomeInfo(null, 0, 0).isEmpty());
        assertTrue(service.biomeKey(null, 0, 64, 0).isEmpty());
        assertTrue(service.regionKey(null, 0, 0).isEmpty());
        assertTrue(service.regionName(null, 0, 0).isEmpty());
    }

    @Test
    public void theServiceExposesDisplayNamesAlongsideLoadKeys() throws NoSuchMethodException {
        assertEquals(Optional.class,
                IrisTerrainService.class.getMethod("surfaceBiomeName", World.class, int.class, int.class)
                        .getReturnType());
        assertEquals(Optional.class,
                IrisTerrainService.class.getMethod("regionName", World.class, int.class, int.class)
                        .getReturnType());
        assertEquals(Optional.class,
                IrisTerrainService.class.getMethod("surfaceBiomeInfo", World.class, int.class, int.class)
                        .getReturnType());
    }

    @Test
    public void anUnanswerableSampleNeverTouchesTheSink() {
        IrisTerrainSVC service = new IrisTerrainSVC();
        AtomicInteger sinkCalls = new AtomicInteger();

        boolean answered = service.sampleColumns(null, SMALL, sample -> sinkCalls.incrementAndGet());

        assertFalse(answered);
        assertEquals(0, sinkCalls.get());
    }

    @Test
    public void nullArgumentsAreRefusedRatherThanDereferenced() {
        IrisTerrainSVC service = new IrisTerrainSVC();

        assertFalse(service.sampleColumns(null, null, null));
        assertFalse(service.sampleColumns(null, SMALL, null));
    }

    @Test
    public void waterRolesMapToThePublicRiverFields() {
        for (WaterRole role : WaterRole.values()) {
            assertEquals(role.name(), role.wet() ? IrisRiverState.WET : IrisRiverState.NONE, IrisTerrainSVC.riverState(role));
            double distance = switch (role) {
                case NONE -> IrisColumnSample.UNAVAILABLE_RIVER_DISTANCE;
                case CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET -> 0D;
                case SHORE, POND_SHORE -> 1D;
                case BANK, DAM -> 2D;
            };
            assertEquals(role.name(), distance, IrisTerrainSVC.riverDistance(role), 0D);
            int flow = switch (role) {
                case NONE, SHORE, POND_SHORE, BANK, DAM -> IrisColumnSample.UNAVAILABLE_RIVER_FLOW;
                case CHANNEL, MOUTH, CASCADE, WATERFALL -> 1;
                case POND, INLET -> 0;
            };
            assertEquals(role.name(), flow, IrisTerrainSVC.riverFlow(role));
        }
    }

    @Test
    public void columnSamplesReportTheWaterRoleOfEachColumn() throws Exception {
        assertEquals(List.of("RIVER", "WET", "0.0", "1", "6"), sampled(WaterRole.CHANNEL, 70));
        assertEquals(List.of("RIVER", "WET", "0.0", "0", "6"), sampled(WaterRole.POND, 70));
        assertEquals(List.of("RIVER_SHORE", "NONE", "1.0", "-1", "unavailable"), sampled(WaterRole.SHORE, 50));
        assertEquals(List.of("LAND", "NONE", "2.0", "-1", "unavailable"), sampled(WaterRole.BANK, 50));
        assertEquals(List.of("LAND", "NONE", "NaN", "-1", "unavailable"), sampled(WaterRole.NONE, 50));
    }

    @Test
    public void serverThreadUnplannedColumnsAreProvisional() throws Exception {
        List<IrisColumnSample> samples = sampledRow(blockX -> blockX < 16, () -> {
        });

        assertEquals(32, samples.size());
        for (IrisColumnSample sample : samples) {
            assertEquals("column " + sample.blockX(), sample.blockX() < 16, sample.provisional());
        }
    }

    @Test
    public void plannedColumnsAreFinal() throws Exception {
        ProvisionalSampling.mark();
        List<IrisColumnSample> samples = sampledRow(blockX -> false, () -> {
        });

        assertEquals(32, samples.size());
        for (IrisColumnSample sample : samples) {
            assertFalse("column " + sample.blockX(), sample.provisional());
        }
    }

    @Test
    public void aSinkThatReadsProvisionallyDoesNotMarkLaterColumns() throws Exception {
        List<IrisColumnSample> samples = sampledRow(blockX -> false, ProvisionalSampling::mark);

        assertEquals(32, samples.size());
        for (IrisColumnSample sample : samples) {
            assertFalse("column " + sample.blockX(), sample.provisional());
        }
    }

    @Test
    public void surfaceHeightIsUnavailableUntilTheColumnIsPlanned() throws Exception {
        World world = world(engine(blockX -> blockX >= 16));
        IrisTerrainSVC service = enabledService();

        assertEquals(OptionalInt.empty(), service.surfaceHeight(world, 16, 0));
        assertEquals(OptionalInt.of(-4), service.surfaceHeight(world, 0, 0));
    }

    @Test
    public void surfaceKindIsUnknownUntilTheColumnIsPlanned() throws Exception {
        World world = world(engine(blockX -> blockX >= 16));
        IrisTerrainSVC service = enabledService();

        assertEquals(IrisSurfaceKind.UNKNOWN, service.surfaceKind(world, 16, 0));
        assertEquals(IrisSurfaceKind.LAND, service.surfaceKind(world, 0, 0));
    }

    @Test
    public void surfaceBiomeReadsAreEmptyUntilTheColumnIsPlanned() throws Exception {
        World world = world(engine(blockX -> blockX >= 16));
        IrisTerrainSVC service = enabledService();

        assertTrue(service.surfaceBiomeKey(world, 16, 0).isEmpty());
        assertTrue(service.surfaceBiomeName(world, 16, 0).isEmpty());
        assertTrue(service.surfaceBiomeInfo(world, 16, 0).isEmpty());
        assertEquals(Optional.of("plains"), service.surfaceBiomeKey(world, 0, 0));
        assertEquals(Optional.of("Plains"), service.surfaceBiomeName(world, 0, 0));
        assertEquals("plains", service.surfaceBiomeInfo(world, 0, 0).orElseThrow().key());
    }

    @Test
    public void biomeKeyIsEmptyUntilTheColumnIsPlanned() throws Exception {
        World world = world(engine(blockX -> blockX >= 16));
        IrisTerrainSVC service = enabledService();

        assertTrue(service.biomeKey(world, 16, 64, 0).isEmpty());
        assertEquals(Optional.of("plains"), service.biomeKey(world, 0, 64, 0));
    }

    @Test
    public void regionReadsAreEmptyUntilTheColumnIsPlanned() throws Exception {
        World world = world(engine(blockX -> blockX >= 16));
        IrisTerrainSVC service = enabledService();

        assertTrue(service.regionKey(world, 16, 0).isEmpty());
        assertTrue(service.regionName(world, 16, 0).isEmpty());
        assertEquals(Optional.of("temperate"), service.regionKey(world, 0, 0));
        assertEquals(Optional.of("Temperate"), service.regionName(world, 0, 0));
    }

    @Test
    public void pendingBiomeMetadataReturnsAbsenceAndResolvesOnRetryWithoutLogging() throws Exception {
        Engine engine = mock(Engine.class);
        World world = world(engine);
        IrisTerrainSVC service = enabledService();
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn("desert/dunes");
        BiomeEnvironment environment = new BiomeEnvironment(1L, biome, mock(IrisRegion.class),
                mock(IrisDimension.class), mock(IrisData.class));
        when(engine.getSurfaceBiomeEnvironment(-17, 32))
                .thenThrow(new SavedBiomeUnavailableException("Saved biome information is loading", true))
                .thenReturn(environment);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertTrue(service.surfaceBiomeInfo(world, -17, 32).isEmpty());
            assertEquals("desert/dunes", service.surfaceBiomeInfo(world, -17, 32).orElseThrow().key());
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void pendingReadsDoNotConsumeTheFirstRealFailureReport() throws Exception {
        Engine engine = mock(Engine.class);
        World world = world(engine);
        IrisTerrainSVC service = enabledService();
        SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException(
                "Saved biome data could not be read", new IllegalStateException("Invalid saved data"));
        when(engine.getSurfaceBiomeEnvironment(-17, 32))
                .thenThrow(new SavedBiomeUnavailableException("Saved biome information is loading", true))
                .thenThrow(failure);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertTrue(service.surfaceBiomeInfo(world, -17, 32).isEmpty());
            logging.verifyNoInteractions();
            assertTrue(service.surfaceBiomeInfo(world, -17, 32).isEmpty());
            logging.verify(() -> IrisLogging.reportError(contains("(1 terrain API query faults"), same(failure)));
            logging.verifyNoMoreInteractions();
        }
    }

    @Test
    public void biomeAndRegionReadsTreatLoadingAsTemporaryAbsence() throws Exception {
        Engine engine = mock(Engine.class);
        World world = world(engine);
        IrisTerrainSVC service = enabledService();
        SavedBiomeUnavailableException pending = new SavedBiomeUnavailableException("Loading saved biomes", true);
        when(engine.getSurfaceBiome(0, 0)).thenThrow(pending);
        when(engine.getBiome(0, 64, 0)).thenThrow(pending);
        when(engine.getRegion(0, 0)).thenThrow(pending);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertTrue(service.surfaceBiomeKey(world, 0, 0).isEmpty());
            assertTrue(service.surfaceBiomeName(world, 0, 0).isEmpty());
            assertTrue(service.biomeKey(world, 0, 64, 0).isEmpty());
            assertTrue(service.regionKey(world, 0, 0).isEmpty());
            assertTrue(service.regionName(world, 0, 0).isEmpty());
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void aLoadingReadWithASuppressedFailureIsStillReported() throws Exception {
        Engine engine = mock(Engine.class);
        World world = world(engine);
        IrisTerrainSVC service = enabledService();
        SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException("Loading saved biomes", true);
        failure.addSuppressed(new IllegalStateException("Failed to release saved definitions"));
        when(engine.getSurfaceBiomeEnvironment(0, 0)).thenThrow(failure);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertTrue(service.surfaceBiomeInfo(world, 0, 0).isEmpty());
            logging.verify(() -> IrisLogging.reportError(contains("surfaceBiomeInfo"), same(failure)));
            logging.verifyNoMoreInteractions();
        }
    }

    @Test
    public void pendingBatchReadDoesNotHideSubsequentSinkFailures() throws Exception {
        Engine engine = mock(Engine.class);
        World world = world(engine);
        IrisTerrainSVC service = enabledService();
        IrisColumnQuery query = IrisColumnQuery.rect(0, 0, 0, 0, 1, EnumSet.of(IrisColumnField.BIOME_KEY));
        when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
        when(engine.getSurfaceBiome(0, 0))
                .thenThrow(new SavedBiomeUnavailableException("Loading saved biomes", true))
                .thenReturn(mock(IrisBiome.class));
        AtomicInteger sinkCalls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Consumer failed");

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
             MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class)) {
            settings.when(IrisSettings::get).thenReturn(new IrisSettings());
            assertFalse(service.sampleColumns(world, query, sample -> sinkCalls.incrementAndGet()));
            assertEquals(0, sinkCalls.get());
            logging.verifyNoInteractions();
            assertFalse(service.sampleColumns(world, query, sample -> { throw failure; }));
            logging.verify(() -> IrisLogging.reportError(contains("(1 terrain API sample faults"), same(failure)));
            logging.verifyNoMoreInteractions();
        }
    }

    private static List<String> sampled(WaterRole role, int head) throws Exception {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getInferredType()).thenReturn(InferredType.LAND);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
        when(engine.getHeight(0, 0)).thenReturn(60);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getSurfaceBiome(0, 0)).thenReturn(biome);
        when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> role, Interpolated.of(value -> 0D, value -> role)));
        when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> (double) head));
        IrisColumnQuery query = IrisColumnQuery.rect(0, 0, 0, 0, 1, EnumSet.of(IrisColumnField.SURFACE_KIND,
                IrisColumnField.RIVER_STATE, IrisColumnField.RIVER_DISTANCE, IrisColumnField.RIVER_FLOW,
                IrisColumnField.RIVER_WATER_SURFACE_Y));
        List<IrisColumnSample> samples = new ArrayList<>();
        try (MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class)) {
            settings.when(IrisSettings::get).thenReturn(new IrisSettings());
            assertTrue(enabledService().sampleColumns(world(engine), query, samples::add));
        }
        assertEquals(1, samples.size());
        IrisColumnSample sample = samples.get(0);
        return List.of(sample.surfaceKind().name(), sample.riverState().name(), String.valueOf(sample.riverDistance()),
                String.valueOf(sample.riverFlow()),
                sample.riverWaterSurfaceY() == IrisColumnSample.UNAVAILABLE_HEIGHT ? "unavailable" : String.valueOf(sample.riverWaterSurfaceY()));
    }

    private static List<IrisColumnSample> sampledRow(IntPredicate unplanned, Runnable afterAccept) throws Exception {
        Engine engine = engine(unplanned);
        IrisColumnQuery query = IrisColumnQuery.rect(0, 0, 31, 0, 1, EnumSet.of(IrisColumnField.SURFACE_HEIGHT,
                IrisColumnField.SURFACE_KIND, IrisColumnField.BIOME_KEY, IrisColumnField.RIVER_STATE));
        List<IrisColumnSample> samples = new ArrayList<>();
        try (MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class)) {
            settings.when(IrisSettings::get).thenReturn(new IrisSettings());
            assertTrue(enabledService().sampleColumns(world(engine), query, sample -> {
                samples.add(sample);
                afterAccept.run();
            }));
        }
        return samples;
    }

    private static Engine engine(IntPredicate unplanned) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        when(biome.getInferredType()).thenReturn(InferredType.LAND);
        when(biome.getLoadKey()).thenReturn("plains");
        when(biome.getName()).thenReturn("Plains");
        when(region.getLoadKey()).thenReturn("temperate");
        when(region.getName()).thenReturn("Temperate");
        BiomeEnvironment environment = new BiomeEnvironment(1L, biome, region, mock(IrisDimension.class), mock(IrisData.class));
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt())).thenAnswer(serverThreadAnswer(unplanned, 60));
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenAnswer(serverThreadAnswer(unplanned, biome));
        when(engine.getSurfaceBiomeEnvironment(anyInt(), anyInt())).thenAnswer(serverThreadAnswer(unplanned, environment));
        when(engine.getBiome(anyInt(), anyInt(), anyInt())).thenAnswer(serverThreadAnswer(unplanned, biome));
        when(engine.getRegion(anyInt(), anyInt())).thenAnswer(serverThreadAnswer(unplanned, region));
        when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((Double x, Double z) -> {
            serverThreadRead(unplanned, x.intValue());
            return WaterRole.NONE;
        }, Interpolated.of(value -> 0D, value -> WaterRole.NONE)));
        when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((double x, double z) -> {
            serverThreadRead(unplanned, (int) x);
            return 50D;
        }));
        return engine;
    }

    private static <T> Answer<T> serverThreadAnswer(IntPredicate unplanned, T value) {
        return invocation -> {
            serverThreadRead(unplanned, (int) invocation.getArgument(0));
            return value;
        };
    }

    private static void serverThreadRead(IntPredicate unplanned, int blockX) {
        if (unplanned.test(blockX)) {
            ProvisionalSampling.mark();
        }
    }

    private static World world(Engine engine) {
        World world = mock(World.class);
        ChunkGenerator generator = mock(ChunkGenerator.class, withSettings().extraInterfaces(PlatformChunkGenerator.class));
        when(world.getGenerator()).thenReturn(generator);
        when(world.getName()).thenReturn("sandbox");
        when(((PlatformChunkGenerator) generator).getEngine()).thenReturn(engine);
        return world;
    }

    private static IrisTerrainSVC enabledService() throws ReflectiveOperationException {
        IrisTerrainSVC service = new IrisTerrainSVC();
        Field enabled = IrisTerrainSVC.class.getDeclaredField("serviceEnabled");
        enabled.setAccessible(true);
        ((AtomicBoolean) enabled.get(service)).set(true);
        return service;
    }
}
