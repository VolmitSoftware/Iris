package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
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
import java.util.HashMap;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class IrisDimensionCarvingResolverParityTest {
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
    public void extremeChildRarityRemainsReachableWithOneNoiseSample() {
        Fixture fixture = createFixture();
        Engine engine = fixture.engine;
        IrisData data = engine.getData();
        IrisBiome rare = data.getBiomeLoader().load("child-a");
        IrisBiome common = data.getBiomeLoader().load("child-b");
        IrisBiome parentBiome = data.getBiomeLoader().load("root-low");
        doReturn(Integer.MAX_VALUE).when(rare).getRarity();
        doReturn(1).when(common).getRarity();
        doReturn(2).when(parentBiome).getRarity();
        IrisDimensionCarvingEntry parent = spy(engine.getDimension().getCarvingEntryIndex().get("root-low"));
        parent.setChildRecursionDepth(1);
        CNG generator = mock(CNG.class);
        doReturn(0.5D).when(generator).noiseFast2D(-17D, 23D);
        doReturn(generator).when(parent).getChildrenGenerator(anyLong(), any(IrisData.class));

        IrisDimensionCarvingEntry selected = IrisDimensionCarvingResolver.resolveFromRoot(
                engine, parent, -17, 23, new IrisDimensionCarvingResolver.State());

        assertSame(engine.getDimension().getCarvingEntryIndex().get("child-a"), selected);
        verify(generator).noiseFast2D(-17D, 23D);
        verify(generator, never()).fit2D(anyInt(), anyInt(), anyDouble(), anyDouble());
    }

    @Test
    public void resolverStatefulOverloadsMatchLegacyResolverAcrossSampleGrid() {
        Fixture fixture = createFixture();
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();

        for (int worldY = -64; worldY <= 320; worldY += 11) {
            IrisDimensionCarvingEntry legacyRoot = legacyResolveRootEntry(fixture.engine, worldY);
            IrisDimensionCarvingEntry statefulRoot = IrisDimensionCarvingResolver.resolveRootEntry(fixture.engine, worldY, state);
            assertSame("root mismatch at worldY=" + worldY, legacyRoot, statefulRoot);

            for (int worldX = -384; worldX <= 384; worldX += 29) {
                for (int worldZ = -384; worldZ <= 384; worldZ += 31) {
                    IrisDimensionCarvingEntry legacyResolved = legacyResolveFromRoot(fixture.engine, legacyRoot, worldX, worldZ);
                    IrisDimensionCarvingEntry statefulResolved = IrisDimensionCarvingResolver.resolveFromRoot(fixture.engine, statefulRoot, worldX, worldZ, state);
                    assertSame("entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, legacyResolved, statefulResolved);
                }
            }
        }
    }

    @Test
    public void resolverStatefulOverloadsMatchLegacyResolverAcrossMixedDepthGraph() {
        Fixture fixture = createMixedDepthFixture();
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();

        for (int worldY = -64; worldY <= 320; worldY += 17) {
            IrisDimensionCarvingEntry legacyRoot = legacyResolveRootEntry(fixture.engine, worldY);
            IrisDimensionCarvingEntry statefulRoot = IrisDimensionCarvingResolver.resolveRootEntry(fixture.engine, worldY, state);
            assertSame("mixed root mismatch at worldY=" + worldY, legacyRoot, statefulRoot);

            for (int worldX = -640; worldX <= 640; worldX += 79) {
                for (int worldZ = -640; worldZ <= 640; worldZ += 83) {
                    IrisDimensionCarvingEntry legacyResolved = legacyResolveFromRoot(fixture.engine, legacyRoot, worldX, worldZ);
                    IrisDimensionCarvingEntry statefulResolved = IrisDimensionCarvingResolver.resolveFromRoot(fixture.engine, statefulRoot, worldX, worldZ, state);
                    assertSame("mixed entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, legacyResolved, statefulResolved);
                }
            }
        }
    }

    @Test
    public void caveBiomeStateOverloadMatchesDefaultOverloadAcrossSampleGrid() {
        Fixture fixture = createFixture();
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();

        for (int y = 1; y <= 300; y += 17) {
            for (int x = -160; x <= 160; x += 23) {
                for (int z = -160; z <= 160; z += 29) {
                    IrisBiome defaultBiome = fixture.engine.getCaveBiome(x, y, z);
                    IrisBiome stateBiome = fixture.engine.getCaveBiome(x, y, z, state);
                    assertSame("cave biome mismatch at x=" + x + " y=" + y + " z=" + z, defaultBiome, stateBiome);
                }
            }
        }
    }

    @Test
    public void columnAnchoredChunkPlanResolutionIsStableAcrossRepeatedBuilds() {
        Fixture fixture = createMixedDepthFixture();
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
        IrisDimensionCarvingEntry root = legacyResolveRootEntry(fixture.engine, 80);

        for (int chunkX = -24; chunkX <= 24; chunkX += 6) {
            for (int chunkZ = -24; chunkZ <= 24; chunkZ += 6) {
                IrisDimensionCarvingEntry[] firstPlan = buildColumnPlan(fixture.engine, root, chunkX, chunkZ, state);
                IrisDimensionCarvingEntry[] secondPlan = buildColumnPlan(fixture.engine, root, chunkX, chunkZ, state);
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
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();

        IrisDimensionCarvingEntry firstExpected = legacyResolveRootEntry(first.engine, 80);
        IrisDimensionCarvingEntry secondExpected = legacyResolveRootEntry(second.engine, 80);

        assertSame(firstExpected, IrisDimensionCarvingResolver.resolveRootEntry(first.engine, 80, state));
        assertSame(secondExpected, IrisDimensionCarvingResolver.resolveRootEntry(second.engine, 80, state));
        assertSame(firstExpected, IrisDimensionCarvingResolver.resolveRootEntry(first.engine, 80, state));
    }

    @Test
    public void threadLocalResolverNeverCarriesDimensionEntriesAcrossEngines() {
        Fixture first = createFixture();
        Fixture second = createMixedDepthFixture();
        int worldY = 83;

        IrisDimensionCarvingEntry firstExpected = legacyResolveRootEntry(first.engine, worldY);
        IrisDimensionCarvingEntry secondExpected = legacyResolveRootEntry(second.engine, worldY);

        assertSame(firstExpected, IrisDimensionCarvingResolver.resolveRootEntry(first.engine, worldY));
        assertSame(secondExpected, IrisDimensionCarvingResolver.resolveRootEntry(second.engine, worldY));
    }

    @Test
    public void threadLocalResolverInvalidatesWhenTheSameEnginePublishesReplacementData() {
        Fixture first = createFixture();
        Fixture replacement = createMixedDepthFixture();
        AtomicReference<IrisDimension> dimension = new AtomicReference<>(first.engine.getDimension());
        AtomicReference<IrisData> data = new AtomicReference<>(first.engine.getData());
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doAnswer((InvocationOnMock invocation) -> dimension.get()).when(engine).getDimension();
        doAnswer((InvocationOnMock invocation) -> data.get()).when(engine).getData();

        int worldY = 83;
        IrisDimensionCarvingEntry firstExpected = legacyResolveRootEntry(first.engine, worldY);
        IrisDimensionCarvingEntry replacementExpected = legacyResolveRootEntry(replacement.engine, worldY);
        assertSame(firstExpected, IrisDimensionCarvingResolver.resolveRootEntry(engine, worldY));

        dimension.set(replacement.engine.getDimension());
        data.set(replacement.engine.getData());
        assertSame(replacementExpected, IrisDimensionCarvingResolver.resolveRootEntry(engine, worldY));
    }

    @Test
    public void resolverStateUsesWeakRuntimeIdentityBindings() throws NoSuchFieldException {
        Field engineIdentity = IrisDimensionCarvingResolver.State.class.getDeclaredField("engineIdentity");
        Field dimensionIdentity = IrisDimensionCarvingResolver.State.class.getDeclaredField("dimensionIdentity");
        Field dataIdentity = IrisDimensionCarvingResolver.State.class.getDeclaredField("dataIdentity");

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
        assertNotNull(IrisDimensionCarvingResolver.resolveRootEntry(fixture.engine(), 80));

        Field threadStateField = IrisDimensionCarvingResolver.class.getDeclaredField("THREAD_STATE");
        threadStateField.setAccessible(true);
        ThreadLocal<?> threadState = (ThreadLocal<?>) threadStateField.get(null);
        Object value = threadState.get();
        assertTrue(value instanceof WeakReference<?>);
        @SuppressWarnings("unchecked")
        WeakReference<IrisDimensionCarvingResolver.State> state =
                (WeakReference<IrisDimensionCarvingResolver.State>) value;
        IrisDimensionCarvingResolver.State populatedState = state.get();
        assertNotNull(populatedState);
        Field biomeCacheField = IrisDimensionCarvingResolver.State.class.getDeclaredField("biomeCache");
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
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
        assertSame(fixture.entry(), IrisDimensionCarvingResolver.resolveRootEntry(
                fixture.engine(), 80, state));

        Field rootEntriesField = IrisDimensionCarvingResolver.State.class
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

        IrisDimensionCarvingEntry entry = buildEntry(
                "retained", "retained", new IrisRange(-64, 320), 0, List.of());
        KList<IrisDimensionCarvingEntry> carvingEntries = new KList<>();
        carvingEntries.add(entry);

        IrisDimension dimension = new IrisDimension();
        dimension.setCarving(carvingEntries);

        Engine engine = (Engine) Proxy.newProxyInstance(
                Engine.class.getClassLoader(),
                new Class<?>[]{Engine.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getDimension" -> dimension;
                    case "getData" -> data;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "carving-retention-test-engine";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        return new RetainedFixture(engine, entry);
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
        doReturn(0).when(fallbackBiome).getCaveMinDepthBelowSurface();

        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        doReturn(rootLowBiome).when(biomeLoader).load("root-low");
        doReturn(rootHighBiome).when(biomeLoader).load("root-high");
        doReturn(childABiome).when(biomeLoader).load("child-a");
        doReturn(childBBiome).when(biomeLoader).load("child-b");
        doReturn(childCBiome).when(biomeLoader).load("child-c");

        IrisData data = mock(IrisData.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();

        IrisDimensionCarvingEntry rootLow = buildEntry("root-low", "root-low", new IrisRange(-64, 120), 4, List.of("child-a", "child-b"));
        IrisDimensionCarvingEntry rootHigh = buildEntry("root-high", "root-high", new IrisRange(121, 320), 3, List.of("child-b", "child-c"));
        IrisDimensionCarvingEntry childA = buildEntry("child-a", "child-a", new IrisRange(-2048, -1024), 3, List.of("child-b"));
        IrisDimensionCarvingEntry childB = buildEntry("child-b", "child-b", new IrisRange(-2048, -1024), 2, List.of("child-c", "child-a"));
        IrisDimensionCarvingEntry childC = buildEntry("child-c", "child-c", new IrisRange(-2048, -1024), 1, List.of());

        KList<IrisDimensionCarvingEntry> carvingEntries = new KList<>();
        carvingEntries.add(rootLow);
        carvingEntries.add(rootHigh);
        carvingEntries.add(childA);
        carvingEntries.add(childB);
        carvingEntries.add(childC);

        Map<String, IrisDimensionCarvingEntry> index = new HashMap<>();
        index.put(rootLow.getId(), rootLow);
        index.put(rootHigh.getId(), rootHigh);
        index.put(childA.getId(), childA);
        index.put(childB.getId(), childB);
        index.put(childC.getId(), childC);

        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(carvingEntries).when(dimension).getCarving();
        doReturn(index).when(dimension).getCarvingEntryIndex();

        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(dimension).when(engine).getDimension();
        doReturn(data).when(engine).getData();
        doReturn(new SeedManager(913_531_771L)).when(engine).getSeedManager();
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        doReturn(surfaceBiome).when(engine).getSurfaceBiome(anyInt(), anyInt());
        doReturn(fallbackBiome).when(engine).getCaveBiome(anyInt(), anyInt());

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
        doReturn(0).when(fallbackBiome).getCaveMinDepthBelowSurface();

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

        IrisDimensionCarvingEntry rootLow = buildEntry("root-low", "root-low", new IrisRange(-64, 120), 7, List.of("child-a", "child-d", "child-e"));
        IrisDimensionCarvingEntry rootHigh = buildEntry("root-high", "root-high", new IrisRange(121, 320), 6, List.of("child-b", "child-c", "child-f"));
        IrisDimensionCarvingEntry childA = buildEntry("child-a", "child-a", new IrisRange(-4096, 4096), 5, List.of("child-b", "child-g"));
        IrisDimensionCarvingEntry childB = buildEntry("child-b", "child-b", new IrisRange(-4096, 4096), 1, List.of("child-c"));
        IrisDimensionCarvingEntry childC = buildEntry("child-c", "child-c", new IrisRange(-4096, 4096), 0, List.of());
        IrisDimensionCarvingEntry childD = buildEntry("child-d", "child-d", new IrisRange(-4096, 4096), 6, List.of("child-e", "child-f"));
        IrisDimensionCarvingEntry childE = buildEntry("child-e", "child-e", new IrisRange(-4096, 4096), 2, List.of("child-a"));
        IrisDimensionCarvingEntry childF = buildEntry("child-f", "child-f", new IrisRange(-4096, 4096), 8, List.of("child-g", "child-c"));
        IrisDimensionCarvingEntry childG = buildEntry("child-g", "child-g", new IrisRange(-4096, 4096), 3, List.of("child-d"));

        KList<IrisDimensionCarvingEntry> carvingEntries = new KList<>();
        carvingEntries.add(rootLow);
        carvingEntries.add(rootHigh);
        carvingEntries.add(childA);
        carvingEntries.add(childB);
        carvingEntries.add(childC);
        carvingEntries.add(childD);
        carvingEntries.add(childE);
        carvingEntries.add(childF);
        carvingEntries.add(childG);

        Map<String, IrisDimensionCarvingEntry> index = new HashMap<>();
        index.put(rootLow.getId(), rootLow);
        index.put(rootHigh.getId(), rootHigh);
        index.put(childA.getId(), childA);
        index.put(childB.getId(), childB);
        index.put(childC.getId(), childC);
        index.put(childD.getId(), childD);
        index.put(childE.getId(), childE);
        index.put(childF.getId(), childF);
        index.put(childG.getId(), childG);

        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(carvingEntries).when(dimension).getCarving();
        doReturn(index).when(dimension).getCarvingEntryIndex();

        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(dimension).when(engine).getDimension();
        doReturn(data).when(engine).getData();
        doReturn(new SeedManager(4_627_991_643L)).when(engine).getSeedManager();
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        doReturn(surfaceBiome).when(engine).getSurfaceBiome(anyInt(), anyInt());
        doReturn(fallbackBiome).when(engine).getCaveBiome(anyInt(), anyInt());

        return new Fixture(engine);
    }

    private IrisDimensionCarvingEntry[] buildColumnPlan(Engine engine, IrisDimensionCarvingEntry rootEntry, int chunkX, int chunkZ, IrisDimensionCarvingResolver.State state) {
        IrisDimensionCarvingEntry[] plan = new IrisDimensionCarvingEntry[256];
        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;
                int columnIndex = (localX << 4) | localZ;
                plan[columnIndex] = IrisDimensionCarvingResolver.resolveFromRoot(engine, rootEntry, worldX, worldZ, state);
            }
        }
        return plan;
    }

    private IrisDimensionCarvingEntry buildEntry(String id, String biome, IrisRange worldRange, int depth, List<String> children) {
        IrisDimensionCarvingEntry entry = new IrisDimensionCarvingEntry();
        entry.setId(id);
        entry.setEnabled(true);
        entry.setBiome(biome);
        entry.setWorldYRange(worldRange);
        entry.setChildRecursionDepth(depth);
        entry.setChildren(new KList<>(children));
        return entry;
    }

    private IrisDimensionCarvingEntry legacyResolveRootEntry(Engine engine, int worldY) {
        IrisDimension dimension = engine.getDimension();
        List<IrisDimensionCarvingEntry> entries = dimension.getCarving();
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        IrisDimensionCarvingEntry resolved = null;
        for (IrisDimensionCarvingEntry entry : entries) {
            if (!legacyIsRootCandidate(engine, entry, worldY)) {
                continue;
            }

            resolved = entry;
        }

        return resolved;
    }

    private IrisDimensionCarvingEntry legacyResolveFromRoot(Engine engine, IrisDimensionCarvingEntry rootEntry, int worldX, int worldZ) {
        if (rootEntry == null) {
            return null;
        }

        IrisBiome rootBiome = legacyResolveEntryBiome(engine, rootEntry);
        if (rootBiome == null) {
            return null;
        }

        int remainingDepth = clampDepth(rootEntry.getChildRecursionDepth());
        if (remainingDepth <= 0) {
            return rootEntry;
        }

        Map<String, IrisDimensionCarvingEntry> entryIndex = engine.getDimension().getCarvingEntryIndex();
        IrisDimensionCarvingEntry current = rootEntry;
        int depth = remainingDepth;
        while (depth > 0) {
            IrisDimensionCarvingEntry selected = legacySelectChild(engine, current, worldX, worldZ, entryIndex);
            if (selected == null || selected == current) {
                break;
            }

            depth--;
            int childDepthLimit = clampDepth(selected.getChildRecursionDepth());
            if (childDepthLimit < depth) {
                depth = childDepthLimit;
            }
            current = selected;
        }

        return current;
    }

    private IrisBiome legacyResolveEntryBiome(Engine engine, IrisDimensionCarvingEntry entry) {
        if (entry == null) {
            return null;
        }

        return entry.getRealBiome(engine.getData());
    }

    private boolean legacyIsRootCandidate(Engine engine, IrisDimensionCarvingEntry entry, int worldY) {
        if (entry == null || !entry.isEnabled()) {
            return false;
        }

        IrisRange worldYRange = entry.getWorldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return legacyResolveEntryBiome(engine, entry) != null;
    }

    private IrisDimensionCarvingEntry legacySelectChild(
            Engine engine,
            IrisDimensionCarvingEntry parent,
            int worldX,
            int worldZ,
            Map<String, IrisDimensionCarvingEntry> entryIndex
    ) {
        KList<String> children = parent.getChildren();
        if (children == null || children.isEmpty()) {
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

            IrisDimensionCarvingEntry child = entryIndex.get(childId.trim());
            if (child == null || !child.isEnabled()) {
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
        CNG childGenerator = parent.getChildrenGenerator(seed, engine.getData());
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

    private record RetainedFixture(Engine engine, IrisDimensionCarvingEntry entry) {
    }

    private record LifetimeReferences(
            WeakReference<IrisDimensionCarvingResolver.State> state,
            WeakReference<Engine> engine
    ) {
    }

    private static final class LegacyCarvingChoice implements IRare {
        private final IrisDimensionCarvingEntry entry;
        private final int rarity;

        private LegacyCarvingChoice(IrisDimensionCarvingEntry entry, int rarity) {
            this.entry = entry;
            this.rarity = rarity;
        }

        @Override
        public int getRarity() {
            return rarity;
        }
    }
}
