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
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
    public void acceptedHydrologyMapsToThePublicDiagnosticStates() {
        assertEquals(IrisRiverState.NONE, IrisTerrainSVC.riverState(null));
        assertEquals(IrisRiverState.WET, IrisTerrainSVC.riverState(river(true)));
        assertEquals(IrisRiverState.DRY, IrisTerrainSVC.riverState(river(false)));
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

    private static HydrologyColumnSample river(boolean connectedFluid) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                1L,
                HydrologyFeatureType.SURFACE_POOL,
                2L,
                3L,
                0,
                63,
                0,
                1,
                0,
                false
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                feature,
                60,
                63,
                63,
                true,
                false,
                false,
                connectedFluid,
                false,
                false,
                true,
                connectedFluid,
                false,
                "water",
                "river",
                "mouth",
                "shore",
                "dry",
                "cave"
        );
        return new HydrologyColumnSample(0, 0, 70, 63, false, "parent", List.of(layer));
    }
}
