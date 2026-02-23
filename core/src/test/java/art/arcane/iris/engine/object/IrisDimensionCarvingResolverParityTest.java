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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisDimensionCarvingResolverParityTest {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;

    @BeforeClass
    public static void setupBukkit() {
        if (Bukkit.getServer() != null) {
            return;
        }

        Server server = mock(Server.class);
        BlockData emptyBlockData = mock(BlockData.class);
        doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
        doReturn("IrisTestServer").when(server).getName();
        doReturn("1.0").when(server).getVersion();
        doReturn("1.0").when(server).getBukkitVersion();
        doReturn(emptyBlockData).when(server).createBlockData(any(Material.class));
        doReturn(emptyBlockData).when(server).createBlockData(anyString());
        Bukkit.setServer(server);
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
