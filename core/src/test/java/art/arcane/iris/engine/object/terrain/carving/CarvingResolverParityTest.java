package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.engine.object.value.IRare;
import com.volmit.config.Range;
import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CarvingResolverParityTest {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;

    @BeforeClass
    public static void setupBukkit() {
        if (Bukkit.getServer() != null) {
            return;
        }

        Server server = mock(Server.class);
        doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
        doReturn("IrisTestServer").when(server).getName();
        doReturn("1.0").when(server).getVersion();
        doReturn("1.0").when(server).getBukkitVersion();
        doAnswer((InvocationOnMock invocation) -> namedBlockData(invocation.getArgument(0, Material.class).name().toLowerCase(Locale.ROOT))).when(server).createBlockData(any(Material.class));
        doAnswer((InvocationOnMock invocation) -> namedBlockData(invocation.getArgument(0, String.class))).when(server).createBlockData(anyString());
        try {
            Bukkit.setServer(server);
        } catch (Throwable ignored) {
        }
    }

    private static BlockData namedBlockData(String key) {
        String canonical = key.indexOf(':') >= 0 ? key : "minecraft:" + key;
        BlockData data = mock(BlockData.class);
        doReturn(canonical).when(data).getAsString();
        return data;
    }

    @Test
    public void resolverStatefulOverloadsMatchLegacyResolverAcrossSampleGrid() {
        Fixture fixture = createFixture();
        CarvingResolver.State state = new CarvingResolver.State();

        for (int worldY = -64; worldY <= 320; worldY += 11) {
            ResolvedCarvingEntry legacyRoot = legacyResolveRootEntry(fixture.engine, worldY);
            ResolvedCarvingEntry statefulRoot = CarvingResolver.resolveRootEntry(fixture.engine, worldY, state);
            assertSame("root mismatch at worldY=" + worldY, legacyRoot, statefulRoot);

            for (int worldX = -384; worldX <= 384; worldX += 29) {
                for (int worldZ = -384; worldZ <= 384; worldZ += 31) {
                    ResolvedCarvingEntry legacyResolved = legacyResolveFromRoot(fixture.engine, legacyRoot, worldX, worldZ);
                    ResolvedCarvingEntry statefulResolved = CarvingResolver.resolveFromRoot(fixture.engine, statefulRoot, worldX, worldZ, state);
                    assertSame("entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, legacyResolved, statefulResolved);
                }
            }
        }
    }

    @Test
    public void resolverStatefulOverloadsMatchLegacyResolverAcrossMixedDepthGraph() {
        Fixture fixture = createMixedDepthFixture();
        CarvingResolver.State state = new CarvingResolver.State();

        for (int worldY = -64; worldY <= 320; worldY += 17) {
            ResolvedCarvingEntry legacyRoot = legacyResolveRootEntry(fixture.engine, worldY);
            ResolvedCarvingEntry statefulRoot = CarvingResolver.resolveRootEntry(fixture.engine, worldY, state);
            assertSame("mixed root mismatch at worldY=" + worldY, legacyRoot, statefulRoot);

            for (int worldX = -640; worldX <= 640; worldX += 79) {
                for (int worldZ = -640; worldZ <= 640; worldZ += 83) {
                    ResolvedCarvingEntry legacyResolved = legacyResolveFromRoot(fixture.engine, legacyRoot, worldX, worldZ);
                    ResolvedCarvingEntry statefulResolved = CarvingResolver.resolveFromRoot(fixture.engine, statefulRoot, worldX, worldZ, state);
                    assertSame("mixed entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, legacyResolved, statefulResolved);
                }
            }
        }
    }

    @Test
    public void columnAnchoredChunkPlanResolutionIsStableAcrossRepeatedBuilds() {
        Fixture fixture = createMixedDepthFixture();
        CarvingResolver.State state = new CarvingResolver.State();
        ResolvedCarvingEntry root = legacyResolveRootEntry(fixture.engine, 80);

        for (int chunkX = -24; chunkX <= 24; chunkX += 6) {
            for (int chunkZ = -24; chunkZ <= 24; chunkZ += 6) {
                ResolvedCarvingEntry[] firstPlan = buildColumnPlan(fixture.engine, root, chunkX, chunkZ, state);
                ResolvedCarvingEntry[] secondPlan = buildColumnPlan(fixture.engine, root, chunkX, chunkZ, state);
                for (int columnIndex = 0; columnIndex < firstPlan.length; columnIndex++) {
                    assertSame(
                            "column plan mismatch at chunkX=" + chunkX + " chunkZ=" + chunkZ + " columnIndex=" + columnIndex,
                            firstPlan[columnIndex],
                            secondPlan[columnIndex]
                    );
                }
            }
        }
    }

    @Test
    public void sharedResolverStateNeverCarriesDimensionEntriesAcrossEngines() {
        Fixture first = createFixture();
        Fixture second = createMixedDepthFixture();
        CarvingResolver.State state = new CarvingResolver.State();

        ResolvedCarvingEntry firstExpected = legacyResolveRootEntry(first.engine, 80);
        ResolvedCarvingEntry secondExpected = legacyResolveRootEntry(second.engine, 80);

        assertSame(firstExpected, CarvingResolver.resolveRootEntry(first.engine, 80, state));
        assertSame(secondExpected, CarvingResolver.resolveRootEntry(second.engine, 80, state));
        assertSame(firstExpected, CarvingResolver.resolveRootEntry(first.engine, 80, state));
    }

    @Test
    public void threadLocalResolverNeverCarriesDimensionEntriesAcrossEngines() {
        Fixture first = createFixture();
        Fixture second = createMixedDepthFixture();
        int worldY = 83;

        ResolvedCarvingEntry firstExpected = legacyResolveRootEntry(first.engine, worldY);
        ResolvedCarvingEntry secondExpected = legacyResolveRootEntry(second.engine, worldY);

        assertSame(firstExpected, CarvingResolver.resolveRootEntry(first.engine, worldY));
        assertSame(secondExpected, CarvingResolver.resolveRootEntry(second.engine, worldY));
    }

    @Test
    public void threadLocalResolverInvalidatesWhenTheSameEnginePublishesReplacementData() {
        Fixture first = createFixture();
        Fixture replacement = createMixedDepthFixture();
        AtomicReference<IrisDimension> dimension = new AtomicReference<>(first.engine.getDimension());
        AtomicReference<IrisData> data = new AtomicReference<>(first.engine.getData());
        AtomicReference<EngineGeneration> generation = new AtomicReference<>(first.engine.getGeneration());
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doAnswer((InvocationOnMock invocation) -> dimension.get()).when(engine).getDimension();
        doAnswer((InvocationOnMock invocation) -> data.get()).when(engine).getData();
        doAnswer((InvocationOnMock invocation) -> generation.get()).when(engine).getGeneration();

        int worldY = 83;
        ResolvedCarvingEntry firstExpected = legacyResolveRootEntry(first.engine, worldY);
        ResolvedCarvingEntry replacementExpected = legacyResolveRootEntry(replacement.engine, worldY);
        assertSame(firstExpected, CarvingResolver.resolveRootEntry(engine, worldY));

        dimension.set(replacement.engine.getDimension());
        data.set(replacement.engine.getData());
        generation.set(replacement.engine.getGeneration());
        assertSame(replacementExpected, CarvingResolver.resolveRootEntry(engine, worldY));
    }

    @Test
    public void resolverStateUsesWeakRuntimeIdentityBindings() throws NoSuchFieldException {
        Field engineIdentity = CarvingResolver.State.class.getDeclaredField("engineIdentity");
        Field dimensionIdentity = CarvingResolver.State.class.getDeclaredField("dimensionIdentity");
        Field dataIdentity = CarvingResolver.State.class.getDeclaredField("dataIdentity");

        assertSame(WeakReference.class, engineIdentity.getType());
        assertSame(WeakReference.class, dimensionIdentity.getType());
        assertSame(WeakReference.class, dataIdentity.getType());
    }

    @Test(timeout = 20_000L)
    public void longLivedWorkerDoesNotRetainPopulatedThreadLocalStateOrEngine() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        try {
            LifetimeReferences references = executor.submit(this::populateThreadLocalLifetimeReferences).get();
            Future<?> blocker = executor.submit(() -> {
                blockerStarted.countDown();
                releaseBlocker.await();
                return null;
            });
            assertTrue(blockerStarted.await(5L, TimeUnit.SECONDS));

            awaitCollection(references);

            releaseBlocker.countDown();
            blocker.get(5L, TimeUnit.SECONDS);
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private LifetimeReferences populateThreadLocalLifetimeReferences() throws Exception {
        RetainedFixture fixture = createRetainedFixture();
        assertNotNull(CarvingResolver.resolveRootEntry(fixture.engine(), 80));

        Field threadStateField = CarvingResolver.class.getDeclaredField("THREAD_STATE");
        threadStateField.setAccessible(true);
        ThreadLocal<?> threadState = (ThreadLocal<?>) threadStateField.get(null);
        Object value = threadState.get();
        assertTrue(value instanceof WeakReference<?>);
        @SuppressWarnings("unchecked")
        WeakReference<CarvingResolver.State> state =
                (WeakReference<CarvingResolver.State>) value;
        CarvingResolver.State populatedState = state.get();
        assertNotNull(populatedState);
        Field biomeCacheField = CarvingResolver.State.class.getDeclaredField("biomeCache");
        biomeCacheField.setAccessible(true);
        assertFalse(((Map<?, ?>) biomeCacheField.get(populatedState)).isEmpty());
        return new LifetimeReferences(
                state,
                new WeakReference<>(fixture.engine()));
    }

    private static void awaitCollection(LifetimeReferences references) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            System.gc();
            if (references.state().get() == null
                    && references.engine().get() == null) {
                return;
            }
            byte[] pressure = new byte[1_048_576];
            pressure[0] = (byte) attempt;
            Thread.sleep(10L);
        }
        assertTrue("Thread-local State was retained", references.state().get() == null);
        assertTrue("Engine was retained", references.engine().get() == null);
    }

    @Test
    public void explicitStateRemainsStronglyCallerOwned() throws Exception {
        RetainedFixture fixture = createRetainedFixture();
        CarvingResolver.State state = new CarvingResolver.State();
        assertSame(fixture.entry(), CarvingResolver.resolveRootEntry(
                fixture.engine(), 80, state));

        Field rootEntriesField = CarvingResolver.State.class
                .getDeclaredField("rootEntriesByWorldY");
        rootEntriesField.setAccessible(true);
        System.gc();

        assertSame(fixture.entry(), ((Map<?, ?>) rootEntriesField.get(state)).get(80));
    }

    private RetainedFixture createRetainedFixture() {
        IrisData data = mock(IrisData.class);
        IrisBiome biome = new IrisBiome();
        biome.setLoader(data);

        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        doReturn(biome).when(biomeLoader).load("retained");
        doReturn(biomeLoader).when(data).getBiomeLoader();

        CarvingBands bands = new CarvingBands(List.of(buildEntry("retained", "retained", Range.of(-64, 320), 0, List.of())));
        IrisDimension dimension = new IrisDimension();

        Engine[] engine = new Engine[1];
        AtomicReference<EngineGeneration> generation = new AtomicReference<>();
        engine[0] = (Engine) Proxy.newProxyInstance(
                Engine.class.getClassLoader(),
                new Class<?>[]{Engine.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getDimension" -> dimension;
                    case "getData" -> data;
                    case "getGeneration" -> generation.updateAndGet(existing -> existing != null ? existing : generationWith(engine[0], bands));
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "carving-retention-test-engine";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        return new RetainedFixture(engine[0], bands.entries().getFirst());
    }

    private Fixture createFixture() {
        IrisBiome rootLowBiome = mock(IrisBiome.class);
        IrisBiome rootHighBiome = mock(IrisBiome.class);
        IrisBiome childABiome = mock(IrisBiome.class);
        IrisBiome childBBiome = mock(IrisBiome.class);
        IrisBiome childCBiome = mock(IrisBiome.class);
        IrisBiome fallbackBiome = mock(IrisBiome.class);
        IrisBiome surfaceBiome = mock(IrisBiome.class);

        doReturn(6).when(rootLowBiome).getRarity();
        doReturn(4).when(rootHighBiome).getRarity();
        doReturn(2).when(childABiome).getRarity();
        doReturn(5).when(childBBiome).getRarity();
        doReturn(1).when(childCBiome).getRarity();

        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        doReturn(rootLowBiome).when(biomeLoader).load("root-low");
        doReturn(rootHighBiome).when(biomeLoader).load("root-high");
        doReturn(childABiome).when(biomeLoader).load("child-a");
        doReturn(childBBiome).when(biomeLoader).load("child-b");
        doReturn(childCBiome).when(biomeLoader).load("child-c");

        IrisData data = mock(IrisData.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();

        CarvingEntry rootLow = buildEntry("root-low", "root-low", Range.of(-64, 120), 4, List.of("child-a", "child-b"));
        CarvingEntry rootHigh = buildEntry("root-high", "root-high", Range.of(121, 320), 3, List.of("child-b", "child-c"));
        CarvingEntry childA = buildEntry("child-a", "child-a", Range.of(-2048, -1024), 3, List.of("child-b"));
        CarvingEntry childB = buildEntry("child-b", "child-b", Range.of(-2048, -1024), 2, List.of("child-c", "child-a"));
        CarvingEntry childC = buildEntry("child-c", "child-c", Range.of(-2048, -1024), 1, List.of());

        CarvingBands bands = new CarvingBands(List.of(rootLow, rootHigh, childA, childB, childC));

        IrisDimension dimension = mock(IrisDimension.class);

        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(dimension).when(engine).getDimension();
        doReturn(data).when(engine).getData();
        doReturn(new SeedManager(913_531_771L)).when(engine).getSeedManager();
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        doReturn(surfaceBiome).when(engine).getSurfaceBiome(anyInt(), anyInt());
        doReturn(fallbackBiome).when(engine).getCaveBiome(anyInt(), anyInt());
        DecoratorTestEngines.withGeneration(engine).state(CarvingBands.class, ignored -> bands);

        return new Fixture(engine);
    }

    private Fixture createMixedDepthFixture() {
        IrisBiome rootLowBiome = mock(IrisBiome.class);
        IrisBiome rootHighBiome = mock(IrisBiome.class);
        IrisBiome childABiome = mock(IrisBiome.class);
        IrisBiome childBBiome = mock(IrisBiome.class);
        IrisBiome childCBiome = mock(IrisBiome.class);
        IrisBiome childDBiome = mock(IrisBiome.class);
        IrisBiome childEBiome = mock(IrisBiome.class);
        IrisBiome childFBiome = mock(IrisBiome.class);
        IrisBiome childGBiome = mock(IrisBiome.class);
        IrisBiome fallbackBiome = mock(IrisBiome.class);
        IrisBiome surfaceBiome = mock(IrisBiome.class);

        doReturn(7).when(rootLowBiome).getRarity();
        doReturn(5).when(rootHighBiome).getRarity();
        doReturn(2).when(childABiome).getRarity();
        doReturn(3).when(childBBiome).getRarity();
        doReturn(6).when(childCBiome).getRarity();
        doReturn(1).when(childDBiome).getRarity();
        doReturn(4).when(childEBiome).getRarity();
        doReturn(8).when(childFBiome).getRarity();
        doReturn(2).when(childGBiome).getRarity();

        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        doReturn(rootLowBiome).when(biomeLoader).load("root-low");
        doReturn(rootHighBiome).when(biomeLoader).load("root-high");
        doReturn(childABiome).when(biomeLoader).load("child-a");
        doReturn(childBBiome).when(biomeLoader).load("child-b");
        doReturn(childCBiome).when(biomeLoader).load("child-c");
        doReturn(childDBiome).when(biomeLoader).load("child-d");
        doReturn(childEBiome).when(biomeLoader).load("child-e");
        doReturn(childFBiome).when(biomeLoader).load("child-f");
        doReturn(childGBiome).when(biomeLoader).load("child-g");

        IrisData data = mock(IrisData.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();

        CarvingEntry rootLow = buildEntry("root-low", "root-low", Range.of(-64, 120), 7, List.of("child-a", "child-d", "child-e"));
        CarvingEntry rootHigh = buildEntry("root-high", "root-high", Range.of(121, 320), 6, List.of("child-b", "child-c", "child-f"));
        CarvingEntry childA = buildEntry("child-a", "child-a", Range.of(-4096, 4096), 5, List.of("child-b", "child-g"));
        CarvingEntry childB = buildEntry("child-b", "child-b", Range.of(-4096, 4096), 1, List.of("child-c"));
        CarvingEntry childC = buildEntry("child-c", "child-c", Range.of(-4096, 4096), 0, List.of());
        CarvingEntry childD = buildEntry("child-d", "child-d", Range.of(-4096, 4096), 6, List.of("child-e", "child-f"));
        CarvingEntry childE = buildEntry("child-e", "child-e", Range.of(-4096, 4096), 2, List.of("child-a"));
        CarvingEntry childF = buildEntry("child-f", "child-f", Range.of(-4096, 4096), 8, List.of("child-g", "child-c"));
        CarvingEntry childG = buildEntry("child-g", "child-g", Range.of(-4096, 4096), 3, List.of("child-d"));

        CarvingBands bands = new CarvingBands(List.of(rootLow, rootHigh, childA, childB, childC, childD, childE, childF, childG));

        IrisDimension dimension = mock(IrisDimension.class);

        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(dimension).when(engine).getDimension();
        doReturn(data).when(engine).getData();
        doReturn(new SeedManager(4_627_991_643L)).when(engine).getSeedManager();
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        doReturn(surfaceBiome).when(engine).getSurfaceBiome(anyInt(), anyInt());
        doReturn(fallbackBiome).when(engine).getCaveBiome(anyInt(), anyInt());
        DecoratorTestEngines.withGeneration(engine).state(CarvingBands.class, ignored -> bands);

        return new Fixture(engine);
    }

    private ResolvedCarvingEntry[] buildColumnPlan(Engine engine, ResolvedCarvingEntry rootEntry, int chunkX, int chunkZ, CarvingResolver.State state) {
        ResolvedCarvingEntry[] plan = new ResolvedCarvingEntry[256];
        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;
                int columnIndex = (localX << 4) | localZ;
                plan[columnIndex] = CarvingResolver.resolveFromRoot(engine, rootEntry, worldX, worldZ, state);
            }
        }
        return plan;
    }

    private static EngineGeneration generationWith(Engine engine, CarvingBands bands) {
        EngineGeneration generation = new EngineGeneration(engine);
        generation.state(CarvingBands.class, ignored -> bands);
        return generation;
    }

    private CarvingEntry buildEntry(String id, String biome, Range worldRange, int depth, List<String> children) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("biome", biome);
        JsonObject range = new JsonObject();
        range.addProperty("min", worldRange.min());
        range.addProperty("max", worldRange.max());
        json.add("worldYRange", range);
        json.addProperty("childRecursionDepth", depth);
        json.add("children", new Gson().toJsonTree(children));
        return new Gson().fromJson(json, CarvingEntry.class);
    }

    private ResolvedCarvingEntry legacyResolveRootEntry(Engine engine, int worldY) {
        List<ResolvedCarvingEntry> entries = CarvingBands.of(engine).entries();
        if (entries.isEmpty()) {
            return null;
        }

        ResolvedCarvingEntry resolved = null;
        for (ResolvedCarvingEntry entry : entries) {
            if (!legacyIsRootCandidate(engine, entry, worldY)) {
                continue;
            }

            resolved = entry;
        }

        return resolved;
    }

    private ResolvedCarvingEntry legacyResolveFromRoot(Engine engine, ResolvedCarvingEntry rootEntry, int worldX, int worldZ) {
        if (rootEntry == null) {
            return null;
        }

        IrisBiome rootBiome = legacyResolveEntryBiome(engine, rootEntry);
        if (rootBiome == null) {
            return null;
        }

        int remainingDepth = clampDepth(rootEntry.childRecursionDepth());
        if (remainingDepth <= 0) {
            return rootEntry;
        }

        Map<String, ResolvedCarvingEntry> entryIndex = CarvingBands.of(engine).index();
        ResolvedCarvingEntry current = rootEntry;
        int depth = remainingDepth;
        while (depth > 0) {
            ResolvedCarvingEntry selected = legacySelectChild(engine, current, worldX, worldZ, entryIndex);
            if (selected == null || selected == current) {
                break;
            }

            depth--;
            int childDepthLimit = clampDepth(selected.childRecursionDepth());
            if (childDepthLimit < depth) {
                depth = childDepthLimit;
            }
            current = selected;
        }

        return current;
    }

    private IrisBiome legacyResolveEntryBiome(Engine engine, ResolvedCarvingEntry entry) {
        if (entry == null) {
            return null;
        }

        return entry.realBiome(engine.getData());
    }

    private boolean legacyIsRootCandidate(Engine engine, ResolvedCarvingEntry entry, int worldY) {
        if (entry == null || !entry.enabled()) {
            return false;
        }

        Range worldYRange = entry.worldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return legacyResolveEntryBiome(engine, entry) != null;
    }

    private ResolvedCarvingEntry legacySelectChild(
            Engine engine,
            ResolvedCarvingEntry parent,
            int worldX,
            int worldZ,
            Map<String, ResolvedCarvingEntry> entryIndex
    ) {
        List<String> children = parent.children();
        if (children.isEmpty()) {
            return parent;
        }

        IrisBiome parentBiome = legacyResolveEntryBiome(engine, parent);
        if (parentBiome == null) {
            return parent;
        }

        KList<LegacyCarvingChoice> options = new KList<>();
        for (String childId : children) {
            if (childId == null || childId.isBlank()) {
                continue;
            }

            ResolvedCarvingEntry child = entryIndex.get(childId.trim());
            if (child == null || !child.enabled()) {
                continue;
            }

            IrisBiome childBiome = legacyResolveEntryBiome(engine, child);
            if (childBiome == null) {
                continue;
            }

            options.add(new LegacyCarvingChoice(child, rarity(childBiome)));
        }

        options.add(new LegacyCarvingChoice(parent, rarity(parentBiome)));
        if (options.size() <= 1) {
            return parent;
        }

        long seed = engine.getSeedManager().getCarve() ^ CHILD_SEED_SALT;
        CNG childGenerator = parent.childrenGenerator(seed, engine.getData());
        LegacyCarvingChoice selected = childGenerator.fitRarity(options, worldX, worldZ);
        if (selected == null || selected.entry == null) {
            return parent;
        }

        return selected.entry;
    }

    private int rarity(IrisBiome biome) {
        if (biome == null) {
            return 1;
        }

        int rarity = biome.getRarity();
        return Math.max(rarity, 1);
    }

    private int clampDepth(int depth) {
        if (depth <= 0) {
            return 0;
        }

        return Math.min(depth, MAX_CHILD_DEPTH);
    }

    private record Fixture(Engine engine) {
    }

    private record RetainedFixture(Engine engine, ResolvedCarvingEntry entry) {
    }

    private record LifetimeReferences(
            WeakReference<CarvingResolver.State> state,
            WeakReference<Engine> engine
    ) {
    }

    private static final class LegacyCarvingChoice implements IRare {
        private final ResolvedCarvingEntry entry;
        private final int rarity;

        private LegacyCarvingChoice(ResolvedCarvingEntry entry, int rarity) {
            this.entry = entry;
            this.rarity = rarity;
        }

        @Override
        public int getRarity() {
            return rarity;
        }
    }
}
