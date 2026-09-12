package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    public void coldBiomeCancelsBeforeChangingDropsOrSchedulingWork() {
        try (Fixture fixture = new Fixture()) {
            fixture.queries.when(() -> EngineBukkitOps.getBiome(fixture.engine, fixture.location))
                    .thenThrow(new SavedBiomeUnavailableException("Biome loading", true));
            BlockBreakEvent event = fixture.event();

            fixture.router.onBlockBreak(event);

            assertTrue(event.isCancelled());
            assertTrue(event.isDropItems());
            fixture.assertNoSideEffects();
            fixture.queries.verify(() -> EngineBukkitOps.getRegion(fixture.engine, fixture.location), never());
        }
    }

    @Test
    public void coldRegionCancelsBeforeFillingAlreadyMatchedBiomeDrops() {
        try (Fixture fixture = new Fixture()) {
            fixture.queries.when(() -> EngineBukkitOps.getRegion(fixture.engine, fixture.location))
                    .thenThrow(new SavedBiomeUnavailableException("Region loading", true));
            BlockBreakEvent event = fixture.event();

            fixture.router.onBlockBreak(event);

            verify(fixture.drops).shouldDropFor(fixture.blockData, fixture.data);
            assertTrue(event.isCancelled());
            assertTrue(event.isDropItems());
            fixture.assertNoSideEffects();
        }
    }

    @Test
    public void alreadyCancelledBreakSkipsSavedLookups() {
        try (Fixture fixture = new Fixture()) {
            BlockBreakEvent event = fixture.event();
            event.setCancelled(true);

            fixture.router.onBlockBreak(event);

            assertTrue(event.isCancelled());
            fixture.queries.verifyNoInteractions();
            fixture.assertNoSideEffects();
        }
    }

    @Test
    public void otherWorldBreakSkipsSavedLookups() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.block.getWorld()).thenReturn(mock(World.class));
            BlockBreakEvent event = fixture.event();

            fixture.router.onBlockBreak(event);

            assertFalse(event.isCancelled());
            fixture.queries.verifyNoInteractions();
            fixture.assertNoSideEffects();
        }
    }

    @Test
    public void freshAttemptAfterLoadingProducesDropsOnceWithoutReplayingTheCancelledEvent() {
        try (Fixture fixture = new Fixture()) {
            fixture.queries.when(() -> EngineBukkitOps.getBiome(fixture.engine, fixture.location))
                    .thenThrow(new SavedBiomeUnavailableException("Biome loading", true))
                    .thenReturn(fixture.biome);
            BlockBreakEvent cold = fixture.event();
            fixture.router.onBlockBreak(cold);
            fixture.assertNoSideEffects();

            BlockBreakEvent retry = fixture.event();
            fixture.router.onBlockBreak(retry);

            assertTrue(cold.isCancelled());
            assertTrue(cold.isDropItems());
            assertFalse(retry.isCancelled());
            assertFalse(retry.isDropItems());
            assertEquals(1, fixture.delayed.size());
            verify(fixture.drops).fillDrops(eq(false), any());
            fixture.delayed.remove().run();

            verify(fixture.world).dropItemNaturally(fixture.location.clone().add(.5, .5, .5), fixture.item);
            assertEquals(1, fixture.markers.size());
            assertTrue(fixture.delayed.isEmpty());
        }
    }

    @Test
    public void permanentSavedBiomeFailureCancelsAndPropagates() {
        verifyReportedFailure(new SavedBiomeUnavailableException("Saved biome missing", false));
    }

    @Test
    public void loadingWithSuppressedFailureCancelsAndPropagates() {
        SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException("Biome loading", true);
        failure.addSuppressed(new IllegalStateException("Saved storage cleanup failed"));
        verifyReportedFailure(failure);
    }

    @Test
    public void protectionProbeCancelledAfterDispatchProducesNoFinalDropsOrMarkerCleanup() {
        try (Fixture fixture = new Fixture()) {
            BlockBreakEvent probe = new ProtectionProbe(fixture.block, fixture.player);
            probe.setDropItems(false);

            fixture.router.onBlockBreak(probe);
            assertFalse(probe.isCancelled());
            assertFalse(probe.isDropItems());
            assertEquals(1, fixture.delayed.size());
            probe.setCancelled(true);
            fixture.delayed.remove().run();

            verify(fixture.world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
            assertTrue(fixture.markers.isEmpty());
            verify(fixture.manager, never()).getMantle();
        }
    }

    private void verifyReportedFailure(SavedBiomeUnavailableException failure) {
        try (Fixture fixture = new Fixture()) {
            fixture.queries.when(() -> EngineBukkitOps.getBiome(fixture.engine, fixture.location)).thenThrow(failure);
            BlockBreakEvent event = fixture.event();

            assertSame(failure, assertThrows(SavedBiomeUnavailableException.class,
                    () -> fixture.router.onBlockBreak(event)));

            assertTrue(event.isCancelled());
            assertTrue(event.isDropItems());
            fixture.assertNoSideEffects();
        }
    }

    private static final class ProtectionProbe extends BlockBreakEvent {
        private ProtectionProbe(Block block, Player player) {
            super(block, player);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final IrisWorldManager manager = mock(IrisWorldManager.class);
        private final Engine engine = mock(Engine.class);
        private final IrisData data = mock(IrisData.class);
        private final World world = mock(World.class);
        private final Block block = mock(Block.class);
        private final BlockData blockData = mock(BlockData.class);
        private final Player player = mock(Player.class);
        private final Location location = new Location(world, 16, 72, 32);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisBlockDrops drops = mock(IrisBlockDrops.class);
        private final ItemStack item = mock(ItemStack.class);
        private final WorldBlockDropRouter router = new WorldBlockDropRouter(manager);
        private final Queue<Runnable> delayed = new ArrayDeque<>();
        private final Queue<Runnable> markers = new ArrayDeque<>();
        private final MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class);
        private final MockedStatic<EngineBukkitOps> queries = mockStatic(EngineBukkitOps.class);
        private final MockedStatic<J> scheduling = mockStatic(J.class);

        private Fixture() {
            IrisWorld irisWorld = mock(IrisWorld.class);
            EngineTarget target = mock(EngineTarget.class);
            IrisRegion region = mock(IrisRegion.class);
            IrisDimension dimension = mock(IrisDimension.class);
            when(manager.getEngine()).thenReturn(engine);
            when(manager.getTarget()).thenReturn(target);
            when(manager.getData()).thenReturn(data);
            when(target.getWorld()).thenReturn(irisWorld);
            when(engine.getWorld()).thenReturn(irisWorld);
            when(irisWorld.minHeight()).thenReturn(-64);
            when(engine.getDimension()).thenReturn(dimension);
            binding.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
            when(block.getWorld()).thenReturn(world);
            when(block.getX()).thenReturn(16);
            when(block.getY()).thenReturn(72);
            when(block.getZ()).thenReturn(32);
            when(block.getLocation()).thenReturn(location);
            when(block.getBlockData()).thenReturn(blockData);
            when(biome.getBlockDrops()).thenReturn(new KList<>(drops));
            when(region.getBlockDrops()).thenReturn(new KList<>());
            when(dimension.getBlockDrops()).thenReturn(new KList<>());
            when(drops.shouldDropFor(blockData, data)).thenReturn(true);
            when(drops.isReplaceVanillaDrops()).thenReturn(true);
            doAnswer(invocation -> {
                KList<ItemStack> results = invocation.getArgument(1);
                results.add(item);
                return null;
            }).when(drops).fillDrops(eq(false), any());
            queries.when(() -> EngineBukkitOps.getBiome(engine, location)).thenReturn(biome);
            queries.when(() -> EngineBukkitOps.getRegion(engine, location)).thenReturn(region);
            when(manager.managedTask(anyString(), any(Runnable.class)))
                    .thenAnswer(invocation -> invocation.getArgument(1));
            scheduling.when(() -> J.runAt(eq(location), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
                delayed.add(invocation.getArgument(1));
                return true;
            });
            scheduling.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                markers.add(invocation.getArgument(0));
                return null;
            });
        }

        private BlockBreakEvent event() {
            return new BlockBreakEvent(block, player);
        }

        private void assertNoSideEffects() {
            verify(drops, never()).fillDrops(anyBoolean(), any());
            verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
            verify(manager, never()).getMantle();
            scheduling.verifyNoInteractions();
            assertTrue(delayed.isEmpty());
            assertTrue(markers.isEmpty());
        }

        @Override
        public void close() {
            scheduling.close();
            queries.close();
            binding.close();
        }
    }
}
