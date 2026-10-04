package art.arcane.iris.modded;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnQueries;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedUndergroundSpawnOwnershipTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void candidateSamplingAndPoolSelectionUseAbsoluteUndergroundOwner() throws Exception {
        NativeWorld world = mock(NativeWorld.class);
        when(world.minHeight()).thenReturn(-64);
        when(world.maxHeight()).thenReturn(320);
        NativeBlockState stone = mock(NativeBlockState.class);
        NativeBlockState air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(1) <= -21 ? stone : air);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        IrisBiome cave = new IrisBiome().setEntitySpawners(new KList<>("residents"));
        IrisDimension dimension = new IrisDimension();
        IrisRegion region = new IrisRegion();
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
        when(data.getSpawnerLoader()).thenReturn(loader);
        when(loader.loadAll(dimension.getEntitySpawners())).thenReturn(new KList<>());
        when(loader.loadAll(region.getEntitySpawners())).thenReturn(new KList<>());
        IrisEntitySpawn entry = mock(IrisEntitySpawn.class);
        IrisEntity entity = mock(IrisEntity.class);
        when(entry.getRealEntity(engine)).thenReturn(entity);
        when(entry.getRarity()).thenReturn(1);
        when(entry.getMinSpawns()).thenReturn(1);
        when(entry.getMaxSpawns()).thenReturn(1);
        when(entity.getSurface()).thenReturn(IrisSurface.LAND);
        when(entity.spawnCategory()).thenReturn("creature");
        IrisSpawner spawner = new IrisSpawner().setGroup(IrisSpawnGroup.CAVE).setInitialSpawns(new KList<>(entry));
        when(loader.loadAll(cave.getEntitySpawners())).thenReturn(new KList<>(spawner));
        BiomeEnvironment environment = new BiomeEnvironment(9L, cave, region, dimension, data);
        when(engine.getBiomeOrMantleEnvironment(anyInt(), eq(44), anyInt())).thenReturn(environment);
        BiomeEnvironment.Scope scope = mock(BiomeEnvironment.Scope.class);
        when(engine.openBiomeEnvironmentScope(environment)).thenReturn(scope);
        NativeEntityRuntime runtime = mock(NativeEntityRuntime.class);
        when(runtime.world()).thenReturn(world);
        ModdedWorldManager manager = new ModdedWorldManager(engine);
        try (MockedStatic<NativeSpawnQueries> queries = mockStatic(NativeSpawnQueries.class);
             MockedStatic<ModdedEntitySpawner> entities = mockStatic(ModdedEntitySpawner.class)) {
            queries.when(() -> NativeSpawnQueries.surfaceHeight(eq(world), anyInt(), anyInt(), eq(true))).thenReturn(64);
            queries.when(() -> NativeSpawnQueries.solid(stone)).thenReturn(true);
            IrisPosition first = ModdedWorldManager.findUndergroundCandidate(world, 2, -1, new RNG(88771L), IrisSurface.LAND);
            IrisPosition second = ModdedWorldManager.findUndergroundCandidate(world, 2, -1, new RNG(88771L), IrisSurface.LAND);
            assertNotNull(first);
            assertEquals(first, second);
            assertEquals(-20, first.getY());
            assertTrue(first.getX() >= 33 && first.getX() <= 47 && first.getZ() >= -15 && first.getZ() <= -1);
            Field field = ModdedWorldManager.class.getDeclaredField("entityRuntime");
            field.setAccessible(true);
            field.set(manager, runtime);
            entities.when(() -> ModdedEntitySpawner.isAreaClearForSpawn(eq(runtime), eq(entity), anyInt(), eq(-20), anyInt()))
                    .thenReturn(true);
            Method spawn = ModdedWorldManager.class.getDeclaredMethod("spawnUnderground", NativeWorld.class,
                    int.class, int.class, boolean.class, Map.class, RNG.class);
            spawn.setAccessible(true);
            spawn.invoke(manager, world, 2, -1, true, Map.of(), new RNG(88771L));
            entities.verify(() -> ModdedEntitySpawner.spawn(eq(engine), eq(entity), eq(runtime), anyInt(), eq(-20), anyInt(), any(RNG.class)));
        } finally {
            manager.close();
        }
        verify(scope).close();
    }
}
