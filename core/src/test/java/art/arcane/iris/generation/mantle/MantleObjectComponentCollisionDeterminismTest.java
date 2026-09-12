package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleObjectComponentCollisionDeterminismTest {
    private static final int RADIUS = 16;
    private static final int TARGET_Y = 10;
    private static final String BLOCKER = "blocker@1";
    private static final String TARGET = "target@2";
    private static final Position LEFT = new Position(15, TARGET_Y, 0);
    private static final Position RIGHT = new Position(16, TARGET_Y, 0);

    @Test
    public void shiftedDestinationWindowsWouldSplitOneCollisionDecision() {
        Scenario scenario = new Scenario(false, false, false);
        Map<Position, String> blocks = new HashMap<>();

        blocks.putAll(generateDirectDestination(0, scenario));
        blocks.putAll(generateDirectDestination(1, scenario));

        assertEquals(BLOCKER, blocks.get(LEFT));
        assertEquals(TARGET, blocks.get(RIGHT));
    }

    @Test
    public void sourceAnchoredPlansMatchForForwardReverseAndParallelDestinations() throws Exception {
        Scenario scenario = new Scenario(false, false, false);
        Map<Position, String> forward = generateAnchoredWorld(List.of(0, 1), scenario);
        Map<Position, String> reverse = generateAnchoredWorld(List.of(1, 0), scenario);
        Map<Position, String> parallel = generateAnchoredWorldParallel(scenario);

        assertEquals(forward, reverse);
        assertEquals(forward, parallel);
        assertFalse(TARGET.equals(forward.get(LEFT)));
        assertFalse(TARGET.equals(forward.get(RIGHT)));
    }

    @Test
    public void allowedCollisionOverrideAcceptsEveryDestinationFragment() {
        Map<Position, String> blocks = generateAnchoredWorld(
                List.of(0, 1),
                new Scenario(true, false, false)
        );

        assertEquals(TARGET, blocks.get(LEFT));
        assertEquals(TARGET, blocks.get(RIGHT));
    }

    @Test
    public void sameSourcePredecessorStillVetoesEveryDestinationFragment() {
        Map<Position, String> blocks = generateAnchoredWorld(
                List.of(0, 1),
                new Scenario(false, true, false)
        );

        assertEquals(BLOCKER, blocks.get(LEFT));
        assertFalse(TARGET.equals(blocks.get(RIGHT)));
    }

    @Test
    public void blockerOutsideTheAnchoredHaloIsUniformlyIgnored() {
        Map<Position, String> blocks = generateAnchoredWorld(
                List.of(0, 1),
                new Scenario(false, false, true)
        );

        assertEquals(TARGET, blocks.get(LEFT));
        assertEquals(TARGET, blocks.get(RIGHT));
    }

    private static Map<Position, String> generateAnchoredWorld(List<Integer> destinations, Scenario scenario) {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(256L);
        Map<Position, String> blocks = new HashMap<>();
        for (Integer destination : destinations) {
            blocks.putAll(generateAnchoredDestination(destination, scenario, cache));
        }
        return blocks;
    }

    private static Map<Position, String> generateAnchoredWorldParallel(Scenario scenario) throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(256L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<Position, String>> left = executor.submit(
                    () -> generateAnchoredDestinationAfterBarrier(0, scenario, cache, ready, start)
            );
            Future<Map<Position, String>> right = executor.submit(
                    () -> generateAnchoredDestinationAfterBarrier(1, scenario, cache, ready, start)
            );
            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            Map<Position, String> blocks = new HashMap<>(left.get(5L, TimeUnit.SECONDS));
            blocks.putAll(right.get(5L, TimeUnit.SECONDS));
            return blocks;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Map<Position, String> generateAnchoredDestinationAfterBarrier(
            int destination,
            Scenario scenario,
            ObjectSourcePlanCache cache,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(5L, TimeUnit.SECONDS));
        return generateAnchoredDestination(destination, scenario, cache);
    }

    private static Map<Position, String> generateAnchoredDestination(
            int destination,
            Scenario scenario,
            ObjectSourcePlanCache cache
    ) {
        Map<Position, String> blocks = new HashMap<>();
        MantleWriter writer = writer(blocks);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, destination, 0);
        MantleObjectComponent.replaySourceChunks(destination, 0, RADIUS, (sourceX, sourceZ) ->
                transaction.apply(cache.get(sourceX, sourceZ,
                        () -> buildSourcePlan(writer, sourceX, sourceZ, scenario))));
        transaction.commit();
        return blocks;
    }

    private static Map<Position, String> generateDirectDestination(int destination, Scenario scenario) {
        Map<Position, String> blocks = new HashMap<>();
        MantleWriter writer = writer(blocks);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, destination, 0);
        MantleObjectComponent.replaySourceChunks(destination, 0, RADIUS,
                (sourceX, sourceZ) -> generateOrigin(transaction, sourceX, sourceZ, scenario));
        transaction.commit();
        return blocks;
    }

    private static ObjectSourcePlan buildSourcePlan(
            MantleWriter writer,
            int sourceX,
            int sourceZ,
            Scenario scenario
    ) {
        ObjectDestinationTransaction scratch = new ObjectDestinationTransaction(writer, sourceX, sourceZ);
        MantleObjectComponent.replaySourcePredecessors(sourceX, sourceZ, RADIUS,
                (predecessorX, predecessorZ) -> generateOrigin(scratch, predecessorX, predecessorZ, scenario));
        int checkpoint = scratch.mutationCheckpoint();
        generateOrigin(scratch, sourceX, sourceZ, scenario);
        return scratch.sourcePlanSince(checkpoint);
    }

    private static void generateOrigin(
            ObjectDestinationTransaction transaction,
            int sourceX,
            int sourceZ,
            Scenario scenario
    ) {
        if (isBlockerSource(sourceX, sourceZ, scenario)) {
            transaction.setData(LEFT.x(), LEFT.y(), LEFT.z(), BLOCKER);
        }
        if (sourceX != 0 || sourceZ != 0) {
            return;
        }
        String collision = transaction.getDataIfPresent(LEFT.x(), LEFT.y(), LEFT.z(), String.class);
        if (BLOCKER.equals(collision) && !scenario.allowed()) {
            return;
        }
        transaction.setData(LEFT.x(), LEFT.y(), LEFT.z(), TARGET);
        transaction.setData(RIGHT.x(), RIGHT.y(), RIGHT.z(), TARGET);
    }

    private static boolean isBlockerSource(int sourceX, int sourceZ, Scenario scenario) {
        if (sourceZ != 0) {
            return false;
        }
        if (scenario.sameSource()) {
            return sourceX == 0;
        }
        return sourceX == (scenario.outsideHalo() ? -2 : -1);
    }

    @SuppressWarnings("unchecked")
    private static MantleWriter writer(Map<Position, String> blocks) {
        MantleWriter writer = mock(MantleWriter.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.getWorldHeight()).thenReturn(64);
        when(writer.getMantle()).thenReturn(mantle);
        when(writer.getPrerequisiteDataIfPresent(anyInt(), anyInt(), anyInt(), any())).thenReturn(null);
        doAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            Object value = invocation.getArgument(3);
            if (value instanceof String marker) {
                blocks.put(new Position(x, y, z), marker);
            }
            return null;
        }).when(writer).setData(anyInt(), anyInt(), anyInt(), any());
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(2);
            task.run();
            return null;
        }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
        return writer;
    }

    private record Scenario(boolean allowed, boolean sameSource, boolean outsideHalo) {
    }

    private record Position(int x, int y, int z) {
    }
}
