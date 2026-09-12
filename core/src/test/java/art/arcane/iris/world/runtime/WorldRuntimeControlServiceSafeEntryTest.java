package art.arcane.iris.world.runtime;

import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class WorldRuntimeControlServiceSafeEntryTest {
    private static final BoundingBox FULL_BLOCK = new BoundingBox(0D, 0D, 0D, 1D, 1D, 1D);

    @Test
    public void resolvesStudioEntryAnchorFromGeneratorInsteadOfMutableWorldSpawn() {
        World world = mock(World.class);
        BukkitChunkGenerator provider = mock(BukkitChunkGenerator.class);
        Location initialSpawn = new Location(world, 0.5D, 96D, 0.5D);
        Location mutableWorldSpawn = new Location(world, 128.5D, 80D, -64.5D);

        doReturn(true).when(provider).isStudio();
        doReturn(initialSpawn).when(provider).getInitialSpawnLocation(world);
        doReturn(mutableWorldSpawn).when(world).getSpawnLocation();

        Location resolved = WorldRuntimeControlService.resolveEntryAnchor(world, provider);

        assertEquals(initialSpawn, resolved);
    }

    @Test
    public void fallsBackToWorldSpawnWhenGeneratorIsNotStudio() {
        World world = mock(World.class);
        PlatformChunkGenerator provider = mock(PlatformChunkGenerator.class);
        Location mutableWorldSpawn = new Location(world, 128.5D, 80D, -64.5D);

        doReturn(false).when(provider).isStudio();
        doReturn(mutableWorldSpawn).when(world).getSpawnLocation();

        Location resolved = WorldRuntimeControlService.resolveEntryAnchor(world, provider);

        assertEquals(mutableWorldSpawn, resolved);
    }

    @Test
    public void resolvesEntryAboveDryCollisionSupportingFloor() {
        World world = loadedWorld(0, 0);
        Block stone = block(Material.STONE, false, false, FULL_BLOCK);
        Block air = block(Material.AIR, false, true);
        doReturn(62).when(world).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
        doAnswer(invocation -> {
            int y = invocation.getArgument(1);
            return y == 62 ? stone : air;
        }).when(world).getBlockAt(anyInt(), anyInt(), anyInt());

        Location source = new Location(world, 0.5D, 62D, 0.5D);
        Location result = WorldRuntimeControlService.findTopSafeLocation(world, source);

        assertNotNull(result);
        assertEquals(63, result.getBlockY());
    }

    @Test
    public void resolvesSafeCavityBelowDimensionRoof() {
        World world = loadedWorld(0, 0);
        Block netherrack = block(Material.NETHERRACK, false, false, FULL_BLOCK);
        Block air = block(Material.AIR, false, true);
        doReturn(300).when(world).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
        doAnswer(invocation -> {
            int y = invocation.getArgument(1);
            if (y == 201 || y == 202) {
                return air;
            }
            return netherrack;
        }).when(world).getBlockAt(anyInt(), anyInt(), anyInt());

        Location source = new Location(world, 0.5D, 201D, 0.5D);
        Location result = WorldRuntimeControlService.findTopSafeLocation(world, source);

        assertNotNull(result);
        assertEquals(201, result.getBlockY());
    }

    @Test
    public void rejectsFluidHazardousAndCollisionBlockedCandidates() {
        World world = loadedWorld(0, 0);
        Block water = block(Material.WATER, true, true);
        Block air = block(Material.AIR, false, true);
        Block stone = block(Material.STONE, false, false, FULL_BLOCK);
        Block leaves = block(Material.OAK_LEAVES, false, false, FULL_BLOCK);
        Block powderSnow = block(Material.POWDER_SNOW, false, true);
        Block magma = block(Material.MAGMA_BLOCK, false, false, FULL_BLOCK);
        Block cactus = block(Material.CACTUS, false, false, FULL_BLOCK);
        Block cobweb = block(Material.COBWEB, false, true, FULL_BLOCK);
        Block fence = block(Material.OAK_FENCE, false, false,
                new BoundingBox(0.375D, 0D, 0.375D, 0.625D, 1.5D, 0.625D));
        Block waterloggedSlab = waterloggedBlock(
                Material.OAK_SLAB,
                new BoundingBox(0D, 0D, 0D, 1D, 0.5D, 1D)
        );
        doReturn(62).when(world).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
        doAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            if (y == 62) {
                if (x == 7 && z == 7) {
                    return leaves;
                }
                if (x == 7 && z == 8) {
                    return powderSnow;
                }
                if (x == 7 && z == 9) {
                    return magma;
                }
                if (x == 8 && z == 9) {
                    return stone;
                }
                if (x == 9 && z == 7) {
                    return waterloggedSlab;
                }
                if (x == 9 && z == 8) {
                    return cactus;
                }
                if (x == 9 && z == 9) {
                    return stone;
                }
                if (x == 10 && z == 10) {
                    return stone;
                }
            }
            if (x == 8 && z == 9 && y == 63) {
                return cobweb;
            }
            if (x == 9 && z == 9 && y == 63) {
                return air;
            }
            if (x == 9 && z == 9 && y == 64) {
                return fence;
            }
            if (x == 10 && z == 10 && (y == 63 || y == 64)) {
                return air;
            }
            return water;
        }).when(world).getBlockAt(anyInt(), anyInt(), anyInt());

        Location source = new Location(world, 8.5D, 63D, 8.5D);
        Location result = WorldRuntimeControlService.findTopSafeLocation(world, source);

        assertNotNull(result);
        assertEquals(10, result.getBlockX());
        assertEquals(63, result.getBlockY());
        assertEquals(10, result.getBlockZ());
    }

    @Test
    public void genericSafeEntryStillRejectsWaterOnlyChunks() {
        World world = loadedWorld(0, 0);
        Block water = block(Material.WATER, true, true);
        Block air = block(Material.AIR, false, true);
        AtomicInteger lowestReadY = new AtomicInteger(Integer.MAX_VALUE);
        doReturn(62).when(world).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
        doAnswer(invocation -> {
            int y = invocation.getArgument(1);
            lowestReadY.accumulateAndGet(y, Math::min);
            return y <= 62 ? water : air;
        }).when(world).getBlockAt(anyInt(), anyInt(), anyInt());

        Location source = new Location(world, 0.5D, 63D, 0.5D);
        Location result = WorldRuntimeControlService.findTopSafeLocation(world, source);

        assertNull(result);
        assertEquals(-64, lowestReadY.get());
    }

    @Test
    public void returnsNullWithoutReadingAnUnloadedChunk() {
        World world = loadedWorld(0, 0);
        doReturn(false).when(world).isChunkLoaded(0, 0);

        Location source = new Location(world, 0.5D, 63D, 0.5D);
        Location result = WorldRuntimeControlService.findTopSafeLocation(world, source);

        assertNull(result);
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    private static World loadedWorld(int chunkX, int chunkZ) {
        World world = mock(World.class);
        doReturn(-64).when(world).getMinHeight();
        doReturn(320).when(world).getMaxHeight();
        doReturn(true).when(world).isChunkLoaded(chunkX, chunkZ);
        return world;
    }

    private static Block block(Material material, boolean liquid, boolean passable, BoundingBox... boundingBoxes) {
        Block block = mock(Block.class);
        VoxelShape collisionShape = mock(VoxelShape.class);
        doReturn(material).when(block).getType();
        doReturn(liquid).when(block).isLiquid();
        doReturn(passable).when(block).isPassable();
        doReturn(collisionShape).when(block).getCollisionShape();
        doReturn(List.of(boundingBoxes)).when(collisionShape).getBoundingBoxes();
        return block;
    }

    private static Block waterloggedBlock(Material material, BoundingBox... boundingBoxes) {
        Block block = block(material, false, false, boundingBoxes);
        Waterlogged blockData = mock(Waterlogged.class);
        doReturn(true).when(blockData).isWaterlogged();
        doReturn(blockData).when(block).getBlockData();
        return block;
    }

}
