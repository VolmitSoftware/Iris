package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.mantle.DeferredPlacementRecords;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterTest;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeferredPlacementInvalidationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int MIN_HEIGHT = -64;
    private static final int SECTIONS = 24;

    @Test
    public void nativeWritesClearDeferredRecordsSoMaterializationSkipsThem() {
        withMantleData(adapter -> {
            Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();
            MantleChunk<Matter> stored = chunk(chunks, adapter, 0, 0);
            Identifier replaced = Identifier.fromString("craftengine:fence_post");
            Identifier kept = Identifier.fromString("craftengine:lantern");
            setRecord(stored, 3, 70, 5, Identifier.class, replaced);
            setRecord(stored, 3, 76, 5, Identifier.class, kept);
            setRecord(stored, 4, 70, 5, TileWrapper.class,
                    new TileWrapper(new TileData("minecraft:chest", new KMap<>())));
            Engine engine = engine(chunks, adapter);

            NativeBlockChangeSet changes = DeferredPlacementRecords.track(engine, bounds());
            changes.add(3, 70, 5);
            changes.add(4, 70, 5);
            DeferredPlacementRecords.invalidate(engine, changes);

            assertNull(record(stored, 4, 70, 5, TileWrapper.class));
            assertEquals(kept, record(stored, 3, 76, 5, Identifier.class));
            World world = mock(World.class);
            when(world.getMinHeight()).thenReturn(MIN_HEIGHT);
            when(world.getMaxHeight()).thenReturn(320);
            Chunk bukkitChunk = mock(Chunk.class);
            when(bukkitChunk.getWorld()).thenReturn(world);
            Block replacedBlock = mock(Block.class);
            Block keptBlock = mock(Block.class);
            when(bukkitChunk.getBlock(3, 70, 5)).thenReturn(replacedBlock);
            when(bukkitChunk.getBlock(3, 76, 5)).thenReturn(keptBlock);
            ExternalDataSVC provider = mock(ExternalDataSVC.class);
            try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
                services.when(() -> IrisServices.get(ExternalDataSVC.class)).thenReturn(provider);

                EngineBukkitOps.materializeCustomBlocks(engine, bukkitChunk, stored);
            }

            verify(provider).processUpdate(engine, keptBlock, kept);
            verify(provider, never()).processUpdate(eq(engine), eq(replacedBlock), any(Identifier.class));
        });
    }

    @Test
    public void regionsWithoutDeferredRecordsAreNotTracked() {
        withMantleData(adapter -> {
            Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();
            setRecord(chunk(chunks, adapter, 3, 0), 0, 70, 0, Identifier.class,
                    Identifier.fromString("craftengine:outside"));

            assertSame(NativeBlockChangeSet.disabled(),
                    DeferredPlacementRecords.track(engine(chunks, adapter), bounds()));
        });
    }

    @Test
    public void neighbouringRecordsEnableTrackingForTheWholeWriteRegion() {
        withMantleData(adapter -> {
            Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();
            setRecord(chunk(chunks, adapter, 1, -1), 0, 70, 0, TileWrapper.class,
                    new TileWrapper(new TileData("minecraft:barrel", new KMap<>())));

            NativeBlockChangeSet changes = DeferredPlacementRecords.track(engine(chunks, adapter), bounds());

            assertNotSame(NativeBlockChangeSet.disabled(), changes);
            changes.add(16, 70, -16);
            DeferredPlacementRecords.invalidate(engine(chunks, adapter), changes);
            assertNull(record(chunks.get(key(1, -1)), 0, 70, 0, TileWrapper.class));
        });
    }

    private static NativeBlockChangeSet.Bounds bounds() {
        return new NativeBlockChangeSet.Bounds(-1, -1, 1, 1, MIN_HEIGHT, 384);
    }

    @SuppressWarnings("unchecked")
    private static Engine engine(Map<Long, MantleChunk<Matter>> chunks, MantleDataAdapter<Matter> adapter) {
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(invocation ->
                chunk(chunks, adapter, invocation.getArgument(0), invocation.getArgument(1)).use());
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        IrisWorld world = mock(IrisWorld.class);
        when(world.minHeight()).thenReturn(MIN_HEIGHT);
        Engine engine = mock(Engine.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        return engine;
    }

    private static MantleChunk<Matter> chunk(Map<Long, MantleChunk<Matter>> chunks, MantleDataAdapter<Matter> adapter,
                                             int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(key(chunkX, chunkZ),
                ignored -> new MantleChunk<>(SECTIONS, chunkX, chunkZ, adapter, MantleHooks.NONE));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private static <T> void setRecord(MantleChunk<Matter> chunk, int x, int worldY, int z, Class<T> type, T value) {
        int y = worldY - MIN_HEIGHT;
        chunk.getOrCreate(y >> 4).slice(type).set(x & 15, y & 15, z & 15, value);
    }

    private static <T> T record(MantleChunk<Matter> chunk, int x, int worldY, int z, Class<T> type) {
        int y = worldY - MIN_HEIGHT;
        return chunk.get(y >> 4).getSlice(type).get(x & 15, y & 15, z & 15);
    }

    private static void withMantleData(MantleTest test) {
        PreObjectMatterTest.setUpBukkit();
        IrisMatterSupport.ensureRegistered();
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block("AIR")).thenReturn(mock(NativeBlockState.class));
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            test.run(IrisEngineMantle.createRuntimeDataAdapter(mock(IrisData.class)));
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @FunctionalInterface
    private interface MantleTest {
        void run(MantleDataAdapter<Matter> adapter);
    }
}
