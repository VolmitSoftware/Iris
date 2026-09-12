package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldChunkMaintenanceSavedBiomeTest {
    @Test
    public void loadingDefersInitialSpawnUntilTheNextSuccessfulPass() throws Exception {
        verifyFailure(new SavedBiomeUnavailableException("Loading", true), true);
    }

    @Test
    public void unavailableSavedDataRemainsAnOperatorFailure() throws Exception {
        verifyFailure(new SavedBiomeUnavailableException("Missing biome", false), false);
    }

    @Test
    public void unrelatedFailuresAreNotSuppressed() throws Exception {
        verifyFailure(new IllegalStateException("Broken update"), false);
    }

    @Test
    public void loadingWithFailedMarkerCleanupRemainsAnOperatorFailure() throws Exception {
        SavedBiomeUnavailableException loading = new SavedBiomeUnavailableException("Loading", true);
        IllegalStateException cleanup = new IllegalStateException("Marker cleanup failed");
        loading.addSuppressed(cleanup);

        verifyFailure(loading, false);

        assertSame(cleanup, loading.getSuppressed()[0]);
    }

    @SuppressWarnings("unchecked")
    private void verifyFailure(RuntimeException failure, boolean loading) throws Exception {
        Engine engine = mock(Engine.class);
        IrisWorldManager manager = mock(IrisWorldManager.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        WorldEntitySpawner spawner = mock(WorldEntitySpawner.class);
        Field field = IrisWorldManager.class.getDeclaredField("entitySpawner");
        field.setAccessible(true);
        field.set(manager, spawner);
        when(manager.getEngine()).thenReturn(engine);
        when(manager.getMantle()).thenReturn(mantle);
        when(mantle.isChunkLoaded(2, -1)).thenReturn(true);
        when(spawner.isEntitySpawningEnabledForCurrentWorld()).thenReturn(true);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getChunkAt(2, -1)).thenReturn(chunk);
        IrisSettings settings = new IrisSettings();
        settings.getWorld().setPostLoadBlockUpdates(true);
        WorldChunkMaintenance maintenance = new WorldChunkMaintenance(manager);

        try (MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class);
             MockedStatic<EngineBukkitOps> updates = mockStatic(EngineBukkitOps.class)) {
            configured.when(IrisSettings::get).thenReturn(settings);
            updates.when(() -> EngineBukkitOps.updateChunk(engine, chunk, mantle))
                    .thenThrow(failure).thenAnswer(invocation -> null);

            if (loading) {
                maintenance.updateChunkRegion(world, 2, -1);
            } else {
                assertSame(failure, assertThrows(RuntimeException.class,
                        () -> maintenance.updateChunkRegion(world, 2, -1)));
            }
            verify(spawner, never()).spawnInitially(chunk);

            maintenance.updateChunkRegion(world, 2, -1);

            verify(spawner).spawnInitially(chunk);
        }
    }
}
