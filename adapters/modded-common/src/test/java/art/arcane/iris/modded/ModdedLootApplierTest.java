package art.arcane.iris.modded;

import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.world.loot.IrisLootMode;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeContainerLoot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.math.RNG;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ModdedLootApplierTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!Items.DIAMOND.builtInRegistryHolder().areComponentsBound()) {
            Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    @Test
    public void addAppendsSourcesInOrder() {
        List<String> sources = new ArrayList<>(List.of("placement-native"));

        LootResolver.injectSources(sources, List.of("dimension-iris", "region-iris"), IrisLootMode.ADD, false);

        assertEquals(List.of("placement-native", "dimension-iris", "region-iris"), sources);
    }

    @Test
    public void clearRemovesEverythingAndContributesNothing() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.injectSources(sources, List.of("biome-iris"), IrisLootMode.CLEAR, false);

        assertEquals(List.of(), sources);
    }

    @Test
    public void replaceRemovesNativeAndIrisSourcesBeforeAdding() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.injectSources(sources, List.of("biome-iris"), IrisLootMode.REPLACE, false);

        assertEquals(List.of("biome-iris"), sources);
    }

    @Test
    public void entityLootBindingTargetsOnlyTheBaseLootInjection() {
        assertTrue(ModdedDeathLoot.hasBindingTag(Set.of("unrelated", "iris_loot|17|1|2|3|test")));
        assertFalse(ModdedDeathLoot.hasBindingTag(Set.of("unrelated", "iris_other")));
    }

    @Test
    public void fallbackDoesNotOverrideExistingNativeOrIrisSources() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.injectSources(sources, List.of("fallback-iris"), IrisLootMode.FALLBACK, false);

        assertEquals(List.of("placement-native", "dimension-iris"), sources);
    }

    @Test
    public void fallbackAddsSourcesWhenPlacementIsEmpty() {
        List<String> sources = new ArrayList<>();

        LootResolver.injectSources(sources, List.of("fallback-iris"), IrisLootMode.FALLBACK, true);

        assertEquals(List.of("fallback-iris"), sources);
    }

    @Test
    public void zeroMultiplierRemovesNativeAndIrisSources() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.scaleSources(sources, 0D, new RNG(17L));

        assertTrue(sources.isEmpty());
    }

    @Test
    public void unitMultiplierPreservesNativeAndIrisSources() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.scaleSources(sources, 1D, new RNG(17L));

        assertEquals(List.of("placement-native", "dimension-iris"), sources);
    }

    @Test
    public void doubleMultiplierScalesTheUnifiedSourceList() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.scaleSources(sources, 2D, new RNG(17L));

        assertEquals(4, sources.size());
        assertEquals("placement-native", sources.get(0));
        assertEquals("dimension-iris", sources.get(1));
        Set<String> expectedSources = Set.of("placement-native", "dimension-iris");
        for (String source : sources) {
            assertTrue(expectedSources.contains(source));
        }
    }

    @Test
    public void nativeKeyIsReturnedWhenTheLiveRegistryContainsIt() {
        ResourceKey<LootTable> key = NativeContainerLoot.resolveNativeKey("minecraft:chests/simple_dungeon", candidate -> true);

        assertNotNull(key);
        assertEquals(Identifier.parse("minecraft:chests/simple_dungeon"), key.identifier());
    }

    @Test
    public void nativeKeyIsRejectedWhenTheLiveRegistryDoesNotContainIt() {
        ResourceKey<LootTable> key = NativeContainerLoot.resolveNativeKey("minecraft:chests/missing", candidate -> false);

        assertNull(key);
    }

    @Test
    public void malformedNativeKeyIsRejectedBeforeRegistryLookup() {
        AtomicBoolean registryConsulted = new AtomicBoolean(false);

        ResourceKey<LootTable> key = NativeContainerLoot.resolveNativeKey("not a valid identifier", candidate -> {
            registryConsulted.set(true);
            return true;
        });

        assertNull(key);
        assertFalse(registryConsulted.get());
    }

    @Test
    public void nativeOnlyFillMarksTheContainerChanged() {
        TrackingContainer container = new TrackingContainer();

        NativeContainerLoot.fillContainer(container, List.<ItemStack>of(), new RNG(17L), message -> {});

        assertTrue(container.changed);
    }

    @Test
    public void doubleChestHasExactlyOneCanonicalHalf() {
        BlockPos leftPos = new BlockPos(0, 64, 0);
        BlockPos rightPos = new BlockPos(1, 64, 0);

        assertTrue(NativeContainerLoot.isCanonicalPair(leftPos, rightPos));
        assertFalse(NativeContainerLoot.isCanonicalPair(rightPos, leftPos));
    }

    @Test
    public void nativeLootTableIsDetectedBeforeContainerAccess() {
        ResourceKey<LootTable> key = ResourceKey.create(
                Registries.LOOT_TABLE,
                Identifier.parse("minecraft:chests/simple_dungeon")
        );
        RandomizableContainer empty = containerWithLootTable(null);
        RandomizableContainer nativeLoot = containerWithLootTable(key);

        assertFalse(NativeContainerLoot.hasNativeLootTable(empty));
        assertTrue(NativeContainerLoot.hasNativeLootTable(nativeLoot));
    }

    @Test
    public void failedFillRestoresContentsAndDoesNotDuplicateLootOnRetry() {
        RejectingContainer container = new RejectingContainer();
        container.setItem(0, diamondStack(2));
        assertEquals(2, container.getItem(0).getCount());
        container.reject = true;
        ItemStack reward = diamondStack(7);

        assertThrows(IllegalStateException.class,
                () -> NativeContainerLoot.fillContainer(container, List.of(reward), new RNG(17), message -> {}));

        assertEquals(2, container.getItem(0).getCount());
        assertTrue(container.getItem(1).isEmpty());
        assertEquals(7, reward.getCount());
        NativeContainerLoot.fillContainer(container, List.of(reward), new RNG(17), message -> {});
        assertEquals(9, container.getItem(0).getCount() + container.getItem(1).getCount());
        assertEquals(7, reward.getCount());
    }

    @Test
    public void failedNativeUnpackRestoresTheLootTableSeedAndPriorContents() {
        DeferredLootChest container = new DeferredLootChest(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        container.setItem(0, diamondStack(2));
        container.reject = true;
        ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:chests/simple_dungeon"));
        container.setLootTable(table, 41L);

        assertThrows(IllegalStateException.class, () -> NativeContainerLoot.fillContainer(container,
                List.of(new ItemStack(Items.DIAMOND)), new RNG(17), message -> {}));

        assertSame(table, container.getLootTable());
        assertEquals(41L, container.getLootTableSeed());
        container.setLootTable(null);
        assertEquals(2, container.getItem(0).getCount());
        for (int slot = 1; slot < container.getContainerSize(); slot++) {
            assertTrue(container.getItem(slot).isEmpty());
        }
    }

    @Test
    public void failedDoubleChestFillRestoresBothDeferredHalvesBeforeRetry() {
        BlockPos leftPos = new BlockPos(0, 64, 0);
        BlockPos rightPos = new BlockPos(1, 64, 0);
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState rightState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT);
        DeferredLootChest left = new DeferredLootChest(leftPos, leftState);
        DeferredLootChest right = new DeferredLootChest(rightPos, rightState);
        left.setItem(0, diamondStack(2));
        right.setItem(0, diamondStack(3));
        right.reject = true;
        ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:chests/simple_dungeon"));
        left.setLootTable(table, 41L);
        right.setLootTable(table, 42L);
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld world = mock(NativeWorld.class);
        NativeBlockState state = mock(NativeBlockState.class);
        when(world.nativeHandle()).thenReturn(level);
        when(state.nativeHandle()).thenReturn(leftState);
        when(level.getBlockEntity(leftPos)).thenReturn(left);
        when(level.getBlockEntity(rightPos)).thenReturn(right);
        CompoundContainer inventory = new CompoundContainer(left, right);
        ItemStack reward = diamondStack(7);
        try (MockedStatic<ChestBlock> chests = mockStatic(ChestBlock.class)) {
            chests.when(() -> ChestBlock.getContainer((ChestBlock) Blocks.CHEST, leftState, level, leftPos, true))
                    .thenReturn(inventory);
            chests.when(() -> ChestBlock.getConnectedBlockPos(leftPos, leftState)).thenReturn(rightPos);
            NativeContainerLoot access = new NativeContainerLoot(new NativeContainerLoot.Target(
                    world, new NativeBlockPoint(0, 64, 0), state));
            NativeContainerLoot.Session session = access.open();
            assertNotNull(session);
            session.add(List.of(NativeItemStack.of(reward)));

            assertThrows(IllegalStateException.class, () -> session.fill(new RNG(17), message -> {}));

            assertSame(table, left.getLootTable());
            assertSame(table, right.getLootTable());
            assertEquals(41L, left.getLootTableSeed());
            assertEquals(42L, right.getLootTableSeed());
            left.setLootTable(null);
            right.setLootTable(null);
            assertEquals(2, countItems(left));
            assertEquals(3, countItems(right));
            assertEquals(7, reward.getCount());
            left.setLootTable(table, 41L);
            right.setLootTable(table, 42L);
            right.reject = false;

            session.fill(new RNG(17), message -> {});

            assertNull(left.getLootTable());
            assertNull(right.getLootTable());
            assertEquals(15, countItems(left) + countItems(right));
            assertEquals(7, reward.getCount());
        }
    }

    private static int countItems(ChestBlockEntity container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += container.getItem(slot).getCount();
        }
        return total;
    }

    private static ItemStack diamondStack(int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static final class RejectingContainer extends SimpleContainer {
        private boolean reject;

        private RejectingContainer() {
            super(2);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (reject) {
                reject = false;
                throw new IllegalStateException("Inventory write failed");
            }
        }
    }

    private static final class DeferredLootChest extends ChestBlockEntity {
        private boolean reject;

        private DeferredLootChest(BlockPos position, BlockState state) {
            super(position, state);
        }

        @Override
        public void unpackLootTable(Player player) {
            if (getLootTable() != null) {
                setLootTable(null);
                setItem(0, diamondStack(4));
                if (reject) {
                    throw new IllegalStateException("Native loot failed");
                }
            }
        }
    }

    private RandomizableContainer containerWithLootTable(ResourceKey<LootTable> key) {
        return (RandomizableContainer) Proxy.newProxyInstance(
                RandomizableContainer.class.getClassLoader(),
                new Class<?>[]{RandomizableContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLootTable")) {
                        return key;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }

    private static final class TrackingContainer extends SimpleContainer {
        private boolean changed;

        private TrackingContainer() {
            super(0);
        }

        @Override
        public void setChanged() {
            changed = true;
            super.setChanged();
        }
    }
}
