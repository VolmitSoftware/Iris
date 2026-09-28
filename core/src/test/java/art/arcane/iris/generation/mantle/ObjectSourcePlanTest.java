package art.arcane.iris.generation.mantle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectSourcePlanTest {
    @Test
    public void destinationLookupRetainsImmutableIndexedOrder() {
        List<ObjectDestinationTransaction.Mutation> mutations = new ArrayList<>();
        int[][] positions = {{-1, -1}, {0, 0}, {-16, -16}, {-17, -1},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, Integer.MIN_VALUE}};
        for (int pass = 0; pass < 3; pass++) {
            for (int[] position : positions) {
                mutations.add(mutationAt(position[0], position[1]));
            }
        }
        ObjectSourcePlan plan = new ObjectSourcePlan(mutations);
        List<ObjectDestinationTransaction.Mutation> expected = List.of(
                mutations.get(0), mutations.get(2), mutations.get(6), mutations.get(8), mutations.get(12), mutations.get(14));
        mutations.clear();

        List<ObjectDestinationTransaction.Mutation> local = plan.mutationsFor(-1, -1);
        assertEquals(expected, local);
        assertSame(local, plan.mutationsFor(-1, -1));
        assertEquals(3, plan.mutationsFor(Integer.MIN_VALUE >> 4, Integer.MAX_VALUE >> 4).size());
        assertEquals(3, plan.mutationsFor(Integer.MAX_VALUE >> 4, Integer.MIN_VALUE >> 4).size());
        assertTrue(plan.mutationsFor(1, -1).isEmpty());
        assertTrue(plan.mutationsFor(-1, 1).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> local.clear());
    }

    @Test
    public void cacheWeightIncludesEachDestinationIndex() {
        ObjectDestinationTransaction.Mutation first = mutationAt(0, 0);
        ObjectDestinationTransaction.Mutation nearby = mutationAt(1, 0);
        ObjectDestinationTransaction.Mutation remote = mutationAt(16, 0);
        ObjectSourcePlan compact = new ObjectSourcePlan(List.of(first, nearby));
        ObjectSourcePlan scattered = new ObjectSourcePlan(List.of(first, remote));

        assertTrue(compact.estimatedRetainedBytes() > new ObjectSourcePlan(List.of()).estimatedRetainedBytes());
        assertTrue(scattered.estimatedRetainedBytes() > compact.estimatedRetainedBytes());
        assertThrows(NullPointerException.class, () -> new ObjectSourcePlan(Arrays.asList(first, null)));
    }

    @Test
    public void retainedBytesIncludeVariableLengthMarkers() {
        ObjectDestinationTransaction.DataKey key = new ObjectDestinationTransaction.DataKey(0, 4, 0, String.class);
        ObjectSourcePlan shortMarker = new ObjectSourcePlan(List.of(new ObjectDestinationTransaction.SetMutation(key, "a")));
        ObjectSourcePlan longMarker = new ObjectSourcePlan(List.of(new ObjectDestinationTransaction.SetMutation(key, "a".repeat(4096))));
        assertTrue(longMarker.estimatedRetainedBytes() - shortMarker.estimatedRetainedBytes() >= 8190);
    }

    @Test
    public void continuationMetadataAddsRetainedBytesWithoutCountingPayloadsTwice() {
        List<ObjectDestinationTransaction.Mutation> shortMarkers = List.of(mutationAt(0, 0), mutationAt(16, 0));
        List<ObjectDestinationTransaction.Mutation> longMarkers = List.of(
                new ObjectDestinationTransaction.SetMutation(
                        new ObjectDestinationTransaction.DataKey(0, 4, 0, String.class), "a".repeat(4096)),
                new ObjectDestinationTransaction.SetMutation(
                        new ObjectDestinationTransaction.DataKey(16, 4, 0, String.class), "b".repeat(4096)));
        ObjectSourcePlan shortPlain = new ObjectSourcePlan(shortMarkers);
        ObjectSourcePlan shortCrossing = new ObjectSourcePlan(shortMarkers, List.of(shortMarkers));
        ObjectSourcePlan longPlain = new ObjectSourcePlan(longMarkers);
        ObjectSourcePlan longCrossing = new ObjectSourcePlan(longMarkers, List.of(longMarkers));
        ObjectSourcePlan repeatedCrossing = new ObjectSourcePlan(shortMarkers, List.of(shortMarkers, shortMarkers));
        int metadataBytes = shortCrossing.estimatedRetainedBytes() - shortPlain.estimatedRetainedBytes();

        assertTrue(metadataBytes > 0);
        assertEquals(metadataBytes, longCrossing.estimatedRetainedBytes() - longPlain.estimatedRetainedBytes());
        assertTrue(repeatedCrossing.estimatedRetainedBytes() > shortCrossing.estimatedRetainedBytes());
        assertSame(shortMarkers.getFirst(), shortCrossing.mutationsFor(0, 0).getFirst());
        assertSame(shortMarkers.getLast(), shortCrossing.mutationsFor(1, 0).getFirst());
    }

    private static ObjectDestinationTransaction.Mutation mutationAt(int x, int z) {
        return new ObjectDestinationTransaction.SetMutation(
                new ObjectDestinationTransaction.DataKey(x, 4, z, String.class), "marker");
    }
}
