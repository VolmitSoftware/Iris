package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.world.tree.BlockDropRouter;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldBlockDropRouterTest {
    @Test
    public void coldGrassBreakNeverCancelsOrChangesNativeDropHandling() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = new CompletableFuture<>();
            BlockBreakEvent event = new BlockBreakEvent(fixture.block, fixture.player);
            fixture.router.onBlockBreak(event);
            assertFalse(event.isCancelled());
            assertTrue(event.isDropItems());
            verify(fixture.engine, never()).getBiomeEnvironmentAsync(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void firstColdBreakReplacesNativeDropsOnceAfterSavedEnvironmentArrives() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = new CompletableFuture<>();
            BlockDropItemEvent event = fixture.event();
            fixture.router.onBlockDropItems(event);
            assertFalse(event.isCancelled());
            assertTrue(event.getItems().isEmpty());
            fixture.flush();
            verify(fixture.drops, never()).fillDrops(anyBoolean(), any());
            fixture.environment.complete(fixture.biomeEnvironment);
            fixture.flush();
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.custom);
            verify(fixture.world, never()).dropItemNaturally(any(Location.class), eq(fixture.nativeStack));
            verify(fixture.drops).fillDrops(eq(false), any());
            fixture.router.close();
            fixture.flush();
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.custom);
        }
    }

    @Test
    public void additiveRulesPreserveServerCalculatedLootIncludingQuantityAndEnchantments() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.drops.isReplaceVanillaDrops()).thenReturn(false);
            fixture.router.onBlockDropItems(fixture.event());
            fixture.flush();
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.nativeStack);
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.custom);
        }
    }

    @Test
    public void rejectedSchedulerLeavesNativeLootInItsOriginalEvent() {
        try (Fixture fixture = new Fixture()) {
            fixture.scheduling.when(() -> J.runAt(eq(fixture.location), any(Runnable.class), eq(1))).thenReturn(false);
            BlockDropItemEvent event = fixture.event();
            fixture.router.onBlockDropItems(event);
            assertFalse(event.isCancelled());
            assertEquals(1, event.getItems().size());
            verify(fixture.drops, never()).fillDrops(anyBoolean(), any());
            verify(fixture.world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }
    }

    @Test
    public void noNativeItemsStillProducesMatchingCustomDrops() {
        try (Fixture fixture = new Fixture()) {
            BlockDropItemEvent event = fixture.event();
            event.getItems().clear();
            fixture.router.onBlockDropItems(event);
            fixture.flush();
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.custom);
        }
    }

    @Test
    public void noMatchingRulesLeaveNativeEventUntouchedWithoutSavedLookups() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.data.hasBlockDropRules(anyString())).thenReturn(false);
            BlockDropItemEvent event = fixture.event();
            fixture.router.onBlockDropItems(event);
            assertEquals(1, event.getItems().size());
            verify(fixture.engine, never()).getBiomeEnvironmentAsync(anyInt(), anyInt(), anyInt());
            assertTrue(fixture.delayed.isEmpty());
        }
    }

    @Test
    public void cancelledDropEventRemainsCancelledAndDoesNotRollOrEmitLoot() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = new CompletableFuture<>();
            BlockDropItemEvent event = fixture.event();
            fixture.router.onBlockDropItems(event);
            event.setCancelled(true);
            fixture.environment.complete(fixture.biomeEnvironment);
            fixture.flush();
            assertTrue(event.isCancelled());
            verify(fixture.drops, never()).fillDrops(anyBoolean(), any());
            verify(fixture.world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }
    }

    @Test
    public void permanentFailurePreservesNormalLootWithoutBlockingOrDiscardingIt() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = CompletableFuture.failedFuture(new SavedBiomeUnavailableException("Saved biome missing", false));
            BlockDropItemEvent event = fixture.event();
            fixture.router.onBlockDropItems(event);
            fixture.flush();
            assertFalse(event.isCancelled());
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.nativeStack);
            verify(fixture.drops, never()).fillDrops(anyBoolean(), any());
            fixture.logging.verify(() -> IrisLogging.reportError(anyString(), any(Throwable.class)));
        }
    }

    @Test
    public void managerCloseRestoresPendingNativeLootOnlyOnce() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = new CompletableFuture<>();
            fixture.router.onBlockDropItems(fixture.event());
            fixture.router.close();
            fixture.environment.complete(fixture.biomeEnvironment);
            fixture.flush();
            verify(fixture.world).dropItemNaturally(fixture.center, fixture.nativeStack);
            verify(fixture.drops, never()).fillDrops(anyBoolean(), any());
        }
    }

    @Test
    public void historicalContextOwnsMatchingAndAllParentRules() {
        try (Fixture fixture = new Fixture()) {
            IrisData historicData = mock(IrisData.class);
            IrisRegion historicRegion = mock(IrisRegion.class);
            IrisDimension historicDimension = mock(IrisDimension.class);
            IrisBlockDrops regionDrops = mock(IrisBlockDrops.class);
            IrisBlockDrops dimensionDrops = mock(IrisBlockDrops.class);
            when(historicRegion.getBlockDrops()).thenReturn(new KList<>(regionDrops));
            when(historicDimension.getBlockDrops()).thenReturn(new KList<>(dimensionDrops));
            when(fixture.drops.shouldDropFor(fixture.state, historicData)).thenReturn(true);
            when(regionDrops.shouldDropFor(fixture.state, historicData)).thenReturn(true);
            when(dimensionDrops.shouldDropFor(fixture.state, historicData)).thenReturn(true);
            BiomeEnvironment historic = new BiomeEnvironment(2L, fixture.biome, historicRegion, historicDimension, historicData);
            fixture.environment = CompletableFuture.completedFuture(historic);
            fixture.router.onBlockDropItems(fixture.event());
            fixture.flush();
            verify(fixture.engine).openBiomeEnvironmentScope(historic);
            verify(regionDrops).fillDrops(eq(false), any());
            verify(dimensionDrops).fillDrops(eq(false), any());
            verify(fixture.engine, never()).getDimension();
            verify(fixture.drops, never()).shouldDropFor(fixture.state, fixture.data);
        }
    }

    @Test
    public void skipParentsStillPreventsRegionAndDimensionLoot() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.drops.isSkipParents()).thenReturn(true);
            fixture.router.onBlockDropItems(fixture.event());
            fixture.flush();
            verify(fixture.region, never()).getBlockDrops();
            verify(fixture.dimension, never()).getBlockDrops();
        }
    }

    @Test
    public void routedTreeBreakPreservesInventoryDestinationWithColdMetadata() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = new CompletableFuture<>();
            RoutedBreak event = new RoutedBreak(fixture.block, fixture.player);
            fixture.router.onBlockBreak(event);
            assertFalse(event.isCancelled());
            assertFalse(event.isDropItems());
            fixture.environment.complete(fixture.biomeEnvironment);
            fixture.flush();
            assertEquals(List.of(fixture.custom), event.routed);
            verify(fixture.world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }
    }

    @Test
    public void cancelledRoutedProbeNeverEmitsDeferredLoot() {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = new CompletableFuture<>();
            RoutedBreak event = new RoutedBreak(fixture.block, fixture.player);
            fixture.router.onBlockBreak(event);
            event.setCancelled(true);
            fixture.environment.complete(fixture.biomeEnvironment);
            fixture.flush();
            assertTrue(event.routed.isEmpty());
            verify(fixture.drops, never()).fillDrops(anyBoolean(), any());
            assertTrue(fixture.markers.isEmpty());
        }
    }

    private static final class RoutedBreak extends BlockBreakEvent implements BlockDropRouter {
        private final List<Object> routed = new ArrayList<>();

        private RoutedBreak(Block block, Player player) {
            super(block, player);
        }

        @Override
        public Collection<ItemStack> captureNativeDrops() {
            return List.of();
        }

        @Override
        public boolean routeDrop(Object drop) {
            return routed.add(drop);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final IrisWorldManager manager = mock(IrisWorldManager.class);
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisData data = mock(IrisData.class);
        private final World world = mock(World.class);
        private final Block block = mock(Block.class);
        private final BlockState blockState = mock(BlockState.class);
        private final BlockData state = mock(BlockData.class);
        private final Player player = mock(Player.class);
        private final Location location = new Location(world, 16, 72, 32);
        private final Location center = location.clone().add(.5, .5, .5);
        private final Location nativeLocation = location.clone().add(.2, .3, .4);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisRegion region = mock(IrisRegion.class);
        private final IrisDimension dimension = mock(IrisDimension.class);
        private final IrisBlockDrops drops = mock(IrisBlockDrops.class);
        private final ItemStack custom = mock(ItemStack.class);
        private final ItemStack nativeStack = mock(ItemStack.class);
        private final BiomeEnvironment biomeEnvironment = new BiomeEnvironment(1L, biome, region, dimension, data);
        private CompletableFuture<BiomeEnvironment> environment = CompletableFuture.completedFuture(biomeEnvironment);
        private final WorldBlockDropRouter router = new WorldBlockDropRouter(manager);
        private final Queue<Runnable> delayed = new ArrayDeque<>();
        private final Queue<Runnable> markers = new ArrayDeque<>();
        private final MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class);
        private final MockedStatic<J> scheduling = mockStatic(J.class);
        private final MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);

        private Fixture() {
            IrisWorld irisWorld = mock(IrisWorld.class);
            EngineTarget target = mock(EngineTarget.class);
            when(manager.getEngine()).thenReturn(engine);
            when(manager.getTarget()).thenReturn(target);
            when(manager.getData()).thenReturn(data);
            when(data.hasBlockDropRules(anyString())).thenReturn(true);
            when(target.getWorld()).thenReturn(irisWorld);
            when(engine.getWorld()).thenReturn(irisWorld);
            when(irisWorld.minHeight()).thenReturn(-64);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
            when(engine.getBiomeEnvironmentAsync(16, 136, 32)).thenAnswer(invocation -> environment);
            when(engine.openBiomeEnvironmentScope(any(BiomeEnvironment.class))).thenReturn(() -> {});
            binding.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
            when(world.getUID()).thenReturn(new UUID(0L, 1L));
            when(block.getWorld()).thenReturn(world);
            when(block.getLocation()).thenReturn(location);
            when(block.getBlockData()).thenReturn(state);
            when(state.clone()).thenReturn(state);
            when(state.getAsString()).thenReturn("minecraft:grass_block");
            when(blockState.getBlockData()).thenReturn(state);
            when(blockState.getLocation()).thenReturn(location);
            when(nativeStack.clone()).thenReturn(nativeStack);
            when(biome.getBlockDrops()).thenReturn(new KList<>(drops));
            when(region.getBlockDrops()).thenReturn(new KList<>());
            when(dimension.getBlockDrops()).thenReturn(new KList<>());
            when(drops.shouldDropFor(state, data)).thenReturn(true);
            when(drops.isReplaceVanillaDrops()).thenReturn(true);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            when(block.getDrops(inventory.getItemInMainHand(), player)).thenReturn(List.of(nativeStack));
            doAnswer(invocation -> {
                KList<ItemStack> results = invocation.getArgument(1);
                results.add(custom);
                return null;
            }).when(drops).fillDrops(eq(false), any());
            when(manager.managedTask(anyString(), any(Runnable.class))).thenAnswer(invocation -> invocation.getArgument(1));
            scheduling.when(() -> J.runAt(eq(location), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
                delayed.add(invocation.getArgument(1));
                return true;
            });
            scheduling.when(() -> J.isOwnedByCurrentRegion(world, 1, 2)).thenReturn(true);
            scheduling.when(() -> J.runRegionFuture(eq(world), eq(1), eq(2), any(Runnable.class))).thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(3);
                task.run();
                return CompletableFuture.completedFuture(null);
            });
            scheduling.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                markers.add(invocation.getArgument(0));
                return null;
            });
            logging.when(() -> IrisLogging.warnOnce(anyString(), anyString())).thenReturn(true);
        }

        private BlockDropItemEvent event() {
            Item item = mock(Item.class);
            when(item.getItemStack()).thenReturn(nativeStack);
            when(item.getLocation()).thenReturn(nativeLocation);
            return new BlockDropItemEvent(block, blockState, player, new ArrayList<>(List.of(item)));
        }

        private void flush() {
            while (!delayed.isEmpty()) {
                delayed.remove().run();
            }
        }

        @Override
        public void close() {
            scheduling.close();
            logging.close();
            binding.close();
        }
    }
}
