package art.arcane.iris.structure.object;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PlacementLifecycleTest {
    private static PlacementLifecycleDependency id(String name) {
        return new PlacementLifecycleDependency(name);
    }

    @Test
    public void resolvesDependenciesAcrossModulesRegardlessOfInstallOrder() {
        List<String> calls = new ArrayList<>();
        PlacementModule consumer = builder -> builder.add(id("consumer"), PlacementStage.HEIGHT,
                (context, state, ignored) -> calls.add("consumer"), id("provider"));
        PlacementModule provider = builder -> builder.add(id("provider"), PlacementStage.HEIGHT,
                (context, state, ignored) -> calls.add("provider"));
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        consumer.install(builder);
        provider.install(builder);
        builder.add(id("independent"), PlacementStage.HEIGHT,
                (context, state, ignored) -> calls.add("independent"));

        PlacementLifecycle plan = builder.build();
        assertTrue(calls.isEmpty());
        plan.run(PlacementStage.HEIGHT, null, new PlacementState(0, null), null);
        assertEquals(List.of("provider", "consumer", "independent"), calls);
    }

    @Test
    public void rejectsMissingDuplicateCyclicAndBackwardDependencies() {
        PlacementLifecycleBuilder missing = new PlacementLifecycleBuilder();
        missing.add(id("stilt"), PlacementStage.AFTER_BLOCKS, (c, s, v) -> {}, id("bounds"));
        String message = assertThrows(IllegalArgumentException.class, missing::build).getMessage();
        assertTrue(message.contains("stilt"));
        assertTrue(message.contains("bounds"));

        PlacementLifecycleBuilder duplicate = new PlacementLifecycleBuilder();
        duplicate.add(id("a"), PlacementStage.HEIGHT, (c, s, v) -> {});
        assertThrows(IllegalArgumentException.class,
                () -> duplicate.add(id("a"), PlacementStage.HEIGHT, (c, s, v) -> {}));

        PlacementLifecycleBuilder cyclic = new PlacementLifecycleBuilder();
        cyclic.add(id("a"), PlacementStage.HEIGHT, (c, s, v) -> {}, id("b"));
        cyclic.add(id("b"), PlacementStage.HEIGHT, (c, s, v) -> {}, id("a"));
        message = assertThrows(IllegalArgumentException.class, cyclic::build).getMessage();
        assertTrue(message.contains("cycle"));
        assertTrue(message.contains("a"));
        assertTrue(message.contains("b"));

        PlacementLifecycleBuilder backward = new PlacementLifecycleBuilder();
        backward.add(id("height"), PlacementStage.HEIGHT, (c, s, v) -> {}, id("fill"));
        backward.add(id("fill"), PlacementStage.AFTER_BLOCKS, (c, s, v) -> {});
        assertTrue(assertThrows(IllegalArgumentException.class, backward::build)
                .getMessage().contains("later step"));
    }

    @Test
    public void rejectsSelfDependencyAndAcceptsRepeatedPrerequisite() {
        PlacementLifecycleBuilder self = new PlacementLifecycleBuilder();
        self.add(id("self"), PlacementStage.HEIGHT, (c, s, v) -> {}, id("self"));
        assertThrows(IllegalArgumentException.class, self::build);

        PlacementLifecycleBuilder repeated = new PlacementLifecycleBuilder();
        repeated.add(id("a"), PlacementStage.HEIGHT, (c, s, v) -> s.y++);
        repeated.add(id("b"), PlacementStage.HEIGHT, (c, s, v) -> s.y *= 2, id("a"), id("a"));
        PlacementState state = new PlacementState(2, null);
        repeated.build().run(PlacementStage.HEIGHT, null, state, null);
        assertEquals(6, state.y);
    }

    @Test
    public void plansAreSnapshotsAndFailuresPropagateWithoutRunningDependents() {
        List<String> calls = new ArrayList<>();
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        builder.add(id("first"), PlacementStage.HEIGHT, (c, s, v) -> calls.add("first"));
        PlacementLifecycle original = builder.build();
        IllegalStateException failure = new IllegalStateException("failed placement");
        builder.add(id("failure"), PlacementStage.HEIGHT, (c, s, v) -> { throw failure; }, id("first"));
        builder.add(id("dependent"), PlacementStage.HEIGHT, (c, s, v) -> calls.add("dependent"), id("failure"));
        original.run(PlacementStage.HEIGHT, null, new PlacementState(0, null), null);
        assertEquals(List.of("first"), calls);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> builder.build().run(PlacementStage.HEIGHT, null, new PlacementState(0, null), null)));
        assertEquals(List.of("first", "first"), calls);
    }

    @Test
    public void everyModeResolvesAndRuntimePlansAreIndependent() {
        PlacementLifecycles first = PlacementLifecycles.resolve();
        PlacementLifecycles second = PlacementLifecycles.resolve();
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            assertNotNull(first.get(mode));
            assertNotSame(first.get(mode), second.get(mode));
        }
    }

    @Test
    public void resolvesLongDependencyChainsWithoutRecursiveTraversal() {
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        for (int i = 9999; i >= 0; i--) {
            builder.add(id("step-" + i), PlacementStage.HEIGHT, (c, s, v) -> s.y++,
                    i == 0 ? new PlacementLifecycleDependency[0]
                            : new PlacementLifecycleDependency[]{id("step-" + (i - 1))});
        }
        PlacementState state = new PlacementState(0, null);
        builder.build().run(PlacementStage.HEIGHT, null, state, null);
        assertEquals(10000, state.y);
    }

    @Test
    public void sharedHeightPlanKeepsConcurrentPlacementStateSeparate() throws Exception {
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        new PlacementModulePreparation().install(builder);
        new PlacementModuleSlope().install(builder);
        new PlacementModuleHeight(PlacementModuleHeight.Mode.MAX, false).install(builder);
        PlacementLifecycle plan = builder.build();
        CountDownLatch sampled = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> sample(plan, 10, sampled));
            var second = executor.submit(() -> sample(plan, 100, sampled));
            assertEquals(11, (int) first.get(10, TimeUnit.SECONDS));
            assertEquals(101, (int) second.get(10, TimeUnit.SECONDS));
        }
    }

    private int sample(PlacementLifecycle plan, int x, CountDownLatch sampled) throws Exception {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenAnswer(call -> {
            sampled.countDown();
            assertTrue(sampled.await(5, TimeUnit.SECONDS));
            return call.<Integer>getArgument(0);
        });
        IrisObjectPlacement config = new IrisObjectPlacement().setForcePlace(true);
        IrisObject object = mock(IrisObject.class);
        when(object.getCenter()).thenReturn(new art.arcane.volmlib.util.math.Vector3i(1, 0, 0));
        when(object.getW()).thenReturn(3);
        when(object.getH()).thenReturn(1);
        when(object.getD()).thenReturn(1);
        PlacementContext context = new PlacementContext(config, mock(IrisData.class), placer, null,
                new RNG(1), object, x, 0, -1, null);
        PlacementState state = new PlacementState(context);
        plan.run(PlacementStage.PREPARE, context, state, null);
        plan.run(PlacementStage.HEIGHT, context, state, null);
        return state.y;
    }

    @Test
    public void guardOwnsTheForcePlaceExemption() {
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        builder.add(id("guard"), PlacementStage.HEIGHT, (c, s, v) -> {
            if (!s.config.isForcePlace()) {
                s.reject("guard");
            }
        });
        builder.add(id("dependent"), PlacementStage.HEIGHT, (c, s, v) -> s.y = 99, id("guard"));
        PlacementLifecycle plan = builder.build();
        IrisObjectPlacement config = new IrisObjectPlacement();
        PlacementContext context = new PlacementContext(config, mock(IrisData.class), mock(IObjectPlacer.class),
                null, new RNG(1), mock(IrisObject.class), 0, 0, -1, null);
        PlacementState rejected = new PlacementState(context);
        plan.run(PlacementStage.HEIGHT, context, rejected, null);
        assertEquals(-1, rejected.heightOrRejected());
        assertEquals("guard", rejected.getRejection());
        config.setForcePlace(true);
        PlacementState forced = new PlacementState(context);
        plan.run(PlacementStage.HEIGHT, context, forced, null);
        assertEquals(99, forced.y);
    }

    @Test
    public void rejectionIsTerminalAcrossPhasesEvenWithForcePlace() {
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        List<String> calls = new ArrayList<>();
        builder.add(id("reject"), PlacementStage.VALIDATE, (c, s, v) -> s.reject("protected"));
        builder.add(id("bore"), PlacementStage.BEFORE_BLOCKS, (c, s, v) -> calls.add("bore"));
        builder.add(id("blocks"), PlacementStage.BLOCKS, (c, s, v) -> calls.add("blocks"));
        builder.add(id("finish"), PlacementStage.AFTER_BLOCKS, (c, s, v) -> calls.add("finish"));
        PlacementContext context = new PlacementContext(new IrisObjectPlacement().setForcePlace(true),
                mock(IrisData.class), mock(IObjectPlacer.class), null, new RNG(1),
                mock(IrisObject.class), 0, 0, 80, null);
        PlacementState result = builder.build().execute(context);
        assertEquals(-1, result.heightOrRejected());
        result.reject("later reason");
        assertEquals("protected", result.getRejection());
        assertTrue(calls.isEmpty());
    }
}
