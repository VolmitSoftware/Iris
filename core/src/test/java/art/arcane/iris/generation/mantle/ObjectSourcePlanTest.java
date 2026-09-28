package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.matter.MatterMarker;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertEquals(local, plan.mutationsFor(-1, -1));
        assertEquals(3, plan.mutationsFor(Integer.MIN_VALUE >> 4, Integer.MAX_VALUE >> 4).size());
        assertEquals(3, plan.mutationsFor(Integer.MAX_VALUE >> 4, Integer.MIN_VALUE >> 4).size());
        assertTrue(plan.mutationsFor(1, -1).isEmpty());
        assertTrue(plan.mutationsFor(-1, 1).isEmpty());
        assertEquals(5, plan.destinationCount());
        assertThrows(UnsupportedOperationException.class, () -> local.clear());
    }

    @Test
    public void packedPlansReplayEveryDestinationInSourceOrder() {
        NativeBlockState[] states = new NativeBlockState[4];
        for (int index = 0; index < states.length; index++) {
            states[index] = mock(NativeBlockState.class);
        }
        NativeBlockState custom = mock(NativeBlockState.class);
        when(custom.isCustom()).thenReturn(true);
        MatterMarker marker = new MatterMarker("loot");
        Random random = new Random(1337L);
        for (int trial = 0; trial < 50; trial++) {
            int originX = random.nextInt(4096) - 2048;
            int originZ = random.nextInt(4096) - 2048;
            List<ObjectDestinationTransaction.Mutation> mutations = new ArrayList<>();
            List<ObjectDestinationTransaction.PlacementRange> placements = new ArrayList<>();
            int placementCount = 1 + random.nextInt(12);
            for (int placement = 0; placement < placementCount; placement++) {
                int start = mutations.size();
                String placementMarker = "trees/oak@" + random.nextInt(1000);
                int centerX = (originX << 4) + random.nextInt(16);
                int centerZ = (originZ << 4) + random.nextInt(16);
                int reach = 1 + random.nextInt(40);
                int blocks = 1 + random.nextInt(60);
                for (int block = 0; block < blocks; block++) {
                    int x = centerX + random.nextInt(2 * reach + 1) - reach;
                    int z = centerZ + random.nextInt(2 * reach + 1) - reach;
                    int y = random.nextInt(768);
                    mutations.add(switch (random.nextInt(6)) {
                        case 0 -> new ObjectDestinationTransaction.CustomBlockMutation(key(x, y, z, NativeBlockState.class), custom);
                        case 1 -> new ObjectDestinationTransaction.SetMutation(key(x, y, z, String.class), placementMarker);
                        case 2 -> new ObjectDestinationTransaction.SetMutation(key(x, y, z, TreeBlockMaterial.class),
                                new TreeBlockMaterial("minecraft:oak_log"));
                        case 3 -> new ObjectDestinationTransaction.SetMutation(key(x, y, z, MatterMarker.class), marker);
                        default -> {
                            NativeBlockState state = states[random.nextInt(states.length)];
                            yield new ObjectDestinationTransaction.SetMutation(key(x, y, z, NativeBlockState.class), state);
                        }
                    });
                }
                if (random.nextInt(4) != 0) {
                    placements.add(new ObjectDestinationTransaction.PlacementRange(start, mutations.size()));
                }
            }
            ObjectSourcePlan plan = new ObjectSourcePlan(mutations, placements);
            Map<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> expected = group(mutations);
            assertEquals(expected.size(), plan.destinationCount());
            for (Map.Entry<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> destination : expected.entrySet()) {
                List<ObjectDestinationTransaction.Mutation> replayed = plan.mutationsFor(destination.getKey().x(), destination.getKey().z());
                assertEquals(destination.getValue(), replayed);
                for (int index = 0; index < replayed.size(); index++) {
                    Object original = value(destination.getValue().get(index));
                    Object decoded = value(replayed.get(index));
                    if (!(original instanceof TreeBlockMaterial) && !(original instanceof String)) {
                        assertSame(original, decoded);
                    }
                }
            }
        }
    }

    @Test
    public void heightsOutsideThePackedRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectSourcePlan(List.of(
                new ObjectDestinationTransaction.SetMutation(key(0, -1, 0, String.class), "marker"))));
        assertThrows(IllegalArgumentException.class, () -> new ObjectSourcePlan(List.of(
                new ObjectDestinationTransaction.SetMutation(key(0, 1 << 22, 0, String.class), "marker"))));
        assertThrows(IllegalArgumentException.class, () -> new ObjectSourcePlan(List.of(
                new ObjectDestinationTransaction.SetMutation(key(0, 4, 0, Object.class), "marker"))));
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
    public void sharedPayloadsAreWeighedOncePerPlan() {
        List<ObjectDestinationTransaction.Mutation> one = List.of(mutationAt(0, 0));
        List<ObjectDestinationTransaction.Mutation> many = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            many.add(mutationAt(index & 15, 0));
        }
        List<ObjectDestinationTransaction.Mutation> trees = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            trees.add(new ObjectDestinationTransaction.SetMutation(key(index & 15, 4, 0, TreeBlockMaterial.class),
                    new TreeBlockMaterial("minecraft:oak_log")));
        }
        int single = new ObjectSourcePlan(one).estimatedRetainedBytes();

        assertEquals(99L * 8L, new ObjectSourcePlan(many).estimatedRetainedBytes() - single);
        assertTrue(new ObjectSourcePlan(trees).estimatedRetainedBytes() - single < 100L * 16L);
    }

    @Test
    public void retainedBytesIncludeVariableLengthMarkers() {
        ObjectDestinationTransaction.DataKey key = new ObjectDestinationTransaction.DataKey(0, 4, 0, String.class);
        ObjectSourcePlan shortMarker = new ObjectSourcePlan(List.of(new ObjectDestinationTransaction.SetMutation(key, "a")));
        ObjectSourcePlan longMarker = new ObjectSourcePlan(List.of(new ObjectDestinationTransaction.SetMutation(key, "a".repeat(4096))));
        assertTrue(longMarker.estimatedRetainedBytes() - shortMarker.estimatedRetainedBytes() >= 8190);
    }

    @Test
    public void unknownPayloadsAreWeighedInsteadOfDisablingRetention() {
        ObjectSourcePlan unknown = new ObjectSourcePlan(List.of(new ObjectDestinationTransaction.SetMutation(
                key(0, 4, 0, Object.class), new Object())));

        assertTrue(unknown.estimatedRetainedBytes() > new ObjectSourcePlan(List.of(mutationAt(0, 0))).estimatedRetainedBytes());
        assertTrue(unknown.estimatedRetainedBytes() < 4096);
    }

    @Test
    public void continuationMetadataAddsRetainedBytesWithoutCountingPayloadsTwice() {
        List<ObjectDestinationTransaction.Mutation> shortMarkers = List.of(mutationAt(0, 0), mutationAt(16, 0));
        List<ObjectDestinationTransaction.Mutation> longMarkers = List.of(
                new ObjectDestinationTransaction.SetMutation(
                        new ObjectDestinationTransaction.DataKey(0, 4, 0, String.class), "a".repeat(4096)),
                new ObjectDestinationTransaction.SetMutation(
                        new ObjectDestinationTransaction.DataKey(16, 4, 0, String.class), "b".repeat(4096)));
        ObjectDestinationTransaction.PlacementRange whole = new ObjectDestinationTransaction.PlacementRange(0, 2);
        ObjectSourcePlan shortPlain = new ObjectSourcePlan(shortMarkers);
        ObjectSourcePlan shortCrossing = new ObjectSourcePlan(shortMarkers, List.of(whole));
        ObjectSourcePlan longPlain = new ObjectSourcePlan(longMarkers);
        ObjectSourcePlan longCrossing = new ObjectSourcePlan(longMarkers, List.of(whole));
        ObjectSourcePlan repeatedCrossing = new ObjectSourcePlan(shortMarkers, List.of(whole, whole));
        int metadataBytes = shortCrossing.estimatedRetainedBytes() - shortPlain.estimatedRetainedBytes();

        assertTrue(metadataBytes > 0);
        assertEquals(metadataBytes, longCrossing.estimatedRetainedBytes() - longPlain.estimatedRetainedBytes());
        assertTrue(repeatedCrossing.estimatedRetainedBytes() > shortCrossing.estimatedRetainedBytes());
        assertEquals(shortMarkers.getFirst(), shortCrossing.mutationsFor(0, 0).getFirst());
        assertEquals(shortMarkers.getLast(), shortCrossing.mutationsFor(1, 0).getFirst());
    }

    @Test
    public void destinationIndexKeepsOnlyTheTouchedChunks() {
        ObjectSourcePlan plan = new ObjectSourcePlan(List.of(mutationAt(-1, 3), mutationAt(40, 3), mutationAt(-2, 3)));
        ObjectSourcePlan index = plan.destinationIndex();

        assertEquals(2, index.destinationCount());
        assertEquals(plan.destinationSlot(-1, 0), index.destinationSlot(-1, 0));
        assertEquals(plan.destinationSlot(2, 0), index.destinationSlot(2, 0));
        assertTrue(index.destinationSlot(0, 0) < 0);
        assertTrue(index.mutationsFor(-1, 0).isEmpty());
        assertTrue(index.estimatedRetainedBytes() < plan.estimatedRetainedBytes());
    }

    private static Map<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> group(
            List<ObjectDestinationTransaction.Mutation> mutations
    ) {
        Map<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> grouped = new LinkedHashMap<>();
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            grouped.computeIfAbsent(new ObjectContinuationBundle.ChunkPosition(mutation.x() >> 4, mutation.z() >> 4),
                    ignored -> new ArrayList<>()).add(mutation);
        }
        return grouped;
    }

    private static Object value(ObjectDestinationTransaction.Mutation mutation) {
        return switch (mutation) {
            case ObjectDestinationTransaction.SetMutation set -> set.value();
            case ObjectDestinationTransaction.CustomBlockMutation custom -> custom.state();
        };
    }

    private static ObjectDestinationTransaction.DataKey key(int x, int y, int z, Class<?> type) {
        return new ObjectDestinationTransaction.DataKey(x, y, z, type);
    }

    private static ObjectDestinationTransaction.Mutation mutationAt(int x, int z) {
        return new ObjectDestinationTransaction.SetMutation(
                new ObjectDestinationTransaction.DataKey(x, 4, z, String.class), "marker");
    }
}
