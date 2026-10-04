package art.arcane.iris.world.entity;

import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.pack.value.IrisRange;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atMost;

public class IrisEntitySpawnTest {
    @Test
    public void caveSpawnWithoutSafeMarkerIsSkipped() {
        assertNull(IrisEntitySpawn.selectCaveSpawnLocation(new KList<>(), null, new RNG(1L)));
    }

    @Test
    public void caveFloorMarkerSpawnsAtFirstAirBlockInNegativeWorldHeight() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, -50, -50));
             MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFolia).thenReturn(false);
            when(fixture.world.getMinHeight()).thenReturn(-64);
            EngineMantle mantle = mock(EngineMantle.class);
            when(fixture.engine.getMantle()).thenReturn(mantle);
            when(mantle.findMarkers(0, 0, MarkerMatter.CAVE_FLOOR))
                    .thenReturn(new KList<>(new IrisPosition(7, -49, 9)));
            fixture.platform.when(() -> BukkitPlatform.toLocation(any(IrisPosition.class), eq(fixture.world)))
                    .thenAnswer(invocation -> {
                        IrisPosition position = invocation.getArgument(0);
                        return new Location(fixture.world, position.getX(), position.getY(), position.getZ());
                    });

            assertEquals(3, fixture.spawn(IrisSpawnGroup.CAVE));

            assertEquals(List.of(new Location(fixture.world, 7.5, -49, 9.5),
                    new Location(fixture.world, 7.5, -49, 9.5),
                    new Location(fixture.world, 7.5, -49, 9.5)), fixture.positions);
        }
    }

    @Test
    public void foliaCaveSpawnUsesOwnedLiveBlocksAndNegativeWorldHeights() {
        Engine engine = mock(Engine.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(-2);
        when(chunk.getZ()).thenReturn(3);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR))).thenReturn(0);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(y == -49 || y == -48 ? Material.CAVE_AIR : Material.STONE);
            when(block.isSolid()).thenReturn(y != -49 && y != -48);
            return block;
        });
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFolia).thenReturn(true);
            scheduler.when(() -> J.isOwnedByCurrentRegion(world, -2, 3)).thenReturn(true);

            Location location = IrisEntitySpawn.findCaveSpawnLocation(engine, chunk, new RNG(1L), IrisSurface.LAND);

            assertEquals(-49, location.getBlockY());
            assertEquals(-2, location.getBlockX() >> 4);
            assertEquals(3, location.getBlockZ() >> 4);
            verify(engine, never()).getMantle();
            verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void foliaCaveGroupSpawnsEntitiesInsideLiveCaveAir() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78));
             MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFolia).thenReturn(true);
            scheduler.when(() -> J.isOwnedByCurrentRegion(fixture.world, 0, 0)).thenReturn(true);
            when(fixture.world.getMinHeight()).thenReturn(-64);
            when(fixture.world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR))).thenReturn(0);
            when(fixture.world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int y = invocation.getArgument(1);
                Block block = mock(Block.class);
                boolean air = y == -49 || y == -48;
                when(block.getType()).thenReturn(air ? Material.CAVE_AIR : Material.STONE);
                when(block.isSolid()).thenReturn(!air);
                return block;
            });
            when(fixture.chunk.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    fixture.world.getBlockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(0.6, 1.95, 0.6));

            assertEquals(3, fixture.spawn(IrisSpawnGroup.CAVE));

            assertTrue(fixture.positions.stream().allMatch(position -> position.getY() == -49D));
            verify(fixture.engine, never()).getMantle();
        }
    }

    @Test
    public void foliaCaveScanDoesNotReadUnownedChunks() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFolia).thenReturn(true);
            assertNull(IrisEntitySpawn.findCaveSpawnLocation(mock(Engine.class), chunk, new RNG(1L), IrisSurface.LAND));
            verify(chunk, never()).getBlock(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void caveScanIsBoundedWhenNoSafeFloorExists() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(4096);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR))).thenReturn(4000);
        Block solid = mock(Block.class);
        when(solid.getType()).thenReturn(Material.STONE);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(solid);

        assertNull(IrisEntitySpawn.findLiveCaveSpawnLocation(chunk, new RNG(1L), IrisSurface.LAND));

        verify(chunk, times(1024)).getBlock(anyInt(), anyInt(), anyInt());
        verify(world, atMost(8)).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR));
    }

    @Test
    public void foliaFluidCaveGroupsSpawnInsideMatchingNegativeHeightFluidWithoutSolidSupport() {
        for (IrisSurface surface : List.of(IrisSurface.WATER, IrisSurface.LAVA)) {
            Material fluid = surface == IrisSurface.WATER ? Material.WATER : Material.LAVA;
            try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(surface, fluid, -65, -1));
                 MockedStatic<J> scheduler = mockStatic(J.class)) {
                scheduler.when(J::isFolia).thenReturn(true);
                scheduler.when(() -> J.isOwnedByCurrentRegion(fixture.world, 0, 0)).thenReturn(true);
                when(fixture.world.getMinHeight()).thenReturn(-64);
                when(fixture.world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR)))
                        .thenReturn(0);
                when(fixture.chunk.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                        fixture.world.getBlockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

                assertEquals(surface.name(), 3, fixture.spawn(IrisSpawnGroup.CAVE));

                for (Location position : fixture.positions) {
                    assertTrue(position.getY() < 0);
                    assertEquals(fluid, position.getBlock().getType());
                    assertEquals(fluid, position.clone().subtract(0, 1, 0).getBlock().getType());
                }
                verify(fixture.engine, never()).getMantle();
            }
        }
    }

    @Test
    public void fluidCaveScannerRejectsOtherFluidsAndDryFloorScannerRejectsFluid() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR))).thenReturn(0);
        Block water = mock(Block.class);
        when(water.getType()).thenReturn(Material.WATER);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(water);

        assertNull(IrisEntitySpawn.findLiveCaveSpawnLocation(chunk, new RNG(1L), IrisSurface.LAVA));
        assertNull(IrisEntitySpawn.findLiveCaveSpawnLocation(chunk, new RNG(1L), IrisSurface.LAND));
    }

    @Test
    public void landEntityBodyRejectsCeilingAndPartialWidthObstructions() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(1.2, 1.95, 0.6));
            Block stone = mock(Block.class);
            when(stone.getType()).thenReturn(Material.STONE);
            when(stone.isSolid()).thenReturn(true);
            when(fixture.world.getBlockAt(-1, 79, 0)).thenReturn(stone);

            assertNull(fixture.entry.spawn(fixture.engine, new Location(fixture.world, 0, 79, 0)));
            assertTrue(fixture.positions.isEmpty());
        }
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(0.6, 2.01, 0.6));
            Block stone = mock(Block.class);
            when(stone.getType()).thenReturn(Material.STONE);
            when(fixture.world.getBlockAt(0, 81, 0)).thenReturn(stone);

            assertNull(fixture.entry.spawn(fixture.engine, new Location(fixture.world, 0, 79, 0)));
            assertTrue(fixture.positions.isEmpty());
        }
    }

    @Test
    public void ambientBatchCannotExceedRemainingCategoryCapacity() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            fixture.remainingCapacity = 1;
            assertEquals(1, fixture.spawn(IrisSpawnGroup.NORMAL));
            assertEquals(1, fixture.positions.size());
        }
    }

    @Test
    public void saturatedAmbientCategorySkipsEverySpawnAttempt() {
        for (int capacity : new int[]{0, -1}) {
            try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
                fixture.remainingCapacity = capacity;
                assertEquals(0, fixture.spawn(IrisSpawnGroup.NORMAL));
                assertTrue(fixture.positions.isEmpty());
                verify(fixture.world, never()).getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class));
            }
        }
    }

    @Test
    public void landEntityBodiesAcceptEveryAirMaterial() {
        for (Material material : List.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR)) {
            try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
                fixture.airMaterial = material;
                assertEquals(material.name(), 3, fixture.spawn(IrisSpawnGroup.NORMAL));
            }
        }
    }

    @Test
    public void foliaWideEntityRejectsUnownedBoundingChunksBeforeReadingThem() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78));
             MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFolia).thenReturn(true);
            scheduler.when(() -> J.isOwnedByCurrentRegion(fixture.world, 0, 0)).thenReturn(true);
            fixture.platform.when(() -> BukkitPlatform.entityBoundingBox(EntityType.TADPOLE))
                    .thenReturn(new Vector3d(4D, 1D, 4D));

            assertNull(fixture.entry.spawn(fixture.engine, new Location(fixture.world, 0, 79, 0)));

            verify(fixture.world, never()).getBlockAt(eq(-2), anyInt(), anyInt());
            assertTrue(fixture.positions.isEmpty());
        }
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
            assertTrue(fixture.positions.stream().allMatch(position -> position.getY() == 79));
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
            assertEquals(0, fixture.entry.spawn(fixture.engine, new IrisPosition(0, 78, 0), new RNG(1L), new IrisEntitySpawn.SpawnContext(new IrisSpawner(), null, false)));
            assertEquals(3, fixture.entry.spawn(fixture.engine, new IrisPosition(0, 77, 0), new RNG(1L), new IrisEntitySpawn.SpawnContext(new IrisSpawner(), null, false)));
        }
    }

    @Test
    public void repeatedExplicitSpawnsDoNotMoveTheCallerLocation() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            Location location = new Location(fixture.world, 0, 79, 0);
            fixture.entry.spawn(fixture.engine, location);
            fixture.entry.spawn(fixture.engine, location);
            assertEquals(new Location(fixture.world, 0, 79, 0), location);
            assertEquals(List.of(new Location(fixture.world, 0.5, 79, 0.5),
                    new Location(fixture.world, 0.5, 79, 0.5)), fixture.positions);
        }
    }

    @Test
    public void failedFirstSiblingDoesNotChangeLaterPositionsOrCustomizationSeeds() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            assertEquals(3, fixture.spawn(IrisSpawnGroup.NORMAL));
            List<Location> expectedPositions = new ArrayList<>(fixture.positions.subList(1, 3));
            List<Long> expectedSeeds = new ArrayList<>(fixture.customizationSeeds.subList(1, 3));
            Location rejected = fixture.positions.getFirst();
            Block obstruction = mock(Block.class);
            when(obstruction.getType()).thenReturn(Material.STONE);
            when(obstruction.isSolid()).thenReturn(true);
            when(fixture.world.getBlockAt(rejected.getBlockX(), rejected.getBlockY(), rejected.getBlockZ()))
                    .thenReturn(obstruction);
            fixture.positions.clear();
            fixture.customizationSeeds.clear();
            assertEquals(2, fixture.spawn(IrisSpawnGroup.NORMAL));
            assertEquals(expectedPositions, fixture.positions);
            assertEquals(expectedSeeds, fixture.customizationSeeds);
        }
    }

    @Test
    public void initialCavePopulationDoesNotDependOnMarkerStorageOrder() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78));
             MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFolia).thenReturn(false);
            EngineMantle mantle = mock(EngineMantle.class);
            when(fixture.engine.getMantle()).thenReturn(mantle);
            IrisPosition first = new IrisPosition(7, 79, 9);
            IrisPosition second = new IrisPosition(3, 79, 5);
            when(mantle.findMarkers(0, 0, MarkerMatter.CAVE_FLOOR))
                    .thenReturn(new KList<>(first, second), new KList<>(second, first));
            fixture.platform.when(() -> BukkitPlatform.toLocation(any(IrisPosition.class), eq(fixture.world)))
                    .thenAnswer(invocation -> {
                        IrisPosition position = invocation.getArgument(0);
                        return new Location(fixture.world, position.getX(), position.getY(), position.getZ());
                    });
            IrisEntitySpawn.SpawnContext context = new IrisEntitySpawn.SpawnContext(
                    new IrisSpawner().setGroup(IrisSpawnGroup.CAVE), null, true);
            assertEquals(3, fixture.entry.spawn(fixture.engine, fixture.chunk, new RNG(1337L), 3, context));
            List<Location> expected = new ArrayList<>(fixture.positions);
            List<Long> expectedSeeds = new ArrayList<>(fixture.customizationSeeds);
            fixture.positions.clear();
            fixture.customizationSeeds.clear();
            assertEquals(3, fixture.entry.spawn(fixture.engine, fixture.chunk, new RNG(1337L), 3, context));
            assertEquals(expected, fixture.positions);
            assertEquals(expectedSeeds, fixture.customizationSeeds);
            verify(mantle, times(2)).findMarkers(0, 0, MarkerMatter.CAVE_FLOOR);
        }
    }

    @Test
    public void initialPopulationIgnoresLiveLightButRecurringPopulationHonorsIt() {
        try (SpawnFixture fixture = new SpawnFixture(new SpawnOptions(IrisSurface.LAND, Material.WATER, 78, 78))) {
            fixture.lightLevel = 15;
            IrisSpawner spawner = new IrisSpawner().setAllowedLightLevels(new IrisRange(0, 0));
            assertEquals(0, fixture.entry.spawn(fixture.engine, fixture.chunk, new RNG(1337L), 3,
                    new IrisEntitySpawn.SpawnContext(spawner, null, false)));
            assertEquals(3, fixture.entry.spawn(fixture.engine, fixture.chunk, new RNG(1337L), 3,
                    new IrisEntitySpawn.SpawnContext(spawner, null, true)));
        }
    }

    @Test
    public void markerExhaustionUsesOnlyTheProvidedSeed() {
        IrisMarker marker = new IrisMarker().setExhaustionChance(0.5D);
        for (long seed = 0; seed < 32; seed++) {
            assertEquals(marker.shouldExhaust(new RNG(seed)), marker.shouldExhaust(new RNG(seed)));
        }
        assertTrue(marker.setExhaustionChance(1D).shouldExhaust(new RNG(1337L)));
        assertFalse(marker.setExhaustionChance(-1D).shouldExhaust(new RNG(1337L)));
    }

    @Test
    public void directEntitySeedDoesNotDependOnWorldObjectIdentity() {
        Engine engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(seeds.getEntity()).thenReturn(1337L);
        IrisEntity entity = spy(new IrisEntity());
        List<Long> captured = new ArrayList<>();
        doAnswer(invocation -> {
            RNG rng = invocation.getArgument(2);
            captured.add(rng.getSeed());
            return null;
        }).when(entity).spawn(eq(engine), any(Location.class), any(RNG.class));
        entity.spawn(engine, new Location(mock(World.class), -31.5, -63.5, 48.5));
        entity.spawn(engine, new Location(mock(World.class), -31.5, -63.5, 48.5));
        assertEquals(List.of(8373759002518926811L, 8373759002518926811L), captured);
    }

    private record SpawnOptions(IrisSurface surface, Material fluid, int floor, int top) {
    }

    private static final class SpawnFixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final Chunk chunk = mock(Chunk.class);
        private final IrisEntitySpawn entry = spy(new IrisEntitySpawn());
        private final List<Location> positions = new ArrayList<>();
        private final List<Long> customizationSeeds = new ArrayList<>();
        private int lightLevel;
        private final MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);
        private Material airMaterial = Material.AIR;
        private int remainingCapacity = Integer.MAX_VALUE;

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
            when(world.getMaxHeight()).thenReturn(256);
            when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.OCEAN_FLOOR))).thenReturn(floor);
            when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.WORLD_SURFACE))).thenReturn(top);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int y = invocation.getArgument(1);
                Block block = mock(Block.class);
                when(block.getType()).thenReturn(y <= floor ? Material.STONE : y <= top ? fluid : airMaterial);
                when(block.isSolid()).thenReturn(y <= floor);
                when(block.getLightLevel()).thenReturn((byte) lightLevel);
                return block;
            });
            when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
                Location location = invocation.getArgument(0);
                return world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            });
            when(definition.spawn(eq(engine), any(Location.class), any(RNG.class))).thenAnswer(invocation -> {
                Location location = invocation.getArgument(1);
                positions.add(location.clone());
                RNG customization = invocation.getArgument(2);
                customizationSeeds.add(customization.getSeed());
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
            return entry.spawn(engine, chunk, new RNG(1337L), remainingCapacity,
                    new IrisEntitySpawn.SpawnContext(new IrisSpawner().setGroup(group), null, false));
        }

        @Override
        public void close() {
            platform.close();
        }
    }
}
