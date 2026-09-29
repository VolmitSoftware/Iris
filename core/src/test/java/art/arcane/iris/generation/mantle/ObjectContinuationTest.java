package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.storage.matter.IrisMatterContext;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectContinuationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void onlyAcceptedCrossingPlacementsLeaveFutureFragments() throws Exception {
        Fixture fixture = new Fixture();
        ObjectDestinationTransaction source = new ObjectDestinationTransaction(fixture.writer, 0, 0);
        int checkpoint = source.beginObjectPlacement();
        source.setData(1, 5, 1, "inside");
        source.endObjectPlacement(checkpoint);
        checkpoint = source.beginObjectPlacement();
        source.setData(15, 5, 1, "crossing");
        source.setData(16, 5, 1, "crossing");
        source.endObjectPlacement(checkpoint);
        checkpoint = source.beginObjectPlacement();
        source.setData(32, 5, 1, "unrelated");
        source.setData(48, 5, 1, "unrelated");
        source.endObjectPlacement(checkpoint);
        source.setData(17, 5, 1, "rejected");
        ObjectSourcePlan plan = source.sourcePlanSince(0);

        plan.persistContinuations(fixture.writer, 0, 0, 0, 0);
        plan.persistContinuations(fixture.writer, 0, 0, 0, 0);

        assertEquals(1, fixture.bundles.size());
        ObjectContinuationBundle bundle = fixture.bundles.get(1);
        assertEquals(1, bundle.fragments().size());
        ObjectContinuationBundle.Fragment fragment = bundle.fragments().getFirst();
        assertEquals(new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, 1), fragment.key());
        assertEquals(List.of(new ObjectContinuationBundle.ChunkPosition(0, 0), new ObjectContinuationBundle.ChunkPosition(1, 0)), fragment.touchedChunks());
        Matter decoded = fragment.decode();
        assertEquals("crossing", decoded.getSlice(String.class).get(0, 5, 1));
        assertNull(decoded.getSlice(String.class).get(1, 5, 1));
    }

    @Test
    public void packedCrossingFragmentsKeepEachPlacementRunInSourceOrder() throws Exception {
        Fixture fixture = new Fixture();
        List<ObjectDestinationTransaction.Mutation> mutations = List.of(
                marker(1, "a"), marker(17, "a"), marker(33, "a"),
                marker(5, "b"),
                marker(18, "loose"),
                marker(40, "c"), marker(20, "c"), marker(2, "c"), marker(41, "d"));
        ObjectSourcePlan plan = new ObjectSourcePlan(mutations, List.of(
                new ObjectDestinationTransaction.PlacementRange(0, 3),
                new ObjectDestinationTransaction.PlacementRange(3, 4),
                new ObjectDestinationTransaction.PlacementRange(5, 9)));

        plan.persistContinuations(fixture.writer, 0, 0, 0, 0);

        ObjectContinuationBundle.ChunkPosition zero = new ObjectContinuationBundle.ChunkPosition(0, 0);
        ObjectContinuationBundle.ChunkPosition one = new ObjectContinuationBundle.ChunkPosition(1, 0);
        ObjectContinuationBundle.ChunkPosition two = new ObjectContinuationBundle.ChunkPosition(2, 0);
        assertEquals(2, fixture.bundles.size());
        assertFragment(fixture.bundles.get(1).fragments().get(0), 0, new ObjectContinuationBundle.Bounds(1, 1, 33, 1),
                List.of(zero, one, two), List.of(marker(17, "a")));
        assertFragment(fixture.bundles.get(1).fragments().get(1), 2, new ObjectContinuationBundle.Bounds(2, 1, 41, 1),
                List.of(two, one, zero), List.of(marker(20, "c")));
        assertFragment(fixture.bundles.get(2).fragments().get(0), 0, new ObjectContinuationBundle.Bounds(1, 1, 33, 1),
                List.of(zero, one, two), List.of(marker(33, "a")));
        assertFragment(fixture.bundles.get(2).fragments().get(1), 2, new ObjectContinuationBundle.Bounds(2, 1, 41, 1),
                List.of(two, one, zero), List.of(marker(40, "c"), marker(41, "d")));
    }

    private static void assertFragment(ObjectContinuationBundle.Fragment fragment, int ordinal, ObjectContinuationBundle.Bounds bounds,
                                       List<ObjectContinuationBundle.ChunkPosition> touched,
                                       List<ObjectDestinationTransaction.Mutation> mutations) throws IOException {
        assertEquals(new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, ordinal), fragment.key());
        assertEquals(bounds, fragment.bounds());
        assertEquals(touched, fragment.touchedChunks());
        Matter decoded = fragment.decode();
        assertEquals(mutations.size(), decoded.getSlice(String.class).getEntryCount());
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            ObjectDestinationTransaction.SetMutation set = (ObjectDestinationTransaction.SetMutation) mutation;
            assertEquals(set.value(), decoded.getSlice(String.class).get(set.x() & 15, set.key().y(), set.z() & 15));
        }
    }

    @Test
    public void generatedDestinationsDoNotRetainContinuations() {
        Fixture fixture = new Fixture();
        when(fixture.chunk.isFlagged(MantleFlag.REAL)).thenReturn(true);
        List<ObjectDestinationTransaction.Mutation> mutations = List.of(marker(-1, "left"), marker(0, "right"));
        ObjectSourcePlan plan = new ObjectSourcePlan(mutations, List.of(new ObjectDestinationTransaction.PlacementRange(0, 2)));

        plan.persistContinuations(fixture.writer, -1, 0, -1, 0);

        assertTrue(fixture.bundles.isEmpty());
    }

    @Test
    public void nestedMatterRoundTripPreservesBlocksCustomIdentifiersTreesAndTiles() throws Exception {
        IrisMatterSupport.ensureRegistered();
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        NativeBlockState base = mock(NativeBlockState.class);
        NativeBlockState custom = mock(NativeBlockState.class);
        NativeBlockState air = mock(NativeBlockState.class);
        when(base.key()).thenReturn("minecraft:chest");
        when(air.key()).thenReturn("minecraft:air");
        when(custom.placementBaseState()).thenReturn(base);
        when(custom.deferredPlacementKey()).thenReturn("test:custom_chest");
        when(platform.registries()).thenReturn(registries);
        when(registries.decodeBlockState("minecraft:chest")).thenReturn(base);
        when(registries.decodeBlockState("minecraft:air")).thenReturn(air);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        TileData.TileReader previousReader = TileData.bindPlatformReader(input -> new TileData(input.readUTF(),
                new Gson().fromJson(input.readUTF(), new TypeToken<KMap<String, Object>>() { })));
        try {
            KMap<String, Object> properties = new KMap<>();
            properties.put("CustomName", "continued chest");
            TileWrapper tile = new TileWrapper(new TileData("minecraft:chest", properties));
            List<ObjectDestinationTransaction.Mutation> mutations = List.of(
                    new ObjectDestinationTransaction.CustomBlockMutation(key(NativeBlockState.class), custom),
                    new ObjectDestinationTransaction.SetMutation(key(String.class), "tree@42"),
                    new ObjectDestinationTransaction.SetMutation(key(TreeBlockMaterial.class), TreeBlockMaterial.of("minecraft:oak_log")),
                    new ObjectDestinationTransaction.SetMutation(key(TileWrapper.class), tile),
                    new ObjectDestinationTransaction.SetMutation(new ObjectDestinationTransaction.DataKey(17, 5, 1, NativeBlockState.class), air));
            ObjectContinuationBundle.Fragment fragment = new ObjectContinuationBundle.Fragment(
                    new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, 2), new ObjectContinuationBundle.Bounds(15, 1, 17, 1),
                    List.of(new ObjectContinuationBundle.ChunkPosition(0, 0), new ObjectContinuationBundle.ChunkPosition(1, 0)),
                    ObjectSourcePlan.encode(mutations, 64));
            Matter outer = new IrisMatter(16, 16, 16);
            outer.slice(ObjectContinuationBundle.class).set(0, 0, 0, new ObjectContinuationBundle(List.of(fragment)));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            outer.write(output);
            Matter loaded = Matter.read(new ByteArrayInputStream(output.toByteArray()));
            ObjectContinuationBundle saved = loaded.<ObjectContinuationBundle>getSlice(ObjectContinuationBundle.class).get(0, 0, 0);
            Matter payload = saved.fragments().getFirst().decode();

            assertSame(base, payload.getSlice(NativeBlockState.class).get(0, 5, 1));
            assertSame(air, payload.getSlice(NativeBlockState.class).get(1, 5, 1));
            assertEquals(Identifier.fromString("test:custom_chest"), payload.getSlice(Identifier.class).get(0, 5, 1));
            assertEquals("tree@42", payload.getSlice(String.class).get(0, 5, 1));
            assertEquals(TreeBlockMaterial.of("minecraft:oak_log"), payload.getSlice(TreeBlockMaterial.class).get(0, 5, 1));
            assertEquals(tile, payload.getSlice(TileWrapper.class).get(0, 5, 1));
            assertFalse(saved.fragments().getFirst().encodedSize() == 0);
        } finally {
            TileData.restorePlatformReader(previousReader);
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void registryMetadataDecodesUsingItsOriginPackContext() throws Exception {
        IrisSpawner spawner = new IrisSpawner();
        spawner.setLoadKey("old-only");
        IrisData origin = mock(IrisData.class);
        IrisData current = mock(IrisData.class);
        ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
        when(loader.load("old-only")).thenReturn(spawner);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisSpawner.class, loader);
        when(origin.getLoaders()).thenReturn(loaders);
        when(current.getLoaders()).thenReturn(new KMap<>());
        ObjectContinuationBundle.Fragment fragment = new ObjectContinuationBundle.Fragment(
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, 0), new ObjectContinuationBundle.Bounds(15, 1, 16, 1),
                List.of(new ObjectContinuationBundle.ChunkPosition(0, 0), new ObjectContinuationBundle.ChunkPosition(1, 0)),
                ObjectSourcePlan.encode(List.of(new ObjectDestinationTransaction.SetMutation(key(IrisSpawner.class), spawner)), 64));

        try (IrisMatterContext.Scope outer = IrisMatterContext.open(current)) {
            assertThrows(IOException.class, fragment::decode);
            try (IrisMatterContext.Scope scope = IrisMatterContext.open(origin)) {
                Matter restored = fragment.decode();
                assertSame(spawner, restored.getSlice(IrisSpawner.class).get(0, 5, 1));
            }
            assertSame(current, IrisMatterContext.require());
        }
    }

    @Test
    public void malformedNestedPayloadCannotSilentlyDropObjectData() {
        byte[] payload = ObjectSourcePlan.encode(List.of(marker(16, "tree@42")), 64);
        for (int size : new int[]{payload.length - 1, payload.length + 1}) {
            ObjectContinuationBundle.Fragment fragment = new ObjectContinuationBundle.Fragment(
                    new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, 0), new ObjectContinuationBundle.Bounds(15, 1, 16, 1),
                    List.of(new ObjectContinuationBundle.ChunkPosition(0, 0), new ObjectContinuationBundle.ChunkPosition(1, 0)),
                    Arrays.copyOf(payload, size));
            assertThrows(IOException.class, fragment::decode);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void directProducerSkipsRealAndDuplicatePayloadSuppliers() {
        IrisMatterSupport.ensureRegistered();
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> saved = mock(MantleChunk.class);
        MantleChunk<Matter> future = mock(MantleChunk.class);
        when(mantle.useChunk(0, 0)).thenReturn(saved);
        when(mantle.useChunk(1, 0)).thenReturn(future);
        when(saved.isFlagged(MantleFlag.REAL)).thenReturn(true);
        Matter section = new IrisMatter(16, 16, 16);
        when(future.getOrCreate(0)).thenReturn(section);
        AtomicInteger supplied = new AtomicInteger();
        Supplier<Matter> supplier = () -> {
            supplied.incrementAndGet();
            Matter payload = new IrisMatter(16, 64, 16);
            payload.slice(String.class).set(0, 5, 1, "tree@1");
            return payload;
        };
        Map<ObjectContinuationBundle.ChunkPosition, Supplier<Matter>> destinations = Map.of(
                new ObjectContinuationBundle.ChunkPosition(0, 0), supplier,
                new ObjectContinuationBundle.ChunkPosition(1, 0), supplier);
        ObjectContinuationBundle.Bounds bounds = new ObjectContinuationBundle.Bounds(15, 1, 16, 1);
        ObjectContinuationBundle.PlacementKey biome = new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, 0);
        ObjectContinuationPersistence.persist(mantle, biome, bounds, destinations);
        ObjectContinuationPersistence.persist(mantle, biome, bounds, destinations);
        assertEquals(1, supplied.get());

        ObjectContinuationBundle.PlacementKey structure = new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.STRUCTURE, 0, 0, 0);
        ObjectContinuationPersistence.persist(mantle, structure, bounds, destinations);
        assertEquals(2, supplied.get());
        assertEquals(2, section.<ObjectContinuationBundle>getSlice(ObjectContinuationBundle.class).get(0, 0, 0).fragments().size());
    }

    private static ObjectDestinationTransaction.DataKey key(Class<?> type) {
        return new ObjectDestinationTransaction.DataKey(16, 5, 1, type);
    }

    private static ObjectDestinationTransaction.Mutation marker(int x, String marker) {
        return new ObjectDestinationTransaction.SetMutation(new ObjectDestinationTransaction.DataKey(x, 5, 1, String.class), marker);
    }

    private static final class Fixture {
        private final MantleWriter writer = mock(MantleWriter.class);
        private final MantleChunk<Matter> chunk;
        private final Map<Integer, ObjectContinuationBundle> bundles = new HashMap<>();

        @SuppressWarnings("unchecked")
        private Fixture() {
            chunk = mock(MantleChunk.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            Engine engine = mock(Engine.class);
            when(writer.getMantle()).thenReturn(mantle);
            when(mantle.getWorldHeight()).thenReturn(64);
            when(writer.getEngine()).thenReturn(engine);
            when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
            when(writer.acquireChunk(anyInt(), anyInt())).thenReturn(chunk);
            when(writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), any())).thenAnswer(call -> bundles.get(((Integer) call.getArgument(0)) >> 4));
            doAnswer(call -> {
                Runnable task = call.getArgument(2);
                task.run();
                return null;
            }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
            doAnswer(call -> {
                ObjectContinuationBundle bundle = call.getArgument(3);
                bundles.put(((Integer) call.getArgument(0)) >> 4, bundle);
                return null;
            }).when(writer).setData(anyInt(), anyInt(), anyInt(), any(ObjectContinuationBundle.class));
        }
    }
}
