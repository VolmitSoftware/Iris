package art.arcane.iris.modded;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.world.entity.EntitySpawnSeed;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnQueries;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Rarity;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterMarker;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedInitialEntitySpawnTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void initialBatchesIgnoreChunkOrderRuntimeRandomPopulationLightAndCooldowns() {
        Map<Long, List<Spawned>> first;
        try (Fixture fixture = new Fixture()) {
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            RNG.r.nextLong();
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, 4, -5));
            first = fixture.byChunk();
            fixture.verifyInitialPolicy();
        }
        try (Fixture fixture = new Fixture()) {
            for (int i = 0; i < 64; i++) {
                RNG.r.nextLong();
            }
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, 4, -5));
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            assertEquals(first, fixture.byChunk());
            assertEquals(2, fixture.completed.size());
            for (List<Spawned> batch : fixture.byChunk().values()) {
                assertTrue(batch.size() >= 2 && batch.size() <= 3);
            }
            fixture.verifyInitialPolicy();
        }
    }

    @Test
    public void undergroundAndSurfacePassesShareTheSuccessfulPopulationBudget() {
        try (Fixture fixture = new Fixture()) {
            BiomeEnvironment environment = fixture.engine.getSurfaceBiomeEnvironment(0, 0);
            when(environment.dimension().hasUndergroundSpawners(fixture.engine)).thenReturn(true);
            when(fixture.engine.getBiomeOrMantleEnvironment(anyInt(), anyInt(), anyInt())).thenReturn(environment);
            NativeBlockState stone = mock(NativeBlockState.class);
            NativeBlockState air = mock(NativeBlockState.class);
            when(air.isAir()).thenReturn(true);
            when(fixture.world.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int y = invocation.getArgument(1);
                return y <= -21 || y == 50 ? stone : air;
            });
            fixture.queries.when(() -> NativeSpawnQueries.solid(stone)).thenReturn(true);
            IrisEntity entity = new IrisEntity().setType("minecraft:cow");
            IrisEntitySpawn entry = spy(new IrisEntitySpawn().setMinSpawns(2).setMaxSpawns(2));
            doReturn(entity).when(entry).getRealEntity(fixture.engine);
            fixture.definitions.add(new IrisSpawner().setGroup(IrisSpawnGroup.CAVE)
                    .setInitialSpawns(new KList<>(entry)).setMaxEntitiesPerChunk(3));
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            assertEquals(3, fixture.spawned.size());
            assertEquals(2, fixture.spawned.stream().filter(spawn -> spawn.y() == -20).count());
            assertEquals(1, fixture.spawned.stream().filter(spawn -> spawn.y() == 51).count());
            fixture.queries.verify(() -> NativeSpawnQueries.livingEntityCategories(eq(fixture.world), anyInt(), anyInt()), never());
        }
    }

    @Test
    public void initialCompletionFollowsOwnedSpawnPassAndDoesNotRepeatIt() {
        try (Fixture fixture = new Fixture()) {
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            int count = fixture.spawned.size();
            assertTrue(count > 0);
            assertEquals("complete", fixture.trace.getLast());
            assertTrue(fixture.trace.subList(0, fixture.trace.size() - 1).stream().allMatch("spawn"::equals));
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            assertEquals(count, fixture.spawned.size());
            assertEquals(1, fixture.completed.size());
        }
    }

    @Test
    public void unsafeChunkAndDisabledMobSpawningLeaveCompletionForRetry() {
        try (Fixture fixture = new Fixture()) {
            fixture.queries.when(() -> NativeSpawnQueries.ambientAllowed(fixture.world, -2, 3, true)).thenReturn(false);
            assertFalse(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            assertTrue(fixture.completed.isEmpty());
            fixture.queries.when(() -> NativeSpawnQueries.ambientAllowed(fixture.world, -2, 3, true)).thenReturn(true);
            fixture.entities.when(() -> ModdedEntitySpawner.chunksSafe(any(NativeEntityRuntime.class), eq(-2), eq(3)))
                    .thenReturn(false);
            assertFalse(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            assertTrue(fixture.completed.isEmpty());
            assertTrue(fixture.spawned.isEmpty());
            fixture.entities.when(() -> ModdedEntitySpawner.chunksSafe(any(NativeEntityRuntime.class), eq(-2), eq(3)))
                    .thenReturn(true);
            assertTrue(fixture.manager.initialSpawnChunk(fixture.world, -2, 3));
            assertTrue(fixture.completed.contains(key(-2, 3)));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void initialMarkerSelectionUsesOneStreamAndExcludesDuplicateAndUnsupportedSpawners() throws ReflectiveOperationException {
        try (Fixture fixture = new Fixture()) {
            IrisSpawner first = fixture.definitions.get(0);
            IrisSpawner second = fixture.definitions.get(1);
            first.setLoadKey("a");
            second.setLoadKey("b");
            IrisSpawner excluded = new IrisSpawner();
            excluded.setLoadKey("excluded");
            excluded.setCompat(CompatStatus.excludedBy(List.of()));
            IrisMarker marker = new IrisMarker().setSpawners(new KList<>("b", "excluded", "a", "b"))
                    .setEmptyAbove(false).setExhaustionChance(0.5);
            BiomeEnvironment environment = fixture.engine.getSurfaceBiomeEnvironment(0, 0);
            ResourceLoader<IrisSpawner> spawners = environment.data().getSpawnerLoader();
            when(spawners.load("a")).thenReturn(first);
            when(spawners.load("b")).thenReturn(second);
            when(spawners.load("excluded")).thenReturn(excluded);
            ResourceLoader<IrisMarker> markers = mock(ResourceLoader.class);
            when(environment.data().getMarkerLoader()).thenReturn(markers);
            when(markers.load("test")).thenReturn(marker);
            when(fixture.engine.getWorld().minHeight()).thenReturn(-64);
            when(fixture.engine.getBiomeEnvironment(-25, 15, 57)).thenReturn(environment);
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            doAnswer(invocation -> {
                Consumer4<Integer, Integer, Integer, MatterMarker> callback = invocation.getArgument(1);
                callback.accept(7, 15, 9, new MatterMarker("test"));
                return null;
            }).when(chunk).iterate(eq(MatterMarker.class), any());
            Method resolve = ModdedWorldManager.class.getDeclaredMethod("resolveMarkerSpawners", IrisMarker.class, BiomeEnvironment.class);
            resolve.setAccessible(true);
            KList<IrisSpawner> resolved = (KList<IrisSpawner>) resolve.invoke(fixture.manager, marker, environment);
            assertEquals(List.of(second, first), resolved);
            Method prepare = ModdedWorldManager.class.getDeclaredMethod("prepareMarkerSpawns", NativeWorld.class,
                    int.class, int.class, MantleChunk.class, boolean.class);
            prepare.setAccessible(true);
            Object prepared = prepare.invoke(fixture.manager, fixture.world, -2, 3, chunk, true);
            Method spawn = ModdedWorldManager.class.getDeclaredMethod("spawnPreparedMarkers", NativeWorld.class, List.class, boolean.class);
            spawn.setAccessible(true);
            spawn.invoke(fixture.manager, fixture.world, prepared, true);

            RNG rng = EntitySpawnSeed.marker(7721L, -25, -49, 57);
            IrisSpawner selected = new KList<>(first, second).getRandom(rng);
            IrisEntitySpawn entry = Rarity.expandWeighted(selected.getInitialSpawns()).getRandom(rng);
            int count = LootResolver.inclusive(rng, entry.getMinSpawns(), entry.getMaxSpawns());
            List<Spawned> expected = new ArrayList<>();
            for (int ordinal = 0; ordinal < count; ordinal++) {
                expected.add(new Spawned(entry.getRealEntity(fixture.engine).getType(), -25, -48, 57,
                        EntitySpawnSeed.entity(rng.getSeed(), ordinal).nextLong()));
            }
            assertEquals(expected, fixture.spawned);
            Mantle<Matter> mantle = fixture.engine.getMantle().getMantle();
            if (marker.shouldExhaust(EntitySpawnSeed.entity(rng.getSeed(), -1))) {
                verify(mantle).remove(-25, 15, 57, MatterMarker.class);
            } else {
                verify(mantle, never()).remove(anyInt(), anyInt(), anyInt(), eq(MatterMarker.class));
            }
        }
    }

    private static long key(int x, int z) {
        return (long) x & 0xFFFFFFFFL | (long) z << 32;
    }

    private record Spawned(String type, int x, int y, int z, long customization) {
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class);
        private final NativeWorld world = mock(NativeWorld.class);
        private final ModdedWorldManager manager = new ModdedWorldManager(engine);
        private final MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class);
        private final MockedStatic<NativeSpawnQueries> queries = mockStatic(NativeSpawnQueries.class);
        private final MockedStatic<ModdedEntitySpawner> entities = mockStatic(ModdedEntitySpawner.class);
        private final List<Spawned> spawned = new ArrayList<>();
        private final List<String> trace = new ArrayList<>();
        private final Set<Long> completed = new HashSet<>();
        private final List<IrisSpawner> definitions = new ArrayList<>();

        @SuppressWarnings("unchecked")
        private Fixture() {
            IrisSettings settings = new IrisSettings();
            settings.getWorld().setAmbientEntitySpawningSystem(true);
            settings.getWorld().setMarkerEntitySpawningSystem(false);
            configured.when(IrisSettings::get).thenReturn(settings);
            EngineMantle engineMantle = mock(EngineMantle.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            IrisWorld irisWorld = mock(IrisWorld.class);
            SeedManager seeds = mock(SeedManager.class);
            when(seeds.getEntity()).thenReturn(7721L);
            when(engine.getSeedManager()).thenReturn(seeds);
            when(engine.getWorld()).thenReturn(irisWorld);
            when(engine.getComplex()).thenReturn(mock(IrisComplex.class));
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getMantle()).thenReturn(mantle);
            when(world.nativeHandle()).thenReturn(mock(ServerLevel.class));
            when(world.minHeight()).thenReturn(-64);
            when(world.maxHeight()).thenReturn(320);
            NativeBlockState support = mock(NativeBlockState.class);
            when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(support);
            queries.when(() -> NativeSpawnQueries.solid(support)).thenReturn(true);
            queries.when(() -> NativeSpawnQueries.ambientAllowed(eq(world), anyInt(), anyInt(), anyBoolean())).thenReturn(true);
            queries.when(() -> NativeSpawnQueries.surfaceHeight(eq(world), anyInt(), anyInt(), anyBoolean())).thenReturn(50);
            queries.when(() -> NativeSpawnQueries.livingEntityCategories(eq(world), anyInt(), anyInt()))
                    .thenReturn(Map.of("creature", 1000));
            queries.when(() -> NativeSpawnQueries.light(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(15);
            entities.when(() -> ModdedEntitySpawner.chunksSafe(any(NativeEntityRuntime.class), anyInt(), anyInt())).thenReturn(true);
            entities.when(() -> ModdedEntitySpawner.isAreaClearForSpawn(any(NativeEntityRuntime.class),
                    any(IrisEntity.class), anyInt(), anyInt(), anyInt())).thenReturn(true);
            entities.when(() -> ModdedEntitySpawner.spawn(eq(engine), any(IrisEntity.class), any(NativeEntityRuntime.class),
                    anyInt(), anyInt(), anyInt(), any(RNG.class))).thenAnswer(invocation -> {
                IrisEntity entity = invocation.getArgument(1);
                RNG rng = invocation.getArgument(6);
                spawned.add(new Spawned(entity.getType(), invocation.getArgument(3), invocation.getArgument(4),
                        invocation.getArgument(5), rng.nextLong()));
                trace.add("spawn");
                return mock(NativeSpawnedEntity.class);
            });
            when(mantle.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(mantle.hasFlag(anyInt(), anyInt(), eq(ModdedWorldManager.INITIAL_SPAWN_COMPLETION_FLAG)))
                    .thenAnswer(invocation -> completed.contains(key(invocation.getArgument(0), invocation.getArgument(1))));
            when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(invocation -> {
                long key = key(invocation.getArgument(0), invocation.getArgument(1));
                MantleChunk<Matter> chunk = mock(MantleChunk.class);
                doAnswer(flag -> {
                    completed.add(key);
                    trace.add("complete");
                    return null;
                }).when(chunk).raiseFlagUnchecked(eq(ModdedWorldManager.INITIAL_SPAWN_COMPLETION_FLAG), any());
                return chunk;
            });
            IrisData data = mock(IrisData.class);
            ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
            when(data.getSpawnerLoader()).thenReturn(loader);
            IrisBiome biome = mock(IrisBiome.class);
            when(biome.getInferredType()).thenReturn(InferredType.LAND);
            when(biome.getEntitySpawners()).thenReturn(new KList<>());
            IrisRegion region = mock(IrisRegion.class);
            when(region.getEntitySpawners()).thenReturn(new KList<>());
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.getEntitySpawners()).thenReturn(new KList<>("land"));
            for (String type : List.of("minecraft:cow", "minecraft:pig")) {
                IrisEntity entity = new IrisEntity().setType(type);
                IrisEntitySpawn entry = spy(new IrisEntitySpawn().setMinSpawns(2).setMaxSpawns(5));
                doReturn(entity).when(entry).getRealEntity(engine);
                IrisSpawner spawner = spy(new IrisSpawner().setInitialSpawns(new KList<>(entry))
                        .setMaxEntitiesPerChunk(3).setAllowedLightLevels(new IrisRange(0, 0)));
                doReturn(false).when(spawner).canSpawn(eq(engine), anyInt(), anyInt());
                definitions.add(spawner);
            }
            when(loader.loadAll(any(KList.class))).thenAnswer(invocation -> {
                KList<String> names = invocation.getArgument(0);
                return names.contains("land") ? new KList<>(definitions) : new KList<>();
            });
            BiomeEnvironment environment = new BiomeEnvironment(1L, biome, region, dimension, data);
            when(engine.getSurfaceBiomeEnvironment(anyInt(), anyInt())).thenReturn(environment);
            when(engine.openBiomeEnvironmentScope(environment)).thenReturn(mock(BiomeEnvironment.Scope.class));
        }

        private Map<Long, List<Spawned>> byChunk() {
            Map<Long, List<Spawned>> output = new HashMap<>();
            for (Spawned spawn : spawned) {
                output.computeIfAbsent(key(spawn.x() >> 4, spawn.z() >> 4), ignored -> new ArrayList<>()).add(spawn);
            }
            return output;
        }

        private void verifyInitialPolicy() {
            queries.verify(() -> NativeSpawnQueries.livingEntityCategories(eq(world), anyInt(), anyInt()), never());
            queries.verify(() -> NativeSpawnQueries.light(eq(world), anyInt(), anyInt(), anyInt()), never());
            for (IrisSpawner spawner : definitions) {
                verify(spawner, never()).canSpawn(eq(engine), anyInt(), anyInt());
                verify(spawner, never()).spawn(eq(engine), anyInt(), anyInt());
            }
        }

        @Override
        public void close() {
            manager.close();
            entities.close();
            queries.close();
            configured.close();
        }
    }
}
