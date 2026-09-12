package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.MarkerSpawnScanner.PreparedMarkerSpawn;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerBiomeTest {
    @Test
    public void loadingSurfaceDefinitionsDoNotConsumeInitialSpawnFlag() throws Exception {
        Engine engine = mock(Engine.class);
        IrisWorldManager manager = manager(engine);
        WorldChunkMaintenance maintenance = manager.chunkMaintenance;
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(2);
        when(chunk.getZ()).thenReturn(-1);
        BiomeEnvironment environment = environment();
        when(engine.getSurfaceBiomeEnvironment(40, -8))
                .thenThrow(new SavedBiomeUnavailableException("Loading", true))
                .thenReturn(environment);
        IrisSettings settings = new IrisSettings();
        settings.getWorld().setAmbientEntitySpawningSystem(true);

        try (MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class)) {
            configured.when(IrisSettings::get).thenReturn(settings);
            WorldEntitySpawner spawner = new WorldEntitySpawner(manager);
            spawner.prepareInitialSpawn(chunk, List.of());
            verify(maintenance, never()).raiseInitialSpawnMarkerFlag(any(), anyInt(), anyInt(), any());
            spawner.prepareInitialSpawn(chunk, List.of());
            verify(maintenance).raiseInitialSpawnMarkerFlag(eq(world), eq(2), eq(-1), any());
        }
    }

    @Test
    public void preparedInitialMarkersKeepDefinitionsAfterSavedQueryEviction() throws Exception {
        Engine engine = mock(Engine.class);
        IrisWorldManager manager = manager(engine);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        BiomeEnvironment environment = environment();
        BiomeEnvironment.Scope scope = mock(BiomeEnvironment.Scope.class);
        when(engine.openBiomeEnvironmentScope(environment)).thenReturn(scope);
        IrisSpawner definition = mock(IrisSpawner.class);
        IrisEntitySpawn entity = mock(IrisEntitySpawn.class);
        IrisPosition position = new IrisPosition(3, 90, 4);
        when(definition.getInitialSpawns()).thenReturn(new KList<>(entity));
        when(definition.canSpawn(engine, 0, 0)).thenReturn(true);
        when(entity.getRarity()).thenReturn(1);
        when(entity.getReferenceSpawner()).thenReturn(definition);
        when(entity.spawn(eq(engine), eq(position), any(RNG.class))).thenReturn(1);
        KSet<IrisSpawner> definitions = new KSet<>();
        definitions.add(definition);
        PreparedMarkerSpawn marker = new PreparedMarkerSpawn(position, definitions, environment);
        IrisSettings settings = new IrisSettings();
        settings.getWorld().setAmbientEntitySpawningSystem(false);

        try (MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class)) {
            configured.when(IrisSettings::get).thenReturn(settings);
            new WorldEntitySpawner(manager).prepareInitialSpawn(chunk, List.of(marker));
            ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            verify(manager.chunkMaintenance).raiseInitialSpawnMarkerFlag(eq(world), eq(0), eq(0), callback.capture());
            doThrow(new SavedBiomeUnavailableException("Evicted", true)).when(engine)
                    .getBiomeEnvironment(anyInt(), anyInt(), anyInt());
            callback.getValue().run();
            verify(entity).spawn(eq(engine), eq(position), any(RNG.class));
            verify(engine).openBiomeEnvironmentScope(environment);
            verify(scope).close();
            verify(engine, never()).getBiomeEnvironment(anyInt(), anyInt(), anyInt());
        }
    }

    private static IrisWorldManager manager(Engine engine) throws Exception {
        IrisWorldManager manager = mock(IrisWorldManager.class);
        when(manager.getEngine()).thenReturn(engine);
        Field field = IrisWorldManager.class.getDeclaredField("chunkMaintenance");
        field.setAccessible(true);
        field.set(manager, mock(WorldChunkMaintenance.class));
        return manager;
    }

    private static BiomeEnvironment environment() {
        return new BiomeEnvironment(3L, mock(IrisBiome.class), mock(IrisRegion.class),
                mock(IrisDimension.class), mock(IrisData.class));
    }
}
