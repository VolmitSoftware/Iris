package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisEntitySpawn.SpawnContext;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.junit.Test;
import org.junit.BeforeClass;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerInitialSeedTest {
    @BeforeClass
    public static void initializeGameRules() throws Exception {
        WorldEntitySpawnerAdmissionTest.initializeGameRules();
    }

    @Test
    public void initialPopulationUsesAuthoredCapacityWithoutLiveGatesOrPopulationReads() throws Exception {
        Fixture fixture = new Fixture();
        fixture.spawn(2, -3);

        assertEquals(1, fixture.observed.size());
        Observation observation = fixture.observed.get("2,-3");
        assertEquals(4, observation.capacity());
        assertTrue(observation.initial());
        verify(fixture.definition, never()).canSpawn(any(Engine.class), anyInt(), anyInt());
        verify(fixture.definition, never()).spawn(any(Engine.class), anyInt(), anyInt());
        verify(fixture.lastChunk, never()).getEntities();
    }

    @Test
    public void chunkBatchSeedsAreIndependentOfVisitOrderAndUnrelatedRandomActivity() throws Exception {
        Fixture fixture = new Fixture();
        for (int coordinate = -16; coordinate < 16; coordinate++) {
            fixture.spawn(coordinate, -coordinate - 1);
        }
        Map<String, Observation> forward = new HashMap<>(fixture.observed);
        fixture.observed.clear();
        for (int coordinate = 15; coordinate >= -16; coordinate--) {
            for (int draw = 0; draw < 19; draw++) {
                RNG.r.nextLong();
            }
            fixture.spawn(coordinate, -coordinate - 1);
        }
        assertEquals(forward, fixture.observed);
        assertEquals(32, forward.values().stream().map(Observation::seed).distinct().count());
    }

    @Test
    public void initialSpawnerIgnoresCooldownButExcludedContentIsNeverAdmitted() {
        Engine engine = mock(Engine.class);
        IrisSpawner definition = mock(IrisSpawner.class);
        when(definition.canSpawn(engine, 1, 2)).thenReturn(false);
        assertTrue(WorldEntitySpawner.spawnerAllowed(definition, engine, 1, 2, true));
        assertFalse(WorldEntitySpawner.spawnerAllowed(definition, engine, 1, 2, false));
        when(definition.isCompatExcluded()).thenReturn(true);
        assertFalse(WorldEntitySpawner.spawnerAllowed(definition, engine, 1, 2, true));
    }

    @SuppressWarnings("unchecked")
    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final IrisWorldManager manager = mock(IrisWorldManager.class);
        private final World world = mock(World.class);
        private final IrisSpawner definition = mock(IrisSpawner.class);
        private final IrisEntitySpawn entry = mock(IrisEntitySpawn.class);
        private final Map<String, Observation> observed = new HashMap<>();
        private final BiomeEnvironment environment;
        private final Method spawn;
        private final WorldEntitySpawner spawner;
        private Chunk lastChunk;

        private Fixture() throws Exception {
            SeedManager seeds = mock(SeedManager.class);
            when(seeds.getEntity()).thenReturn(918273645L);
            when(engine.getSeedManager()).thenReturn(seeds);
            when(manager.getEngine()).thenReturn(engine);
            IrisData data = mock(IrisData.class);
            ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
            when(data.getSpawnerLoader()).thenReturn(loader);
            KList<String> authored = new KList<>("qa/initial");
            when(loader.loadAll(any(KList.class))).thenReturn(new KList<>());
            when(loader.loadAll(authored)).thenReturn(new KList<>(definition));
            IrisBiome biome = mock(IrisBiome.class);
            IrisRegion region = mock(IrisRegion.class);
            IrisDimension dimension = mock(IrisDimension.class);
            when(biome.getEntitySpawners()).thenReturn(authored);
            when(region.getEntitySpawners()).thenReturn(new KList<>());
            when(dimension.getEntitySpawners()).thenReturn(new KList<>());
            when(definition.getInitialSpawns()).thenReturn(new KList<>(entry));
            when(definition.getMaxEntitiesPerChunk()).thenReturn(4);
            when(entry.getRarity()).thenReturn(1);
            when(entry.getRealEntity(engine)).thenReturn(mock(IrisEntity.class));
            when(world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(true);
            when(entry.spawn(eq(engine), any(Chunk.class), any(RNG.class), anyInt(), any(SpawnContext.class)))
                    .thenAnswer(invocation -> {
                        Chunk chunk = invocation.getArgument(1);
                        RNG rng = invocation.getArgument(2);
                        int capacity = invocation.getArgument(3);
                        SpawnContext context = invocation.getArgument(4);
                        observed.put(chunk.getX() + "," + chunk.getZ(), new Observation(rng.getSeed(), capacity, context.initial()));
                        return 4;
                    });
            environment = new BiomeEnvironment(1L, biome, region, dimension, data);
            spawner = new WorldEntitySpawner(manager);
            spawn = WorldEntitySpawner.class.getDeclaredMethod("spawnAmbient", Chunk.class, boolean.class, BiomeEnvironment.class);
            spawn.setAccessible(true);
        }

        private void spawn(int x, int z) throws Exception {
            lastChunk = mock(Chunk.class);
            when(lastChunk.getWorld()).thenReturn(world);
            when(lastChunk.getX()).thenReturn(x);
            when(lastChunk.getZ()).thenReturn(z);
            spawn.invoke(spawner, lastChunk, true, environment);
        }
    }

    private record Observation(long seed, int capacity, boolean initial) {
    }
}
