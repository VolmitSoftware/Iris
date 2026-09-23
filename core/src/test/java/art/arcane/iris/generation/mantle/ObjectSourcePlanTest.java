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

        assertTrue(compact.mutationWeight() > 1 + first.weight() + nearby.weight());
        assertTrue(scattered.mutationWeight() > compact.mutationWeight());
        assertThrows(NullPointerException.class, () -> new ObjectSourcePlan(Arrays.asList(first, null)));
    }

    private static ObjectDestinationTransaction.Mutation mutationAt(int x, int z) {
        return new ObjectDestinationTransaction.SetMutation(
                new ObjectDestinationTransaction.DataKey(x, 4, z, String.class), "marker");
    }
}
