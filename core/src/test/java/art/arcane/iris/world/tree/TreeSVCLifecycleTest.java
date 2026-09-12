package art.arcane.iris.world.tree;

import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.GenerationSessionManager;
import art.arcane.iris.generation.runtime.GenerationTransitionGate;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.data.Cuboid;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.world.StructureGrowEvent;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TreeSVCLifecycleTest {
    @Test
    public void growthDuringCutoverLeavesOwnerFreeForCheckpointAndRetriesAfterward() throws Exception {
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ExecutorService reloader = Executors.newSingleThreadExecutor();
        Fixture fixture = new Fixture();
        AtomicInteger checkpoints = new AtomicInteger();
        try {
            StructureGrowEvent first = fixture.event();
            owner.submit(() -> fixture.grow(first)).get(2L, TimeUnit.SECONDS);
            assertFalse(first.isCancelled());
            assertEquals(1, fixture.biomeReads.get());

            StructureGrowEvent during = fixture.event();
            CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
                try (GenerationTransitionGate.Transition ignored = fixture.sessions.transitionGate().beginTransition(2_000L)) {
                    fixture.sessions.sealAndAwait("Studio generation cutover", 2_000L);
                    Future<?> growth = owner.submit(() -> fixture.grow(during));
                    owner.submit(checkpoints::incrementAndGet).get(2L, TimeUnit.SECONDS);
                    growth.get(2L, TimeUnit.SECONDS);
                    assertTrue(during.isCancelled());
                    assertEquals(1, fixture.biomeReads.get());
                    fixture.sessions.activateNextSession();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }, reloader);
            cutover.get(4L, TimeUnit.SECONDS);

            StructureGrowEvent after = fixture.event();
            owner.submit(() -> fixture.grow(after)).get(2L, TimeUnit.SECONDS);
            assertFalse(after.isCancelled());
            assertEquals(2, fixture.biomeReads.get());
            assertEquals(1, checkpoints.get());
            assertEquals(0, fixture.sessions.activeLeases());
        } finally {
            owner.shutdownNow();
            reloader.shutdownNow();
            assertTrue(owner.awaitTermination(3L, TimeUnit.SECONDS));
            assertTrue(reloader.awaitTermination(3L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void disabledTreeOverridesLeaveNormalGrowthUntouched() {
        Fixture fixture = new Fixture();
        fixture.dimension.getTreeSettings().setEnabled(false);
        StructureGrowEvent event = fixture.event();

        fixture.grow(event);

        assertFalse(event.isCancelled());
        assertEquals(0, fixture.biomeReads.get());
        assertEquals(0, fixture.sessions.activeLeases());
    }

    private static final class Fixture {
        private final GenerationSessionManager sessions = new GenerationSessionManager(true);
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final IrisDimension dimension = new IrisDimension();
        private final PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        private final TreeSVC service = spy(new TreeSVC());
        private final AtomicInteger biomeReads = new AtomicInteger();

        private Fixture() {
            dimension.getTreeSettings().setEnabled(true);
            EngineTarget target = mock(EngineTarget.class);
            IrisWorld irisWorld = mock(IrisWorld.class);
            Block block = mock(Block.class);
            BlockData data = mock(BlockData.class);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            when(engine.getDimension()).thenReturn(dimension);
            when(generator.getEngine()).thenReturn(engine);
            when(generator.getTarget()).thenReturn(target);
            when(target.getWorld()).thenReturn(irisWorld);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(block.getBlockData()).thenReturn(data);
            when(data.clone()).thenReturn(data);
            doReturn(mock(Cuboid.class)).when(service).getSaplings(any(), any(), any());
            when(engine.getBiome(8, 80, 8)).thenAnswer(invocation -> {
                try (GenerationTransitionGate.Participation ignored = sessions.transitionGate().enter()) {
                    biomeReads.incrementAndGet();
                    return new IrisBiome();
                }
            });
            when(engine.getRegion(8, 80, 8)).thenReturn(new IrisRegion());
        }

        private StructureGrowEvent event() {
            return new StructureGrowEvent(new Location(world, 8, 80, 8), TreeType.TREE, false, null, List.of());
        }

        private void grow(StructureGrowEvent event) {
            try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
                toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
                service.on(event);
            }
        }
    }
}
