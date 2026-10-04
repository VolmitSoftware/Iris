package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.MarkerSpawnScanner.PreparedMarkerSpawn;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisEntitySpawn.SpawnContext;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerMarkerSeedTest {
    @Test
    public void initialMarkersIgnoreHashSetInsertionOrderAndOtherChunksRandomDraws() throws Exception {
        Engine engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(seeds.getEntity()).thenReturn(145398276L);
        IrisWorldManager manager = mock(IrisWorldManager.class);
        when(manager.getEngine()).thenReturn(engine);
        BiomeEnvironment environment = new BiomeEnvironment(1L, mock(IrisBiome.class), mock(IrisRegion.class),
                mock(IrisDimension.class), mock(IrisData.class));
        when(engine.openBiomeEnvironmentScope(environment)).thenReturn(mock(BiomeEnvironment.Scope.class));
        Map<String, Observation> observed = new HashMap<>();
        IrisMarker marker = new IrisMarker();
        IrisSpawner first = definition("qa/a", engine, marker, observed);
        IrisSpawner second = definition("qa/b", engine, marker, observed);
        KSet<IrisSpawner> forward = new KSet<>();
        forward.add(first);
        forward.add(second);
        KSet<IrisSpawner> reversed = new KSet<>();
        reversed.add(second);
        reversed.add(first);
        Method spawn = WorldEntitySpawner.class.getDeclaredMethod("spawnPreparedMarker", PreparedMarkerSpawn.class, boolean.class);
        spawn.setAccessible(true);
        WorldEntitySpawner spawner = new WorldEntitySpawner(manager);
        for (int x = -32; x < 32; x++) {
            IrisPosition position = new IrisPosition(x * 16 + 1, -49, -x * 16 + 3);
            spawn.invoke(spawner, new PreparedMarkerSpawn(position, forward, environment, marker), true);
        }
        Map<String, Observation> expected = new HashMap<>(observed);
        observed.clear();
        for (int x = 31; x >= -32; x--) {
            RNG.r.nextLong();
            IrisPosition position = new IrisPosition(x * 16 + 1, -49, -x * 16 + 3);
            spawn.invoke(spawner, new PreparedMarkerSpawn(position, reversed, environment, marker), true);
        }
        assertEquals(expected, observed);
        assertEquals(64, observed.size());
        assertTrue(observed.values().stream().allMatch(Observation::initial));
        verify(first, never()).canSpawn(any(Engine.class), anyInt(), anyInt());
        verify(second, never()).canSpawn(any(Engine.class), anyInt(), anyInt());
        verify(first, never()).spawn(any(Engine.class), anyInt(), anyInt());
        verify(second, never()).spawn(any(Engine.class), anyInt(), anyInt());
    }

    private static IrisSpawner definition(String key, Engine engine, IrisMarker marker, Map<String, Observation> observed) {
        IrisSpawner spawner = mock(IrisSpawner.class);
        IrisEntitySpawn entry = mock(IrisEntitySpawn.class);
        when(spawner.getLoadKey()).thenReturn(key);
        when(spawner.getInitialSpawns()).thenReturn(new KList<>(entry));
        when(entry.getRarity()).thenReturn(1);
        when(entry.spawn(eq(engine), any(IrisPosition.class), any(RNG.class), any(SpawnContext.class)))
                .thenAnswer(invocation -> {
                    IrisPosition position = invocation.getArgument(1);
                    RNG rng = invocation.getArgument(2);
                    SpawnContext context = invocation.getArgument(3);
                    assertEquals(marker, context.marker());
                    observed.put(position.getX() + "," + position.getY() + "," + position.getZ(),
                            new Observation(context.spawner().getLoadKey(), rng.getSeed(), context.initial()));
                    return 1;
                });
        return spawner;
    }

    private record Observation(String definition, long seed, boolean initial) {
    }
}
