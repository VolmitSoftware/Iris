package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.modules.carve.CarvingEntry;
import art.arcane.iris.modules.carve.CarvingSettings;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.volmit.config.Range;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The module's pick of a cave biome band is the pick the band code made with Iris's own generators: the root by world Y, then the
 * child a weighted walk along the entry's children list selects from the generator's noise. The reference below is that walk,
 * written out again.
 */
public class CarvingResolverParityTest {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;
    private static final long CHILD_GENERATOR_SALT = 2137L;
    private static final int CHILD_GENERATOR_SIGNATURE = 2137;
    private static final double MIN_CHILD_SCALE = 0.0001D;

    @Test
    public void resolverMatchesReferenceResolverAcrossSampleGrid() {
        Fixture fixture = createFixture();

        for (int worldY = -64; worldY <= 320; worldY += 11) {
            ResolvedCarvingEntry referenceRoot = fixture.referenceRootEntry(worldY);
            ResolvedCarvingEntry root = fixture.resolver.rootEntryAtWorldY(worldY);
            assertSame("root mismatch at worldY=" + worldY, referenceRoot, root);

            for (int worldX = -384; worldX <= 384; worldX += 29) {
                for (int worldZ = -384; worldZ <= 384; worldZ += 31) {
                    ResolvedCarvingEntry referenceResolved = fixture.referenceResolveFromRoot(referenceRoot, worldX, worldZ);
                    ResolvedCarvingEntry resolved = fixture.resolver.resolveFromRoot(root, worldX, worldZ);
                    assertSame("entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, referenceResolved, resolved);
                }
            }
        }
    }

    @Test
    public void resolverMatchesReferenceResolverAcrossMixedDepthGraph() {
        Fixture fixture = createMixedDepthFixture();

        for (int worldY = -64; worldY <= 320; worldY += 17) {
            ResolvedCarvingEntry referenceRoot = fixture.referenceRootEntry(worldY);
            ResolvedCarvingEntry root = fixture.resolver.rootEntryAtWorldY(worldY);
            assertSame("mixed root mismatch at worldY=" + worldY, referenceRoot, root);

            for (int worldX = -640; worldX <= 640; worldX += 79) {
                for (int worldZ = -640; worldZ <= 640; worldZ += 83) {
                    ResolvedCarvingEntry referenceResolved = fixture.referenceResolveFromRoot(referenceRoot, worldX, worldZ);
                    ResolvedCarvingEntry resolved = fixture.resolver.resolveFromRoot(root, worldX, worldZ);
                    assertSame("mixed entry mismatch at worldY=" + worldY + " worldX=" + worldX + " worldZ=" + worldZ, referenceResolved, resolved);
                }
            }
        }
    }

    @Test
    public void aParentLeftOutOfItsChildrenNeverKeepsTheSpot() {
        CarveTestWorld world = new CarveTestWorld().carveSeed(77_031L).biome("parent").biome("only-child");
        Fixture fixture = new Fixture(world, List.of(
                buildEntry("parent", "parent", Range.of(-64, 320), 1, List.of(child("only-child", 1))),
                buildEntry("only-child", "only-child", Range.of(-4096, -2048), 1, List.of())));
        ResolvedCarvingEntry root = fixture.resolver.rootEntryAtWorldY(80);

        for (int worldX = -256; worldX <= 256; worldX += 37) {
            for (int worldZ = -256; worldZ <= 256; worldZ += 41) {
                assertSame(fixture.resolver.index().get("only-child"), fixture.resolver.resolveFromRoot(root, worldX, worldZ));
            }
        }
    }

    @Test
    public void aParentListedAmongItsChildrenKeepsTheSpotWhereItsEntryReaches() {
        CarveTestWorld world = new CarveTestWorld().carveSeed(77_031L).biome("parent").biome("child");
        Fixture fixture = new Fixture(world, List.of(
                buildEntry("parent", "parent", Range.of(-64, 320), 1, List.of(child("child", 1), child("parent", 1))),
                buildEntry("child", "child", Range.of(-4096, -2048), 1, List.of())));
        ResolvedCarvingEntry root = fixture.resolver.rootEntryAtWorldY(80);

        List<ResolvedCarvingEntry> resolved = new ArrayList<>();
        for (int worldX = -512; worldX <= 512; worldX += 37) {
            for (int worldZ = -512; worldZ <= 512; worldZ += 41) {
                resolved.add(fixture.resolver.resolveFromRoot(root, worldX, worldZ));
            }
        }

        assertTrue("some spots stay the parent", resolved.contains(root));
        assertTrue("some spots become the child", resolved.contains(fixture.resolver.index().get("child")));
    }

    @Test
    public void columnAnchoredChunkPlanResolutionIsStableAcrossRepeatedBuilds() {
        Fixture fixture = createMixedDepthFixture();
        ResolvedCarvingEntry root = fixture.referenceRootEntry(80);

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

        ResolvedCarvingEntry firstExpected = first.referenceRootEntry(80);
        ResolvedCarvingEntry secondExpected = second.referenceRootEntry(80);

        assertSame(firstExpected, first.resolver.rootEntryAtWorldY(80));
        assertSame(secondExpected, second.resolver.rootEntryAtWorldY(80));
        assertSame(firstExpected, first.resolver.rootEntryAtWorldY(80));
        assertNotSame(firstExpected, secondExpected);
    }

    @Test
    public void aWorldReadsItsBandsFromTheDimensionsSettings() {
        CarveTestWorld world = new CarveTestWorld().biome("root-low")
                .dimension(new CarvingSettings(List.of(buildEntry("root-low", "root-low", Range.of(-64, 120), 4, List.of()))));

        CarvingResolver resolver = CarvingResolver.of(world);

        assertSame(resolver, CarvingResolver.of(world));
        assertSame(resolver.index().get("root-low"), resolver.rootEntryAt(80 - CarveTestWorld.MIN_HEIGHT));
        assertNull(resolver.rootEntryAtWorldY(121));
    }

    @Test
    public void childrenKeepTheWeightsTheyAreWrittenWith() {
        CarvingEntry entry = buildEntry("parent", "parent", Range.of(-64, 320), 1, List.of(child("a", 3), child("parent", 1)));

        assertEquals(2, entry.children().size());
        assertEquals("a", entry.children().get(0).id());
        assertEquals(3, entry.children().get(0).weight());
        assertEquals(1, entry.children().get(1).weight());
    }

    private Fixture createFixture() {
        CarveTestWorld world = new CarveTestWorld().carveSeed(913_531_771L)
                .biome("root-low").biome("root-high").biome("child-a").biome("child-b").biome("child-c");
        return new Fixture(world, List.of(
                buildEntry("root-low", "root-low", Range.of(-64, 120), 4, List.of(child("child-a", 2), child("child-b", 3), child("root-low", 1))),
                buildEntry("root-high", "root-high", Range.of(121, 320), 3, List.of(child("child-b", 1), child("child-c", 2), child("root-high", 4))),
                buildEntry("child-a", "child-a", Range.of(-2048, -1024), 3, List.of(child("child-b", 1), child("child-a", 2))),
                buildEntry("child-b", "child-b", Range.of(-2048, -1024), 2, List.of(child("child-c", 1), child("child-a", 1), child("child-b", 3))),
                buildEntry("child-c", "child-c", Range.of(-2048, -1024), 1, List.of())));
    }

    private Fixture createMixedDepthFixture() {
        CarveTestWorld world = new CarveTestWorld().carveSeed(4_627_991_643L)
                .biome("root-low").biome("root-high").biome("child-a").biome("child-b").biome("child-c")
                .biome("child-d").biome("child-e").biome("child-f").biome("child-g");
        return new Fixture(world, List.of(
                buildEntry("root-low", "root-low", Range.of(-64, 120), 7, List.of(child("child-a", 2), child("child-d", 5), child("child-e", 1), child("root-low", 3))),
                buildEntry("root-high", "root-high", Range.of(121, 320), 6, List.of(child("child-b", 1), child("child-c", 1), child("child-f", 4), child("root-high", 2))),
                buildEntry("child-a", "child-a", Range.of(-4096, 4096), 5, List.of(child("child-b", 3), child("child-g", 2), child("child-a", 1))),
                buildEntry("child-b", "child-b", Range.of(-4096, 4096), 1, List.of(child("child-c", 1), child("child-b", 1))),
                buildEntry("child-c", "child-c", Range.of(-4096, 4096), 0, List.of()),
                buildEntry("child-d", "child-d", Range.of(-4096, 4096), 6, List.of(child("child-e", 2), child("child-f", 1))),
                buildEntry("child-e", "child-e", Range.of(-4096, 4096), 2, List.of(child("child-a", 1), child("child-e", 5))),
                buildEntry("child-f", "child-f", Range.of(-4096, 4096), 8, List.of(child("child-g", 1), child("child-c", 1), child("child-f", 2))),
                buildEntry("child-g", "child-g", Range.of(-4096, 4096), 3, List.of(child("child-d", 1)))));
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

    private static JsonObject child(String id, int weight) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("weight", weight);
        return json;
    }

    private static CarvingEntry buildEntry(String id, String biome, Range worldRange, int depth, List<JsonObject> children) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("biome", biome);
        JsonObject range = new JsonObject();
        range.addProperty("min", worldRange.min());
        range.addProperty("max", worldRange.max());
        json.add("worldYRange", range);
        json.addProperty("childRecursionDepth", depth);
        JsonArray listed = new JsonArray();
        children.forEach(listed::add);
        json.add("children", listed);
        return new Gson().fromJson(json, CarvingEntry.class);
    }

    private static int clampDepth(int depth) {
        if (depth <= 0) {
            return 0;
        }
        return Math.min(depth, MAX_CHILD_DEPTH);
    }

    /** The module's resolver, and the band code as a plain walk, over the same entries and world. */
    private static final class Fixture {
        private final CarveTestWorld world;
        private final CarvingResolver resolver;
        private final Map<String, CarvingEntry> settingsById = new HashMap<>();

        private Fixture(CarveTestWorld world, List<CarvingEntry> entries) {
            this.world = world;
            this.resolver = new CarvingResolver(world, entries);
            entries.forEach(entry -> settingsById.put(entry.id(), entry));
        }

        private ResolvedCarvingEntry referenceRootEntry(int worldY) {
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

        private ResolvedCarvingEntry referenceResolveFromRoot(ResolvedCarvingEntry rootEntry, int worldX, int worldZ) {
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
                ResolvedCarvingEntry selected = referenceSelectChild(current, worldX, worldZ);
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

        private ResolvedCarvingEntry referenceSelectChild(ResolvedCarvingEntry parent, int worldX, int worldZ) {
            List<ResolvedCarvingEntry> entries = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            int totalWeight = 0;
            for (var listed : parent.children()) {
                ResolvedCarvingEntry entry = resolver.index().get(listed.id().trim());
                if (entry == null || !entry.enabled() || resolver.entryBiome(entry) == null) {
                    continue;
                }

                entries.add(entry);
                weights.add(listed.weight());
                totalWeight += listed.weight();
            }
            if (entries.isEmpty()) {
                return parent;
            }

            long seed = world.seed(IrisSeedPurposes.CARVE) ^ CHILD_SEED_SALT;
            double position = referenceChildrenGenerator(parent, seed).noiseFast2D(worldX, worldZ) * totalWeight;
            int weightUpToHere = 0;
            for (int index = 0; index < entries.size(); index++) {
                weightUpToHere += weights.get(index);
                if (position < weightUpToHere) {
                    return entries.get(index);
                }
            }
            return entries.getLast();
        }

        /** The generator of an entry's children as Iris built it: its style created from the entry's seed, then scaled by the shrink factor. */
        private CNG referenceChildrenGenerator(ResolvedCarvingEntry parent, long seed) {
            CarvingEntry settings = settingsById.get(parent.id());
            long idHash = settings.id().trim().hashCode();
            long generatorSeed = seed ^ (idHash << 32) ^ CHILD_GENERATOR_SALT;
            double scale = Math.max(MIN_CHILD_SCALE, settings.childShrinkFactor());
            RNG random = new RNG(generatorSeed);
            return IrisValues.generatorStyle(settings.childStyle(), new IrisPackResources(world.data())).createNoCache(random.nextParallelRNG(CHILD_GENERATOR_SIGNATURE), world.data())
                    .bake().scale(scale).bake();
        }
    }
}
