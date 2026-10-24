package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.modules.carve.CarvingSettings;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.volmit.config.Range;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotSame;

/**
 * The module's pick of a cave biome band is the pick the band code made with Iris's own generators: the root by world Y, then the
 * child a rarity-weighted draw of the generator's noise selects. The reference below is that code, written out again.
 */
public class CarvingResolverParityTest {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;
    private static final long CHILD_GENERATOR_SALT = 2137L;
    private static final int CHILD_GENERATOR_SIGNATURE = 2137;
    private static final double MIN_CHILD_SCALE = 0.0001D;

    @Test
    public void resolverMatchesLegacyResolverAcrossSampleGrid() {
        Fixture fixture = createFixture();

        for (int worldY = -64; worldY <= 320; worldY += 11) {
            ResolvedCarvingEntry legacyRoot = fixture.legacyRootEntry(worldY);
            ResolvedCarvingEntry root = fixture.resolver.rootEntryAtWorldY(worldY);
            assertSame("root mismatch at worldY=" + worldY, legacyRoot, root);

            for (int worldX = -384; worldX <= 384; worldX += 29) {
                for (int worldZ = -384; worldZ <= 384; worldZ += 31) {
                    ResolvedCarvingEntry legacyResolved = fixture.legacyResolveFromRoot(legacyRoot, worldX, worldZ);
                    ResolvedCarvingEntry resolved = fixture.resolver.resolveFromRoot(root, worldX, worldZ);
                    assertSame("entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, legacyResolved, resolved);
                }
            }
        }
    }

    @Test
    public void resolverMatchesLegacyResolverAcrossMixedDepthGraph() {
        Fixture fixture = createMixedDepthFixture();

        for (int worldY = -64; worldY <= 320; worldY += 17) {
            ResolvedCarvingEntry legacyRoot = fixture.legacyRootEntry(worldY);
            ResolvedCarvingEntry root = fixture.resolver.rootEntryAtWorldY(worldY);
            assertSame("mixed root mismatch at worldY=" + worldY, legacyRoot, root);

            for (int worldX = -640; worldX <= 640; worldX += 79) {
                for (int worldZ = -640; worldZ <= 640; worldZ += 83) {
                    ResolvedCarvingEntry legacyResolved = fixture.legacyResolveFromRoot(legacyRoot, worldX, worldZ);
                    ResolvedCarvingEntry resolved = fixture.resolver.resolveFromRoot(root, worldX, worldZ);
                    assertSame("mixed entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, legacyResolved, resolved);
                }
            }
        }
    }

    @Test
    public void columnAnchoredChunkPlanResolutionIsStableAcrossRepeatedBuilds() {
        Fixture fixture = createMixedDepthFixture();
        ResolvedCarvingEntry root = fixture.legacyRootEntry(80);

        for (int chunkX = -24; chunkX <= 24; chunkX += 6) {
            for (int chunkZ = -24; chunkZ <= 24; chunkZ += 6) {
                ResolvedCarvingEntry[] firstPlan = buildColumnPlan(fixture.resolver, root, chunkX, chunkZ);
                ResolvedCarvingEntry[] secondPlan = buildColumnPlan(fixture.resolver, root, chunkX, chunkZ);
                for (int columnIndex = 0; columnIndex < firstPlan.length; columnIndex++) {
                    assertSame("column plan mismatch at chunkX=" + chunkX + " chunkZ=" + chunkZ + " columnIndex=" + columnIndex,
                            firstPlan[columnIndex], secondPlan[columnIndex]);
                }
            }
        }
    }

    @Test
    public void eachWorldKeepsItsOwnBands() {
        Fixture first = createFixture();
        Fixture second = createMixedDepthFixture();

        ResolvedCarvingEntry firstExpected = first.legacyRootEntry(80);
        ResolvedCarvingEntry secondExpected = second.legacyRootEntry(80);

        assertSame(firstExpected, first.resolver.rootEntryAtWorldY(80));
        assertSame(secondExpected, second.resolver.rootEntryAtWorldY(80));
        assertSame(firstExpected, first.resolver.rootEntryAtWorldY(80));
        assertNotSame(firstExpected, secondExpected);
    }

    @Test
    public void aWorldReadsItsBandsFromTheDimensionsSettings() {
        CarveTestWorld world = new CarveTestWorld().biome("root-low", 6)
                .dimension(new CarvingSettings(List.of(buildEntry("root-low", "root-low", Range.of(-64, 120), 4, List.of()))));

        CarvingResolver resolver = CarvingResolver.of(world);

        assertSame(resolver, CarvingResolver.of(world));
        assertSame(resolver.index().get("root-low"), resolver.rootEntryAt(80 - CarveTestWorld.MIN_HEIGHT));
        assertNull(resolver.rootEntryAtWorldY(121));
    }

    private Fixture createFixture() {
        CarveTestWorld world = new CarveTestWorld().carveSeed(913_531_771L)
                .biome("root-low", 6).biome("root-high", 4).biome("child-a", 2).biome("child-b", 5).biome("child-c", 1);
        return new Fixture(world, List.of(
                buildEntry("root-low", "root-low", Range.of(-64, 120), 4, List.of("child-a", "child-b")),
                buildEntry("root-high", "root-high", Range.of(121, 320), 3, List.of("child-b", "child-c")),
                buildEntry("child-a", "child-a", Range.of(-2048, -1024), 3, List.of("child-b")),
                buildEntry("child-b", "child-b", Range.of(-2048, -1024), 2, List.of("child-c", "child-a")),
                buildEntry("child-c", "child-c", Range.of(-2048, -1024), 1, List.of())));
    }

    private Fixture createMixedDepthFixture() {
        CarveTestWorld world = new CarveTestWorld().carveSeed(4_627_991_643L)
                .biome("root-low", 7).biome("root-high", 5).biome("child-a", 2).biome("child-b", 3).biome("child-c", 6)
                .biome("child-d", 1).biome("child-e", 4).biome("child-f", 8).biome("child-g", 2);
        return new Fixture(world, List.of(
                buildEntry("root-low", "root-low", Range.of(-64, 120), 7, List.of("child-a", "child-d", "child-e")),
                buildEntry("root-high", "root-high", Range.of(121, 320), 6, List.of("child-b", "child-c", "child-f")),
                buildEntry("child-a", "child-a", Range.of(-4096, 4096), 5, List.of("child-b", "child-g")),
                buildEntry("child-b", "child-b", Range.of(-4096, 4096), 1, List.of("child-c")),
                buildEntry("child-c", "child-c", Range.of(-4096, 4096), 0, List.of()),
                buildEntry("child-d", "child-d", Range.of(-4096, 4096), 6, List.of("child-e", "child-f")),
                buildEntry("child-e", "child-e", Range.of(-4096, 4096), 2, List.of("child-a")),
                buildEntry("child-f", "child-f", Range.of(-4096, 4096), 8, List.of("child-g", "child-c")),
                buildEntry("child-g", "child-g", Range.of(-4096, 4096), 3, List.of("child-d"))));
    }

    private ResolvedCarvingEntry[] buildColumnPlan(CarvingResolver resolver, ResolvedCarvingEntry rootEntry, int chunkX, int chunkZ) {
        ResolvedCarvingEntry[] plan = new ResolvedCarvingEntry[256];
        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;
                plan[(localX << 4) | localZ] = resolver.resolveFromRoot(rootEntry, worldX, worldZ);
            }
        }
        return plan;
    }

    private static CarvingEntry buildEntry(String id, String biome, Range worldRange, int depth, List<String> children) {
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

    private static int clampDepth(int depth) {
        if (depth <= 0) {
            return 0;
        }
        return Math.min(depth, MAX_CHILD_DEPTH);
    }

    /** The module's resolver, and the band code as it was, over the same entries and world. */
    private static final class Fixture {
        private final CarveTestWorld world;
        private final CarvingResolver resolver;
        private final Map<String, CarvingEntry> settingsById = new HashMap<>();

        private Fixture(CarveTestWorld world, List<CarvingEntry> entries) {
            this.world = world;
            this.resolver = new CarvingResolver(world, entries);
            entries.forEach(entry -> settingsById.put(entry.id(), entry));
        }

        private ResolvedCarvingEntry legacyRootEntry(int worldY) {
            ResolvedCarvingEntry resolved = null;
            for (ResolvedCarvingEntry entry : resolver.entries()) {
                if (!entry.enabled()) {
                    continue;
                }
                Range worldYRange = entry.worldYRange();
                if (worldYRange != null && !worldYRange.contains(worldY)) {
                    continue;
                }
                if (resolver.entryBiome(entry) == null) {
                    continue;
                }
                resolved = entry;
            }
            return resolved;
        }

        private ResolvedCarvingEntry legacyResolveFromRoot(ResolvedCarvingEntry rootEntry, int worldX, int worldZ) {
            if (rootEntry == null || resolver.entryBiome(rootEntry) == null) {
                return null;
            }

            int remainingDepth = clampDepth(rootEntry.childRecursionDepth());
            if (remainingDepth <= 0) {
                return rootEntry;
            }

            ResolvedCarvingEntry current = rootEntry;
            int depth = remainingDepth;
            while (depth > 0) {
                ResolvedCarvingEntry selected = legacySelectChild(current, worldX, worldZ);
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

        private ResolvedCarvingEntry legacySelectChild(ResolvedCarvingEntry parent, int worldX, int worldZ) {
            List<String> children = parent.children();
            if (children.isEmpty()) {
                return parent;
            }
            if (resolver.entryBiome(parent) == null) {
                return parent;
            }

            KList<LegacyCarvingChoice> options = new KList<>();
            for (String childId : children) {
                if (childId == null || childId.isBlank()) {
                    continue;
                }

                ResolvedCarvingEntry child = resolver.index().get(childId.trim());
                if (child == null || !child.enabled() || resolver.entryBiome(child) == null) {
                    continue;
                }

                options.add(new LegacyCarvingChoice(child, rarity(child)));
            }

            options.add(new LegacyCarvingChoice(parent, rarity(parent)));
            if (options.size() <= 1) {
                return parent;
            }

            long seed = world.seed(IrisSeedPurposes.CARVE) ^ CHILD_SEED_SALT;
            LegacyCarvingChoice selected = legacyChildrenGenerator(parent, seed).fitRarity(options, worldX, worldZ);
            if (selected == null || selected.entry == null) {
                return parent;
            }

            return selected.entry;
        }

        private int rarity(ResolvedCarvingEntry entry) {
            return Math.max(resolver.entryBiome(entry).rarity(), 1);
        }

        /** The generator of an entry's children as Iris built it: its style created from the entry's seed, then scaled by the shrink factor. */
        private CNG legacyChildrenGenerator(ResolvedCarvingEntry parent, long seed) {
            CarvingEntry settings = settingsById.get(parent.id());
            long idHash = settings.id().trim().hashCode();
            long generatorSeed = seed ^ (idHash << 32) ^ CHILD_GENERATOR_SALT;
            double scale = Math.max(MIN_CHILD_SCALE, settings.childShrinkFactor());
            RNG random = new RNG(generatorSeed);
            return IrisValues.generatorStyle(settings.childStyle(), new IrisPackResources(world.data())).createNoCache(random.nextParallelRNG(CHILD_GENERATOR_SIGNATURE), world.data())
                    .bake().scale(scale).bake();
        }
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
