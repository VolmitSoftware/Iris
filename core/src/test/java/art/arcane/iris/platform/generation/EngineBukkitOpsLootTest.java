package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.world.event.IrisLootEvent;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import io.papermc.paper.registry.RegistryAccess;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    public void failedInsertionRestoresExistingLootBeforeRetry() {
        Inventory inventory = mock(Inventory.class);
        AtomicReference<ItemStack[]> contents = new AtomicReference<>(new ItemStack[]{item(2), null});
        AtomicBoolean reject = new AtomicBoolean(true);
        when(inventory.getSize()).thenReturn(2);
        when(inventory.getContents()).thenAnswer(invocation -> contents.get());
        doAnswer(invocation -> {
            contents.set(invocation.getArgument(0));
            return null;
        }).when(inventory).setContents(any(ItemStack[].class));
        doAnswer(invocation -> {
            ItemStack incoming = invocation.getArgument(0);
            contents.get()[0].setAmount(contents.get()[0].getAmount() + incoming.getAmount());
            if (reject.getAndSet(false)) {
                throw new IllegalStateException("Insertion failed");
            }
            return new HashMap<Integer, ItemStack>();
        }).when(inventory).addItem(any(ItemStack.class));
        ItemStack reward = item(7);

        assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.populateInventory(null, inventory, List.of(reward), new RNG(17)));

        assertEquals(2, contents.get()[0].getAmount());
        EngineBukkitOps.populateInventory(null, inventory, List.of(reward), new RNG(17));
        int total = 0;
        for (ItemStack item : contents.get()) {
            total += item == null ? 0 : item.getAmount();
        }
        assertEquals(9, total);
        assertEquals(7, reward.getAmount());
    }

    @Test
    public void failedNativeInventoryAccessRestoresBothChestSnapshots() {
        Inventory inventory = mock(Inventory.class);
        DoubleChest holder = mock(DoubleChest.class);
        org.bukkit.block.Chest left = mock(org.bukkit.block.Chest.class);
        org.bukkit.block.Chest right = mock(org.bukkit.block.Chest.class);
        Block leftBlock = mock(Block.class);
        Block rightBlock = mock(Block.class);
        BlockState leftSnapshot = mock(BlockState.class);
        BlockState rightSnapshot = mock(BlockState.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(holder.getLeftSide()).thenReturn(left);
        when(holder.getRightSide()).thenReturn(right);
        when(left.getBlock()).thenReturn(leftBlock);
        when(right.getBlock()).thenReturn(rightBlock);
        when(leftBlock.getState()).thenReturn(leftSnapshot);
        when(rightBlock.getState()).thenReturn(rightSnapshot);
        when(leftSnapshot.update(true, false)).thenReturn(true);
        when(rightSnapshot.update(true, false)).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("Native loot failed");
        doThrow(failure).when(inventory).addItem(any(ItemStack.class));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.populateInventory(null, inventory,
                        List.of(item(1)), new RNG(17))));

        verify(leftSnapshot).update(true, false);
        verify(rightSnapshot).update(true, false);
    }

    @Test
    public void failedBarrelFillRestoresItsBlockSnapshot() {
        Inventory inventory = mock(Inventory.class);
        Barrel holder = mock(Barrel.class);
        Block block = mock(Block.class);
        Barrel snapshot = mock(Barrel.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(holder.getBlock()).thenReturn(block);
        when(block.getState()).thenReturn(snapshot);
        when(snapshot.update(true, false)).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("Native barrel loot failed");
        when(inventory.getContents()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.populateInventory(null, inventory, List.of(), new RNG(17))));

        verify(snapshot).update(true, false);
    }

    @Test
    public void failedRollbackPreservesTheCauseAndRestoresTheOtherHalf() {
        Inventory inventory = mock(Inventory.class);
        DoubleChest holder = mock(DoubleChest.class);
        org.bukkit.block.Chest left = mock(org.bukkit.block.Chest.class, RETURNS_DEEP_STUBS);
        org.bukkit.block.Chest right = mock(org.bukkit.block.Chest.class, RETURNS_DEEP_STUBS);
        BlockState leftSnapshot = mock(BlockState.class);
        BlockState rightSnapshot = mock(BlockState.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(holder.getLeftSide()).thenReturn(left);
        when(holder.getRightSide()).thenReturn(right);
        when(left.getBlock().getState()).thenReturn(leftSnapshot);
        when(right.getBlock().getState()).thenReturn(rightSnapshot);
        when(rightSnapshot.update(true, false)).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("Native loot failed");
        IllegalStateException rollbackFailure = new IllegalStateException("Snapshot restore failed");
        when(leftSnapshot.update(true, false)).thenThrow(rollbackFailure);
        doThrow(failure).when(inventory).addItem(any(ItemStack.class));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.populateInventory(null, inventory, List.of(item(1)), new RNG(17))));

        assertEquals(1, failure.getSuppressed().length);
        assertSame(rollbackFailure, failure.getSuppressed()[0]);
        verify(rightSnapshot).update(true, false);
    }

    @Test
    public void cancelledLootLeavesTheInventoryUntouched() {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        Inventory inventory = mock(Inventory.class);
        World world = mock(World.class);
        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<IrisLootEvent> events = mockStatic(IrisLootEvent.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(world, 2, -2)).thenReturn(true);
            events.when(() -> IrisLootEvent.callLootEvent(any(), eq(inventory), eq(world), eq(33), eq(64), eq(-17)))
                    .thenReturn(true);

            EngineBukkitOps.addItems(engine, false, inventory, new KList<>(), InventorySlotType.STORAGE, world, 33, 64, -17);

            verifyNoInteractions(inventory);
        }
    }

    @Test
    public void offRegionLootDoesNotRollOrTouchInventory() {
        Engine engine = mock(Engine.class);
        Inventory inventory = mock(Inventory.class);
        World world = mock(World.class);
        IrisLootTable table = mock(IrisLootTable.class);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            assertThrows(IllegalStateException.class, () -> EngineBukkitOps.addItems(engine, false, inventory,
                    new KList<>(table), InventorySlotType.STORAGE, world, 33, 64, -17));

            scheduling.verify(() -> J.isOwnedByCurrentRegion(world, 2, -2));
            verifyNoInteractions(table, inventory, engine);
        }
    }

    @Test
    public void owningRegionLootCompletesInlineWithoutGlobalDispatch() {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        Inventory inventory = mock(Inventory.class);
        World world = mock(World.class);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<IrisLootEvent> events = mockStatic(IrisLootEvent.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(world, 2, -2)).thenReturn(true);

            EngineBukkitOps.addItems(engine, false, inventory, new KList<>(), InventorySlotType.STORAGE, world, 33, 64, -17);

            verify(inventory).setContents(any(ItemStack[].class));
            scheduling.verify(() -> J.isOwnedByCurrentRegion(world, 2, -2));
            scheduling.verifyNoMoreInteractions();
        }
    }

    @Test
    public void foliaLootEventRunsOnTheOwningRegionWithoutGlobalDispatch() {
        World world = mock(World.class);
        Inventory inventory = mock(Inventory.class);
        PluginManager manager = mock(PluginManager.class);
        Thread owner = Thread.currentThread();
        AtomicInteger eventCalls = new AtomicInteger();
        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(world, 2, -2)).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            doAnswer(invocation -> {
                assertSame(owner, Thread.currentThread());
                eventCalls.incrementAndGet();
                return null;
            }).when(manager).callEvent(any(LootGenerateEvent.class));

            assertFalse(IrisLootEvent.callLootEvent(new KList<>(), inventory, world, 33, 64, -17));

            assertEquals(1, eventCalls.get());
            scheduling.verify(() -> J.isOwnedByCurrentRegion(world, 2, -2));
            scheduling.verifyNoMoreInteractions();
        }
    }

    @Test
    public void lootEventRetainsListenerChangesAndCancellation() {
        World world = mock(World.class);
        Inventory inventory = mock(Inventory.class);
        PluginManager manager = mock(PluginManager.class);
        ItemStack original = item(2);
        ItemStack replacement = item(3);
        KList<ItemStack> items = new KList<>(original);
        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(world, 2, -2)).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            doAnswer(invocation -> {
                LootGenerateEvent event = invocation.getArgument(0);
                event.setLoot(List.of(replacement));
                event.setCancelled(true);
                return null;
            }).when(manager).callEvent(any(LootGenerateEvent.class));

            assertTrue(IrisLootEvent.callLootEvent(items, inventory, world, 33, 64, -17));

            assertEquals(List.of(replacement), items);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void failedLootRollKeepsPendingMarkerUntilSuccessfulRetry() {
        BukkitTestServer.install();
        AtomicBoolean pending = new AtomicBoolean(true);
        AtomicInteger attempts = new AtomicInteger();
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
        World world = mock(World.class);
        when(world.getMaxHeight()).thenReturn(256);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        Block block = mock(Block.class);
        BlockData state = mock(BlockData.class);
        org.bukkit.block.Chest chest = mock(org.bukkit.block.Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(chunk.getBlock(1, 64, 1)).thenReturn(block);
        when(block.getBlockData()).thenReturn(state);
        when(block.getX()).thenReturn(1);
        when(block.getZ()).thenReturn(1);
        when(block.getState()).thenReturn(chest);
        when(chest.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        MantleChunk<Matter> mantle = spy(new MantleChunk<>(16, 0, 0, mock(MantleDataAdapter.class), MantleHooks.NONE));
        doAnswer(invocation -> {
            if (pending.get()) {
                Consumer4<Integer, Integer, Integer, MatterUpdate> visitor = invocation.getArgument(1);
                visitor.accept(1, 64, 1, UpdateMatter.ON);
            }
            return null;
        }).when(mantle).iterate(eq(MatterUpdate.class), any());
        doAnswer(invocation -> {
            pending.set(false);
            return null;
        }).when(mantle).deleteSlices(MatterUpdate.class);
        IrisLootTable table = mock(IrisLootTable.class);
        IllegalStateException failure = new IllegalStateException("Loot provider unavailable");
        when(table.getLoot(false, 0L, InventorySlotType.STORAGE, world, 1, 64, 1)).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() < 2) {
                throw failure;
            }
            return new KList<ItemStack>();
        });
        KList<IrisLootTable> tables = new KList<>(table);
        PluginManager manager = mock(PluginManager.class);
        try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class)) {
            registries.when(RegistryAccess::registryAccess).thenReturn(BukkitTestServer.registryAccess());
            try (MockedStatic<EngineBukkitOps> operations = mockStatic(EngineBukkitOps.class);
                 MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class);
                 MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                 MockedStatic<IrisLootEvent> events = mockStatic(IrisLootEvent.class);
                 MockedStatic<J> scheduling = mockStatic(J.class);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
                logging.when(() -> IrisLogging.errorOnce(anyString(), anyString())).thenReturn(true, false);
                scheduling.when(() -> J.isOwnedByCurrentRegion(world, 0, 0)).thenReturn(true);
                resolution.when(() -> BukkitBlockResolution.isStorage(state)).thenReturn(true);
                resolution.when(() -> BukkitBlockResolution.isStorageChest(state)).thenReturn(true);
                operations.when(() -> EngineBukkitOps.isCanonicalContainer(block)).thenReturn(true);
                operations.when(() -> EngineBukkitOps.getLootTables(eq(engine), any(RNG.class), eq(block), eq(mantle), eq(false))).thenReturn(tables);
                operations.when(() -> EngineBukkitOps.addItems(engine, false, inventory, tables, InventorySlotType.STORAGE, world, 1, 64, 1)).thenCallRealMethod();
                operations.when(() -> EngineBukkitOps.populateInventory(eq(engine), eq(inventory), any(), any())).thenCallRealMethod();
                operations.when(() -> EngineBukkitOps.update(engine, 1, 64, 1, chunk, mantle)).thenCallRealMethod();
                operations.when(() -> EngineBukkitOps.materializeUpdates(engine, chunk, mantle)).thenCallRealMethod();
                operations.when(() -> EngineBukkitOps.materializeUpdates(eq(engine), eq(chunk), eq(mantle), any(EngineBukkitOps.UpdateDispatcher.class))).thenCallRealMethod();
                operations.when(() -> EngineBukkitOps.runMaterializationPasses(eq(mantle), any(Runnable.class), any(Runnable.class), any(Runnable.class))).thenCallRealMethod();
                Runnable pass = () -> EngineBukkitOps.runMaterializationPasses(mantle, () -> {}, () -> {},
                        () -> EngineBukkitOps.materializeUpdates(engine, chunk, mantle));

                assertSame(failure, assertThrows(IllegalStateException.class, pass::run));
                assertTrue(pending.get());
                assertFalse(mantle.isFlagged(MantleFlag.UPDATE));
                assertFalse(mantle.isFlagged(MantleFlag.ETCHED));
                assertSame(failure, assertThrows(IllegalStateException.class, pass::run));
                assertTrue(pending.get());
                logging.verify(() -> IrisLogging.reportError(failure));

                pass.run();
                pass.run();

                assertFalse(pending.get());
                assertTrue(mantle.isFlagged(MantleFlag.UPDATE));
                assertTrue(mantle.isFlagged(MantleFlag.ETCHED));
                assertEquals(3, attempts.get());
            }
        }
    }

    private ItemStack item(int amount) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenAnswer(invocation -> count.get());
        when(item.clone()).thenAnswer(invocation -> item(count.get()));
        doAnswer(invocation -> {
            count.set(invocation.getArgument(0));
            return null;
        }).when(item).setAmount(anyInt());
        return item;
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
