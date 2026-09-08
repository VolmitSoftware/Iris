package art.arcane.iris.engine.platform;

import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyRuntime;
import org.bukkit.Chunk;
import org.bukkit.World;
import art.arcane.iris.engine.object.StudioMode;
import art.arcane.iris.engine.object.IrisDimension;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;

public class BukkitChunkGeneratorInitializationModeTest {
    @Test
    public void normalStudioDemandsItsActualEntryAndAuthoringDoesNotPlanTerrain() throws Exception {
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        Field studio = BukkitChunkGenerator.class.getDeclaredField("studio");
        studio.setAccessible(true);
        studio.setBoolean(generator, true);
        Field loadLock = BukkitChunkGenerator.class.getDeclaredField("loadLock");
        loadLock.setAccessible(true);
        loadLock.set(generator, new BukkitChunkGenerator.GenerationStageGate(2, generator::isClosing));
        doReturn(false).when(generator).usesFlatStudioTerrain();
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(null));
        IrisEngine engine = mock(IrisEngine.class);
        generator.setEngine(engine);
        IrisDimension dimension = mock(IrisDimension.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getStudioMode()).thenReturn(StudioMode.NORMAL);
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime hydrology = mock(IrisHydrologyRuntime.class);
        HydrologyPlannerSettings settings = mock(HydrologyPlannerSettings.class, RETURNS_DEEP_STUBS);
        when(settings.routing().tileSize()).thenReturn(512);
        when(hydrology.settings()).thenReturn(settings);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        Method prefetch = BukkitChunkGenerator.class.getDeclaredMethod(
                "prefetchSpawnHydrology", Engine.class, World.class);
        prefetch.setAccessible(true);

        ((CompletableFuture<?>) prefetch.invoke(generator, engine, world)).get(5L, TimeUnit.SECONDS);

        verify(engine).startStudioEntryHydrology(0, 0);
        verifyNoInteractions(hydrology);
        assertEquals(0.5D, generator.getInitialSpawnLocation(world).getX(), 0D);
        assertEquals(96D, generator.getInitialSpawnLocation(world).getY(), 0D);
        assertEquals(0.5D, generator.getInitialSpawnLocation(world).getZ(), 0D);

        when(dimension.getStudioMode()).thenReturn(StudioMode.BIOME_BUFFET_1x1);
        ((CompletableFuture<?>) prefetch.invoke(generator, engine, world)).get(5L, TimeUnit.SECONDS);
        verify(engine, times(1)).startStudioEntryHydrology(0, 0);
        verify(hydrology).prefetchArea(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        when(dimension.getStudioMode()).thenReturn(StudioMode.NORMAL);

        IrisEngine authoring = mock(IrisEngine.class);
        doReturn(true).when(generator).usesFlatStudioTerrain();
        ((CompletableFuture<?>) prefetch.invoke(generator, authoring, world)).get(5L, TimeUnit.SECONDS);
        verifyNoInteractions(authoring);

        IrisEngine generated = mock(IrisEngine.class);
        when(generated.getComplex()).thenReturn(complex);
        doReturn(false).when(generator).usesFlatStudioTerrain();
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        ((CompletableFuture<?>) prefetch.invoke(generator, generated, world)).get(5L, TimeUnit.SECONDS);
        verify(generated, never()).startStudioEntryHydrology(anyInt(), anyInt());
    }

    @Test
    public void standardStudioTerrainModeTracksHotloadedDimension() {
        assertFalse(BukkitChunkGenerator.usesFlatStudioTerrain(true, false, StudioMode.NORMAL));
        assertTrue(BukkitChunkGenerator.usesFlatStudioTerrain(true, false, StudioMode.OBJECT_BUFFET));
        assertFalse(BukkitChunkGenerator.usesFlatStudioTerrain(true, false, StudioMode.NORMAL));
        assertTrue(BukkitChunkGenerator.usesFlatStudioTerrain(true, true, StudioMode.NORMAL));
        assertFalse(BukkitChunkGenerator.usesFlatStudioTerrain(false, false, StudioMode.OBJECT_BUFFET));
    }

    @Test
    public void authoringBaseHeightMatchesTheFloorAndEmptyShortWorlds() {
        assertEquals(65, BukkitChunkGenerator.authoringBaseHeight(-64, 320));
        assertEquals(129, BukkitChunkGenerator.authoringBaseHeight(128, 256));
        assertEquals(-64, BukkitChunkGenerator.authoringBaseHeight(-64, 64));
        assertEquals(-64, BukkitChunkGenerator.authoringBaseHeight(-64, 0));
    }

    @Test
    public void runtimeAndOrdinaryStudioWarmGenerationCaches() {
        IrisEngine.InitializationMode runtime =
                BukkitChunkGenerator.selectInitializationMode(false, false, false);
        IrisEngine.InitializationMode studio =
                BukkitChunkGenerator.selectInitializationMode(true, false, false);

        assertEquals(IrisEngine.InitializationMode.RUNTIME, runtime);
        assertFalse(runtime.studio());
        assertTrue(runtime.warmGenerationCaches());
        assertEquals(IrisEngine.InitializationMode.STUDIO, studio);
        assertTrue(studio.studio());
        assertTrue(studio.warmGenerationCaches());
    }

    @Test
    public void activeJigsawStudioSkipsGenerationCacheWarm() {
        IrisEngine.InitializationMode mode =
                BukkitChunkGenerator.selectInitializationMode(true, true, false);

        assertEquals(IrisEngine.InitializationMode.JIGSAW_STUDIO, mode);
        assertTrue(mode.studio());
        assertFalse(mode.warmGenerationCaches());
    }

    @Test
    public void activeObjectStudioSkipsGenerationCacheWarm() {
        IrisEngine.InitializationMode mode =
                BukkitChunkGenerator.selectInitializationMode(true, false, true);

        assertEquals(IrisEngine.InitializationMode.OBJECT_STUDIO, mode);
        assertTrue(mode.studio());
        assertFalse(mode.warmGenerationCaches());
        assertEquals(IrisEngine.InitializationMode.RUNTIME,
                BukkitChunkGenerator.selectInitializationMode(false, false, true));
    }

    @Test
    public void jigsawBootstrapAndInitializationFailureSkipNativeStructureGeneration() {
        assertFalse(BukkitChunkGenerator.shouldGenerateNativeStructures(true, false, false));
        assertFalse(BukkitChunkGenerator.shouldGenerateNativeStructures(true, true, false));
        assertFalse(BukkitChunkGenerator.shouldGenerateNativeStructures(false, true, false));
        assertFalse(BukkitChunkGenerator.shouldGenerateNativeStructures(false, false, true));
        assertTrue(BukkitChunkGenerator.shouldGenerateNativeStructures(false, false, false));
    }

    @Test
    public void onlyOrdinaryOpenStudioRunsThePackHotloader() {
        assertFalse(BukkitChunkGenerator.shouldRunStudioHotload(false, false, false));
        assertFalse(BukkitChunkGenerator.shouldRunStudioHotload(true, true, false));
        assertFalse(BukkitChunkGenerator.shouldRunStudioHotload(true, false, true));
        assertTrue(BukkitChunkGenerator.shouldRunStudioHotload(true, false, false));
    }

    @Test
    public void transientStudioWorldsAreNotPersisted() {
        assertFalse(BukkitChunkGenerator.shouldPersistWorldRegistration(true));
        assertTrue(BukkitChunkGenerator.shouldPersistWorldRegistration(false));
    }
}
