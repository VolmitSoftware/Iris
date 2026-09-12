package art.arcane.iris.platform.generation;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.loot.LootTable;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EngineBukkitOpsLootTest {
    @Test
    public void singleChestIsCanonical() {
        Block block = chestBlock(4, 8, Chest.Type.SINGLE, BlockFace.NORTH);

        assertTrue(EngineBukkitOps.isCanonicalContainer(block));
    }

    @Test
    public void doubleChestHasExactlyOneCanonicalHalf() {
        Block left = chestBlock(0, 0, Chest.Type.LEFT, BlockFace.NORTH);
        Block right = chestBlock(1, 0, Chest.Type.RIGHT, BlockFace.NORTH);

        assertTrue(EngineBukkitOps.isCanonicalContainer(left));
        assertFalse(EngineBukkitOps.isCanonicalContainer(right));
    }

    @Test
    public void nativeLootIsDetectedWithoutOpeningInventory() {
        Block block = chestBlock(0, 0, Chest.Type.SINGLE, BlockFace.NORTH);
        org.bukkit.block.Chest state = mock(org.bukkit.block.Chest.class);
        LootTable table = mock(LootTable.class);
        when(block.getState()).thenReturn(state);
        when(state.getLootTable()).thenReturn(table);

        assertTrue(EngineBukkitOps.hasNativeLootTable(block));
    }

    @Test
    public void foliaLootMutationUsesOwningRegionOnly() {
        World world = mock(World.class);
        AtomicInteger primaryChecks = new AtomicInteger();
        AtomicInteger regionSchedules = new AtomicInteger();
        AtomicInteger syncSchedules = new AtomicInteger();
        AtomicBoolean mutated = new AtomicBoolean();
        Runnable mutation = () -> mutated.set(true);
        EngineBukkitOps.LootMutationTask task = new EngineBukkitOps.LootMutationTask(world, 12, -3, mutation);

        boolean scheduled = EngineBukkitOps.dispatchLootMutation(
                task,
                true,
                () -> {
                    primaryChecks.incrementAndGet();
                    return true;
                },
                regionTask -> {
                    regionSchedules.incrementAndGet();
                    assertSame(world, regionTask.world());
                    assertEquals(12, regionTask.chunkX());
                    assertEquals(-3, regionTask.chunkZ());
                    assertSame(mutation, regionTask.mutation());
                    regionTask.mutation().run();
                    return true;
                },
                runnable -> syncSchedules.incrementAndGet()
        );

        assertTrue(scheduled);
        assertTrue(mutated.get());
        assertEquals(1, regionSchedules.get());
        assertEquals(0, primaryChecks.get());
        assertEquals(0, syncSchedules.get());
    }

    @Test
    public void failedFoliaRegionScheduleDoesNotMutateInventoryElsewhere() {
        World world = mock(World.class);
        AtomicInteger primaryChecks = new AtomicInteger();
        AtomicInteger syncSchedules = new AtomicInteger();
        AtomicBoolean mutated = new AtomicBoolean();
        EngineBukkitOps.LootMutationTask task = new EngineBukkitOps.LootMutationTask(world, 1, 2, () -> mutated.set(true));

        boolean scheduled = EngineBukkitOps.dispatchLootMutation(
                task,
                true,
                () -> {
                    primaryChecks.incrementAndGet();
                    return true;
                },
                regionTask -> false,
                runnable -> syncSchedules.incrementAndGet()
        );

        assertFalse(scheduled);
        assertFalse(mutated.get());
        assertEquals(0, primaryChecks.get());
        assertEquals(0, syncSchedules.get());
    }

    @Test
    public void nonFoliaPrimaryThreadMutatesInline() {
        World world = mock(World.class);
        AtomicInteger regionSchedules = new AtomicInteger();
        AtomicInteger syncSchedules = new AtomicInteger();
        AtomicBoolean mutated = new AtomicBoolean();
        EngineBukkitOps.LootMutationTask task = new EngineBukkitOps.LootMutationTask(world, 4, 5, () -> mutated.set(true));

        boolean scheduled = EngineBukkitOps.dispatchLootMutation(
                task,
                false,
                () -> true,
                regionTask -> {
                    regionSchedules.incrementAndGet();
                    return true;
                },
                runnable -> syncSchedules.incrementAndGet()
        );

        assertTrue(scheduled);
        assertTrue(mutated.get());
        assertEquals(0, regionSchedules.get());
        assertEquals(0, syncSchedules.get());
    }

    @Test
    public void nonFoliaAsyncCompletionUsesSyncScheduler() {
        World world = mock(World.class);
        AtomicInteger regionSchedules = new AtomicInteger();
        AtomicInteger syncSchedules = new AtomicInteger();
        AtomicBoolean mutated = new AtomicBoolean();
        EngineBukkitOps.LootMutationTask task = new EngineBukkitOps.LootMutationTask(world, 6, 7, () -> mutated.set(true));

        boolean scheduled = EngineBukkitOps.dispatchLootMutation(
                task,
                false,
                () -> false,
                regionTask -> {
                    regionSchedules.incrementAndGet();
                    return true;
                },
                runnable -> {
                    syncSchedules.incrementAndGet();
                    runnable.run();
                }
        );

        assertTrue(scheduled);
        assertTrue(mutated.get());
        assertEquals(0, regionSchedules.get());
        assertEquals(1, syncSchedules.get());
    }

    private Block chestBlock(int x, int z, Chest.Type type, BlockFace facing) {
        Block block = mock(Block.class);
        Chest chest = mock(Chest.class);
        when(block.getX()).thenReturn(x);
        when(block.getZ()).thenReturn(z);
        when(block.getBlockData()).thenReturn(chest);
        when(chest.getType()).thenReturn(type);
        when(chest.getFacing()).thenReturn(facing);
        return block;
    }
}
