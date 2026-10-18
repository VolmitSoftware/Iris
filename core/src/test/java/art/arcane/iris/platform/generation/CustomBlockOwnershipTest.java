package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.mantle.CustomBlockOwnership;
import art.arcane.iris.generation.mantle.DeferredPlacementRecords;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.CustomBlockOwnershipMatter;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterTest;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CustomBlockOwnershipTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int MIN_HEIGHT = -64;
    private static final NativeBlockPoint WORLD_POSITION = new NativeBlockPoint(19, 70, -11);
    private static final NativeBlockPoint MANTLE_POSITION = new NativeBlockPoint(3, 134, 5);
    private static final CustomBlockOwnership.Entry ORIGINAL = new CustomBlockOwnership.Entry(
            Identifier.fromString("craftengine:example/chair"), "original-entity", false);
    private static final CustomBlockOwnership.Entry REPLACEMENT = new CustomBlockOwnership.Entry(
            Identifier.fromString("craftengine:example/chair"), "replacement-entity", false);

    private IrisPlatform previous;
    private PlatformScheduler scheduler;
    private EnginePlatformHooks hooks;
    private Engine engine;
    private NativeWorld world;
    private MantleChunk<Matter> chunk;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        PreObjectMatterTest.setUpBukkit();
        IrisMatterSupport.ensureRegistered();
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block("AIR")).thenReturn(mock(NativeBlockState.class));
        when(platform.registries()).thenReturn(registries);
        scheduler = mock(PlatformScheduler.class);
        when(platform.scheduler()).thenReturn(scheduler);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        chunk = new MantleChunk<>(24, 1, -1,
                IrisEngineMantle.createRuntimeDataAdapter(mock(IrisData.class)), MantleHooks.NONE);
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.useChunk(1, -1)).thenAnswer(invocation -> chunk.use());
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        world = mock(NativeWorld.class);
        when(world.isChunkLoaded(1, -1)).thenReturn(true);
        IrisWorld irisWorld = mock(IrisWorld.class);
        when(irisWorld.platformWorld()).thenReturn(world);
        when(irisWorld.minHeight()).thenReturn(MIN_HEIGHT);
        engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(irisWorld);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        hooks = mock(EnginePlatformHooks.class);
        IrisServices.register(EnginePlatformHooks.class, hooks);
    }

    @After
    public void tearDown() {
        IrisServices.remove(EnginePlatformHooks.class);
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void materializedFurnitureEnablesTrackingAndRemovesOnOwningThread() {
        remember(ORIGINAL);
        NativeBlockChangeSet changes = changes();
        assertNotSame(NativeBlockChangeSet.disabled(), changes);

        DeferredPlacementRecords.invalidate(engine, changes);

        verify(hooks, never()).removeCustomBlock(any(), any(), any());
        CustomBlockOwnership.Entry pending = pendingOriginal();
        assertEquals(List.of(pending), ownership().entries());
        when(hooks.removeCustomBlock(engine, WORLD_POSITION, pending)).thenReturn(true);
        scheduledTask().run();

        verify(hooks).removeCustomBlock(engine, WORLD_POSITION, pending);
        assertNull(ownership());
    }

    @Test
    public void queuedRemovalKeepsReplacementOwnership() {
        remember(ORIGINAL);
        DeferredPlacementRecords.invalidate(engine, changes());
        remember(REPLACEMENT);
        when(hooks.removeCustomBlock(engine, WORLD_POSITION, pendingOriginal())).thenReturn(true);

        scheduledTask().run();

        assertEquals(List.of(REPLACEMENT), ownership().entries());
        verify(hooks, never()).removeCustomBlock(eq(engine), eq(WORLD_POSITION), eq(REPLACEMENT));
    }

    @Test
    public void unloadedChunkRetainsRemovalUntilLoadedAndRetriesDeclinedCleanup() {
        remember(ORIGINAL);
        DeferredPlacementRecords.invalidate(engine, changes());
        when(world.isChunkLoaded(1, -1)).thenReturn(false);

        scheduledTask().run();

        verify(hooks, never()).removeCustomBlock(any(), any(), any());
        when(world.isChunkLoaded(1, -1)).thenReturn(true);
        DeferredPlacementRecords.removeInvalidated(engine, 1, -1);
        assertEquals(List.of(pendingOriginal()), ownership().entries());
        when(hooks.removeCustomBlock(engine, WORLD_POSITION, pendingOriginal())).thenReturn(true);

        DeferredPlacementRecords.removeInvalidated(engine, 1, -1);

        assertNull(ownership());
    }

    @Test
    public void ownershipPersistsPendingAndReplacementTokens() throws Exception {
        CustomBlockOwnership expected = new CustomBlockOwnership(List.of(pendingOriginal(), REPLACEMENT));
        CustomBlockOwnershipMatter matter = new CustomBlockOwnershipMatter();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.writeNode(expected, new DataOutputStream(bytes));

        assertEquals(expected, matter.readNode(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    public void invalidationDuringProviderCallbackRemovesCompletedPlacement() {
        chunk.getOrCreate(MANTLE_POSITION.y() >> 4).slice(Identifier.class)
                .set(MANTLE_POSITION.x(), MANTLE_POSITION.y() & 15, MANTLE_POSITION.z(), ORIGINAL.identifier());
        DeferredPlacementRecords.invalidate(engine, changes());

        DeferredPlacementRecords.remember(engine, chunk, MANTLE_POSITION, ORIGINAL);

        assertEquals(List.of(pendingOriginal()), ownership().entries());
        when(hooks.removeCustomBlock(engine, WORLD_POSITION, pendingOriginal())).thenReturn(true);
        scheduledTask().run();
        assertNull(ownership());
    }

    @Test(timeout = 10000)
    public void providerCallbackAllowsConcurrentInvalidationAndSkipsCanceledSnapshotEntries() {
        chunk.getOrCreate(MANTLE_POSITION.y() >> 4).slice(Identifier.class)
                .set(MANTLE_POSITION.x(), MANTLE_POSITION.y() & 15, MANTLE_POSITION.z(), ORIGINAL.identifier());
        chunk.getOrCreate(MANTLE_POSITION.y() >> 4).slice(Identifier.class)
                .set(MANTLE_POSITION.x() + 1, MANTLE_POSITION.y() & 15, MANTLE_POSITION.z(), ORIGINAL.identifier());
        NativeBlockChangeSet changes = changes();
        changes.add(WORLD_POSITION.x() + 1, WORLD_POSITION.y(), WORLD_POSITION.z());
        World bukkitWorld = mock(World.class);
        when(bukkitWorld.getMinHeight()).thenReturn(MIN_HEIGHT);
        when(bukkitWorld.getMaxHeight()).thenReturn(320);
        Chunk bukkitChunk = mock(Chunk.class);
        when(bukkitChunk.getWorld()).thenReturn(bukkitWorld);
        Block started = mock(Block.class);
        Block canceled = mock(Block.class);
        when(bukkitChunk.getBlock(3, 70, 5)).thenReturn(started);
        when(bukkitChunk.getBlock(4, 70, 5)).thenReturn(canceled);
        ExternalDataSVC provider = mock(ExternalDataSVC.class);
        IrisServices.register(ExternalDataSVC.class, provider);
        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor()) {
            when(provider.processUpdate(engine, started, ORIGINAL.identifier())).thenAnswer(invocation -> {
                CompletableFuture.runAsync(() -> DeferredPlacementRecords.invalidate(engine, changes), worker)
                        .get(3, TimeUnit.SECONDS);
                return ORIGINAL;
            });

            EngineBukkitOps.materializeCustomBlocks(engine, bukkitChunk, chunk);

            verify(provider).processUpdate(engine, started, ORIGINAL.identifier());
            verify(provider, never()).processUpdate(eq(engine), eq(canceled), any());
            assertEquals(List.of(pendingOriginal()), ownership().entries());
            when(hooks.removeCustomBlock(engine, WORLD_POSITION, pendingOriginal())).thenReturn(true);
            scheduledTask().run();
            assertNull(ownership());
        } finally {
            IrisServices.remove(ExternalDataSVC.class);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void queuedInvalidationKeepsSelectedMantleAndHeightAfterRuntimeChanges() {
        remember(ORIGINAL);
        DeferredPlacementRecords.invalidate(engine, changes());
        Mantle<Matter> replacementMantle = mock(Mantle.class);
        EngineMantle replacementRuntime = mock(EngineMantle.class);
        when(replacementRuntime.getMantle()).thenReturn(replacementMantle);
        when(engine.getMantle()).thenReturn(replacementRuntime);
        when(engine.getMinHeight()).thenReturn(0);
        when(hooks.removeCustomBlock(engine, WORLD_POSITION, pendingOriginal())).thenReturn(true);

        scheduledTask().run();

        assertNull(ownership());
        verify(hooks).removeCustomBlock(engine, WORLD_POSITION, pendingOriginal());
        verify(replacementMantle, never()).useChunk(anyInt(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void completedPlacementUsesSuppliedChunkWhenEngineSelectsAnotherMantle() {
        Mantle<Matter> replacementMantle = mock(Mantle.class);
        EngineMantle replacementRuntime = mock(EngineMantle.class);
        when(replacementRuntime.getMantle()).thenReturn(replacementMantle);
        when(engine.getMantle()).thenReturn(replacementRuntime);

        DeferredPlacementRecords.remember(engine, chunk, MANTLE_POSITION, ORIGINAL);

        when(engine.getMinHeight()).thenReturn(0);
        when(hooks.removeCustomBlock(engine, WORLD_POSITION, pendingOriginal())).thenReturn(true);
        scheduledTask().run();

        assertNull(ownership());
        verify(hooks).removeCustomBlock(engine, WORLD_POSITION, pendingOriginal());
        verify(replacementMantle, never()).useChunk(anyInt(), anyInt());
    }

    private void remember(CustomBlockOwnership.Entry entry) {
        chunk.getOrCreate(MANTLE_POSITION.y() >> 4).slice(Identifier.class)
                .set(MANTLE_POSITION.x(), MANTLE_POSITION.y() & 15, MANTLE_POSITION.z(), entry.identifier());
        DeferredPlacementRecords.remember(engine, chunk, MANTLE_POSITION, entry);
    }

    private NativeBlockChangeSet changes() {
        NativeBlockChangeSet changes = DeferredPlacementRecords.track(engine,
                new NativeBlockChangeSet.Bounds(1, -1, 1, -1, MIN_HEIGHT, 384));
        changes.add(WORLD_POSITION.x(), WORLD_POSITION.y(), WORLD_POSITION.z());
        return changes;
    }

    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).region(eq(world), eq(1), eq(-1), task.capture());
        return task.getValue();
    }

    private CustomBlockOwnership ownership() {
        return chunk.get(MANTLE_POSITION.y() >> 4).getSlice(CustomBlockOwnership.class)
                .get(MANTLE_POSITION.x(), MANTLE_POSITION.y() & 15, MANTLE_POSITION.z());
    }

    private static CustomBlockOwnership.Entry pendingOriginal() {
        return new CustomBlockOwnership.Entry(ORIGINAL.identifier(), ORIGINAL.token(), true);
    }
}
