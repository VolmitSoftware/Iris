package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.junit.Assert.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerUndergroundOwnershipTest {
    @Test
    public void cavePoolUsesActualCandidateBiomeAndPlacesInsideItsVolume() throws Exception {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisWorldManager manager = mock(IrisWorldManager.class);
        when(manager.getEngine()).thenReturn(engine);
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("basin").setBiome("basin-biome").setProbability(1);
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 1191L, -64, 320));
        SubterrainPlan plan = planner.plansForBounds(0, 0, 512, 512).getFirst();
        SubterrainPosition anchor = plan.anchor();
        Location candidate = new Location(world, anchor.x(), anchor.y(), anchor.z());
        IrisBiome underground = new IrisBiome();
        IrisDimension dimension = new IrisDimension().setSubterrainFeatures(new KList<>(feature));
        IrisRegion region = new IrisRegion().setEntitySpawners(new KList<>("cave-residents"));
        IrisRegion stackedRegion = new IrisRegion().setEntitySpawners(new KList<>("wrong-pack-residents"));
        IrisComplex complex = mock(IrisComplex.class);
        DimensionStackContext stack = mock(DimensionStackContext.class);
        DimensionStackLayout layout = mock(DimensionStackLayout.class);
        DimensionStackLayout.Layer layer = mock(DimensionStackLayout.Layer.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<IrisRegion> regionStream = mock(ProceduralStream.class);
        doReturn(complex).when(engine).getComplex();
        doReturn(dimension).when(engine).getDimension();
        doReturn(planner).when(complex).getSubterrainPlanner();
        doReturn(regionStream).when(complex).getRegionStream();
        when(regionStream.get(anchor.x(), anchor.z())).thenReturn(region);
        doReturn(stack).when(engine).getDimensionStackContext();
        when(stack.getLayout(anchor.x(), anchor.z())).thenReturn(layout);
        when(layout.layerAt(anyInt())).thenReturn(layer);
        when(layer.region()).thenReturn(stackedRegion);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
        doReturn(data).when(engine).getData();
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(biomeLoader.load("basin-biome")).thenReturn(underground);
        when(data.getSpawnerLoader()).thenReturn(loader);
        when(loader.loadAll(dimension.getEntitySpawners())).thenReturn(new KList<>());
        when(loader.loadAll(underground.getEntitySpawners())).thenReturn(new KList<>());
        IrisEntitySpawn entry = mock(IrisEntitySpawn.class);
        IrisEntity entity = mock(IrisEntity.class);
        when(entity.getSurface()).thenReturn(IrisSurface.LAND);
        when(entry.getRealEntity(engine)).thenReturn(entity);
        when(entry.getRarity()).thenReturn(1);
        IrisSpawner spawner = new IrisSpawner().setGroup(IrisSpawnGroup.CAVE).setInitialSpawns(new KList<>(entry));
        when(loader.loadAll(region.getEntitySpawners())).thenReturn(new KList<>(spawner));
        BiomeEnvironment environment = engine.getBiomeOrMantleEnvironment(anchor.x(), anchor.y() + 64, anchor.z());
        assertSame(underground, environment.biome());
        assertSame(region, environment.region());
        BiomeEnvironment.Scope scope = mock(BiomeEnvironment.Scope.class);
        when(engine.openBiomeEnvironmentScope(environment)).thenReturn(scope);
        try (MockedStatic<J> scheduler = mockStatic(J.class);
             MockedStatic<IrisEntitySpawn> positions = mockStatic(IrisEntitySpawn.class)) {
            positions.when(() -> IrisEntitySpawn.findLiveCaveSpawnLocation(eq(chunk), any(RNG.class), eq(IrisSurface.LAND)))
                    .thenReturn(candidate);
            Method method = WorldEntitySpawner.class.getDeclaredMethod("spawnUnderground",
                    Chunk.class, boolean.class, WorldEntitySpawner.ChunkCounter.class, RNG.class);
            method.setAccessible(true);
            method.invoke(new WorldEntitySpawner(manager), chunk, true, new WorldEntitySpawner.ChunkCounter(new org.bukkit.entity.Entity[0]), new RNG(17719L));
        }
        verify(loader, never()).loadAll(stackedRegion.getEntitySpawners());
        verify(engine, never()).getSurfaceBiomeEnvironment(anchor.x(), anchor.z());
        assertSame(stackedRegion, engine.getRegion(anchor.x(), plan.bounds().maxY() + 65, anchor.z()));
        verify(entry).spawn(eq(engine), eq(candidate), any(RNG.class), eq(spawner.getMaxEntitiesPerChunk()),
                any(IrisEntitySpawn.SpawnContext.class));
        verify(scope).close();
    }
}
