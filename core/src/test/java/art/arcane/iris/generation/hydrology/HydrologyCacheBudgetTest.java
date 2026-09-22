package art.arcane.iris.generation.hydrology;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCacheBudgetTest {
    private static final long MEBIBYTE = 1024L * 1024L;

    @Test
    public void retainedCacheAllowancesShareOneHeapScaledLimit() {
        for (long heap : new long[]{32L * MEBIBYTE, 1024L * MEBIBYTE, 2048L * MEBIBYTE, 4096L * MEBIBYTE, Long.MAX_VALUE}) {
            for (boolean regionalEnabled : new boolean[]{false, true}) {
                HydrologyCacheBudget budget = HydrologyCacheBudget.forHeap(heap, regionalEnabled);
                long total = budget.tileBytes() + budget.columnBytes() + budget.diagnosticBytes()
                        + budget.routingBytes() + budget.ownerBytes() + budget.edgeBytes() + budget.regionalDraftBytes()
                        + budget.regionalCoarseBytes() + budget.regionalTerrainBytes();
                assertTrue(total <= Math.clamp(heap / 8L, 8L * MEBIBYTE, 256L * MEBIBYTE));
                assertTrue(total >= 8L * MEBIBYTE);
                assertEquals(budget.tileBytes() / 6L, budget.columnBytes());
            }
        }
        HydrologyCacheBudget twoGigabytes = HydrologyCacheBudget.forHeap(2048L * MEBIBYTE, true);
        assertEquals(96L * MEBIBYTE, twoGigabytes.tileBytes());
        assertEquals(32L * MEBIBYTE, twoGigabytes.ownerBytes());
        assertEquals(32L * MEBIBYTE, twoGigabytes.regionalTerrainBytes());
        HydrologyCacheBudget localOnly = HydrologyCacheBudget.forHeap(2048L * MEBIBYTE, false);
        assertEquals(twoGigabytes.tileBytes(), localOnly.tileBytes());
        assertEquals(96L * MEBIBYTE, localOnly.ownerBytes());
        assertEquals(24L * MEBIBYTE, localOnly.routingBytes());
        assertEquals(8L * MEBIBYTE, localOnly.edgeBytes());
        assertEquals(0L, localOnly.regionalDraftBytes() + localOnly.regionalCoarseBytes()
                + localOnly.regionalTerrainBytes());
        assertThrows(IllegalArgumentException.class, () -> HydrologyCacheBudget.forHeap(0L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void disabledRegionalCachesLeaveTheirBudgetForLocalPlanning() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyPlannerSettings.defaults(),
                (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land"));
        HydrologyCacheBudget budget = HydrologyCacheBudget.runtime(false);
        assertEquals(budget.ownerBytes(), planner.resolvedOwners.policy().eviction().orElseThrow().getMaximum());
        assertEquals(budget.routingBytes(), planner.routingContexts.policy().eviction().orElseThrow().getMaximum());
        assertEquals(budget.edgeBytes(), planner.refinedEdgeCache.policy().eviction().orElseThrow().getMaximum());
        Field field = HydrologyRegionalPlanner.class.getDeclaredField("drafts");
        field.setAccessible(true);
        Cache<HydrologyTileKey, HydrologyRegionalNetwork> drafts =
                (Cache<HydrologyTileKey, HydrologyRegionalNetwork>) field.get(planner.regional);
        assertEquals(0L, drafts.policy().eviction().orElseThrow().getMaximum());
        drafts.put(new HydrologyTileKey(0, 0), HydrologyRegionalNetwork.EMPTY);
        drafts.cleanUp();
        assertEquals(0L, drafts.estimatedSize());
    }

    @Test
    public void weightsKeepEntryAndStationLimitsAndSaturateLargeValues() {
        assertEquals(64, HydrologyCacheWeights.bounded(1L, 1024L, 16));
        assertEquals(256, HydrologyCacheWeights.bounded(256L, 1024L, 16));
        assertEquals(512, HydrologyCacheWeights.bounded(1L, 1024L, 16, 50, 100));
        assertEquals(Integer.MAX_VALUE, HydrologyCacheWeights.bounded(Long.MAX_VALUE, 1024L, 16));
        Cache<Integer, Integer> cache = Caffeine.newBuilder().maximumWeight(1024L)
                .weigher((Integer key, Integer bytes) -> HydrologyCacheWeights.bounded(bytes, 1024L, 16)).build();
        for (int key = 0; key < 64; key++) {
            cache.put(key, 1);
        }
        cache.cleanUp();
        assertTrue(cache.estimatedSize() <= 16L);
        cache.put(64, 2048);
        cache.cleanUp();
        assertNull(cache.getIfPresent(64));
        assertTrue(cache.policy().eviction().orElseThrow().weightedSize().orElseThrow() <= 1024L);
    }

    @Test
    public void largeRoutingContextsEvictByBytesBeforeTheEntryLimit() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), (x, z) -> terrain);
        long budget = HydrologyCacheBudget.runtime().routingBytes();
        assertTrue(planner.routingContexts.policy().eviction().orElseThrow().isWeighted());
        assertEquals(budget, planner.routingContexts.policy().eviction().orElseThrow().getMaximum());
        HydrologyGridNode node = new HydrologyGridNode(0, 0, 0, 0, 0, 1L, terrain);
        int nodeCount = (int) (budget / 640L);
        SourceRoutingContext context = new SourceRoutingContext(
                new HydrologySampledGrid(0, 0, 0, 0, 16, 1, 16, Collections.nCopies(nodeCount, node)),
                null, null, List.of());
        for (int index = 0; index < 8; index++) {
            planner.routingContexts.put(new HydrologyTileKey(index, 0), context);
        }
        planner.routingContexts.cleanUp();
        assertTrue(planner.routingContexts.estimatedSize() < 8L);
        assertTrue(planner.routingContexts.policy().eviction().orElseThrow().weightedSize().orElseThrow() <= budget);
        assertNotNull(planner.routingContexts.asMap().values().iterator().next());
    }

    @Test
    public void regionalTreeWeightCountsSharedDownstreamLabelsOnce() {
        HydrologyRegionalGraph.Label downstream = new HydrologyRegionalGraph.Label(
                new HydrologyRegionalGraph.LabelState(0, 0, 63, 0D, 0D, 0), null);
        HydrologyRegionalGraph.Label first = new HydrologyRegionalGraph.Label(
                new HydrologyRegionalGraph.LabelState(1, 1, 63, 1D, 1D, 0), downstream);
        HydrologyRegionalGraph.Label second = new HydrologyRegionalGraph.Label(
                new HydrologyRegionalGraph.LabelState(3, 2, 63, 1D, 1D, 0), downstream);
        HydrologyRegionalGraph.Tree tree = new HydrologyRegionalGraph.Tree(
                new HydrologyRegionalGraph.Label[]{first, second, null}, new int[4], List.of(), new int[4]);
        assertEquals(3, tree.retainedLabelCount());
    }
}
