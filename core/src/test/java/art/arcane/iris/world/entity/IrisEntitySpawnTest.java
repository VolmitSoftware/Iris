package art.arcane.iris.world.entity;

import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Vector3d;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class IrisEntitySpawnTest {
    @Test
    public void caveSpawnWithoutSafeMarkerIsSkipped() {
        assertNull(IrisEntitySpawn.selectCaveSpawnLocation(new KList<>(), null, new RNG(1L)));
    }

    @Test
    public void mixedNormalSpawnerPlacesEveryTadpoleInsideOneBlockDeepWater() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 77, 78))) {
            assertEquals(3, fixture.spawn(IrisSpawnGroup.NORMAL));
            assertEquals(3, fixture.positions.size());
            assertTrue(fixture.positions.stream().allMatch(position -> position.getY() == 78.5));
        }
    }

    @Test
    public void underwaterSpawnerAcceptsWaterInsteadOfRequiringAir() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 65, 78))) {
            assertEquals(3, fixture.spawn(IrisSpawnGroup.UNDERWATER));
            assertTrue(fixture.positions.stream().allMatch(position -> position.getBlockY() > 65
                    && position.getBlockY() <= 78));
        }
    }

    @Test
    public void waterEntityCannotSpawnInAirAboveWater() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 77, 78))) {
            assertNull(fixture.entry.spawn(fixture.engine, new Location(fixture.world, 0, 79, 0)));
            assertTrue(fixture.positions.isEmpty());
        }
    }

    @Test
    public void dryColumnsDoNotSpawnWaterEntities() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 78, 78))) {
            assertEquals(0, fixture.spawn(IrisSpawnGroup.NORMAL));
            assertEquals(0, fixture.spawn(IrisSpawnGroup.UNDERWATER));
        }
    }

    @Test
    public void mixedNormalSpawnerRetainsLandEntityPlacement() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            assertEquals(3, fixture.spawn(IrisSpawnGroup.NORMAL));
            assertTrue(fixture.positions.stream().allMatch(position -> position.getY() == 79.5));
        }
    }

    @Test
    public void lavaEntityOccupiesLavaAndRejectsWater() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAVA, Material.LAVA, 77, 78))) {
            assertEquals(3, fixture.spawn(IrisSpawnGroup.NORMAL));
        }
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAVA, Material.WATER, 77, 78))) {
            assertEquals(0, fixture.spawn(IrisSpawnGroup.NORMAL));
        }
    }

    @Test
    public void fluidVolumeIncludesTheCenteredEntityHeight() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 77, 78))) {
            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(0.4, 0.8, 0.4));
            assertNull(fixture.entry.spawn(fixture.engine, new Location(fixture.world, 0, 78, 0)));
            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(0.4, 0.5, 0.4));
            fixture.entry.spawn(fixture.engine, new Location(fixture.world, 0, 78, 0));
            assertEquals(1, fixture.positions.size());
        }
    }

    @Test
    public void fluidVolumeRejectsWaterloggedSolidObstructionBesideWideEntity() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 77, 79))) {
            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(1.3, 0.8, 1.3));
            Block obstruction = mock(Block.class);
            Waterlogged data = mock(Waterlogged.class);
            when(data.isWaterlogged()).thenReturn(true);
            when(obstruction.getBlockData()).thenReturn(data);
            when(obstruction.getType()).thenReturn(Material.OAK_FENCE);
            when(obstruction.isSolid()).thenReturn(true);
            when(fixture.world.getBlockAt(-2, 78, -1)).thenReturn(obstruction);
            assertNull(fixture.entry.spawn(fixture.engine, new Location(fixture.world, -1, 78, -1)));
        }
    }

    @Test
    public void waterloggedNonSolidPlantsCountAsWater() {
        Block block = mock(Block.class);
        Waterlogged data = mock(Waterlogged.class);
        when(block.getType()).thenReturn(Material.SEA_PICKLE);
        when(block.getBlockData()).thenReturn(data);
        when(data.isWaterlogged()).thenReturn(true);
        assertTrue(IrisSurface.WATER.matches(block));
    }

    @Test
    public void markerWaterSpawnsStillRequireSubmergedBodies() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.WATER, Material.WATER, 77, 78));
             MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class)) {
            binding.when(() -> BukkitWorldBinding.tryBind(null)).thenReturn(true);
            binding.when(() -> BukkitWorldBinding.world(null)).thenReturn(fixture.world);
            fixture.platform.when(() -> BukkitPlatform.toLocation(any(IrisPosition.class), eq(fixture.world)))
                    .thenAnswer(invocation -> {
                        IrisPosition position = invocation.getArgument(0);
                        return new Location(fixture.world, position.getX(), position.getY(), position.getZ());
                    });
            fixture.entry.setReferenceSpawner(new IrisSpawner());
            assertEquals(0, fixture.entry.spawn(fixture.engine, new IrisPosition(0, 78, 0), new RNG(1L)));
            assertEquals(3, fixture.entry.spawn(fixture.engine, new IrisPosition(0, 77, 0), new RNG(1L)));
        }
    }

    @Test
    public void repeatedExplicitSpawnsDoNotMoveTheCallerLocation() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            Location location = new Location(fixture.world, 0, 79, 0);
            fixture.entry.spawn(fixture.engine, location);
            fixture.entry.spawn(fixture.engine, location);
            assertEquals(new Location(fixture.world, 0, 79, 0), location);
            assertEquals(List.of(new Location(fixture.world, 0.5, 79.5, 0.5),
                    new Location(fixture.world, 0.5, 79.5, 0.5)), fixture.positions);
        }
    }

    private record SpawnOptions(IrisSurface surface, Material fluid, int floor, int top) {
    }

    private static final class SpawnFixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final Chunk chunk = mock(Chunk.class);
        private final IrisEntitySpawn entry = spy(new IrisEntitySpawn());
        private final List<Location> positions = new ArrayList<>();
        private final MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);

        private SpawnFixture(SpawnOptions options) {
            IrisSurface surface = options.surface();
            Material fluid = options.fluid();
            int floor = options.floor();
            int top = options.top();
            IrisEntity definition = mock(IrisEntity.class);
            when(definition.getSurface()).thenReturn(surface);
            when(definition.getBukkitType()).thenReturn(EntityType.TADPOLE);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(chunk.getWorld()).thenReturn(world);
            when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR))).thenReturn(floor);
            when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.WORLD_SURFACE))).thenReturn(top);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int y = invocation.getArgument(1);
                Block block = mock(Block.class);
                when(block.getType()).thenReturn(y <= floor ? Material.STONE : y <= top ? fluid : Material.AIR);
                when(block.isSolid()).thenReturn(y <= floor);
                return block;
            });
            when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
                Location location = invocation.getArgument(0);
                return world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            });
            when(definition.spawn(eq(engine), any(Location.class), any(RNG.class))).thenAnswer(invocation -> {
                Location location = invocation.getArgument(1);
                positions.add(location.clone());
                Entity spawned = mock(Entity.class);
                when(spawned.getLocation()).thenReturn(location.clone());
                when(spawned.getType()).thenReturn(EntityType.TADPOLE);
                return spawned;
            });
            platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(0.4, 0.3, 0.4));
            doReturn(definition).when(entry).getRealEntity(engine);
            entry.setMinSpawns(3).setMaxSpawns(3);
        }

        private int spawn(IrisSpawnGroup group) {
            entry.setReferenceSpawner(new IrisSpawner().setGroup(group));
            return entry.spawn(engine, chunk, new RNG(1337L));
        }

        @Override
        public void close() {
            platform.close();
        }
    }
}
