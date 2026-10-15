package art.arcane.iris.modded;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ModdedDimensionInjectionTest {
    private static final long STEP_TIMEOUT_SECONDS = 10L;

    @Test
    public void everyDimensionPreparesConcurrentlyOffTheCallingThread() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(4);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        steps.prepareBarrier = new CountDownLatch(dimensions.size());

        List<ModdedDimensionInjection.Result> results;
        try (ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps)) {
            results = restore.finish();
        }

        assertEquals(dimensions.size(), results.size());
        for (ModdedDimensionInjection.Result result : results) {
            assertNull(result.failure());
        }
        assertFalse(steps.prepareThreads.contains(Thread.currentThread()));
    }

    @Test
    public void engineBuildsRunConcurrentlyWhileLevelMutationStaysOnTheCallingThread() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(3);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        steps.buildBarrier = new CountDownLatch(dimensions.size());

        try (ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps)) {
            restore.finish();
        }

        assertFalse(steps.buildThreads.contains(Thread.currentThread()));
        assertEquals(Set.of(Thread.currentThread()), steps.serverThreads);
        assertEquals(List.of("level-0:engine-0", "level-1:engine-1", "level-2:engine-2"), steps.completed);
    }

    @Test
    public void aFailedPreparationIsReportedWhileTheOtherDimensionsStillRestore() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(3);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        IllegalStateException failure = new IllegalStateException("generation history is unusable");
        steps.prepareFailure = failure;
        steps.failingIndex = 1;

        List<ModdedDimensionInjection.Result> results;
        try (ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps)) {
            results = restore.finish();
        }

        assertNull(results.get(0).failure());
        assertSame(failure, results.get(1).failure());
        assertNull(results.get(2).failure());
        assertSame(dimensions.get(1), results.get(1).dimension());
        assertEquals(List.of("level-0:engine-0", "level-2:engine-2"), steps.completed);
        assertTrue(steps.begun.stream().noneMatch((String level) -> level.equals("level-1")));
    }

    @Test
    public void aFailedEngineBuildAbandonsItsLevelWithoutCompletingIt() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(2);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        IllegalStateException failure = new IllegalStateException("engine refused");
        steps.buildFailure = failure;
        steps.failingIndex = 0;

        List<ModdedDimensionInjection.Result> results;
        try (ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps)) {
            results = restore.finish();
        }

        assertSame(failure, results.get(0).failure());
        assertNull(results.get(1).failure());
        assertEquals(List.of("level-0:null"), steps.abandoned);
        assertEquals(List.of("level-1:engine-1"), steps.completed);
    }

    @Test
    public void aLevelThatCannotBeCreatedDiscardsItsPreparation() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(2);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        IllegalStateException failure = new IllegalStateException("dimension type missing");
        steps.beginFailure = failure;
        steps.failingIndex = 1;

        List<ModdedDimensionInjection.Result> results;
        try (ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps)) {
            results = restore.finish();
        }

        assertSame(failure, results.get(1).failure());
        assertEquals(List.of("prepared-1"), steps.discarded);
        assertEquals(List.of("level-0:engine-0"), steps.completed);
    }

    @Test
    public void awaitingPreparationsReturnsOnlyOnceEveryDimensionIsPreparedAndCreatesNoLevel() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(3);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        CountDownLatch release = new CountDownLatch(1);
        steps.prepareGate = release;
        Thread.ofPlatform().daemon(true).start(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
        });

        try (ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps)) {
            restore.awaitPreparations();

            assertEquals(dimensions.size(), steps.prepared.get());
            assertTrue(steps.begun.isEmpty());
            restore.finish();
        }

        assertEquals(List.of("level-0:engine-0", "level-1:engine-1", "level-2:engine-2"), steps.completed);
        assertEquals(0L, release.getCount());
    }

    @Test
    public void closingAnUnfinishedRestoreDiscardsEveryPreparation() throws InterruptedException {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(3);
        RecordingSteps steps = new RecordingSteps(dimensions.size());

        ModdedDimensionInjection<String, String, String> restore = ModdedDimensionInjection.start(dimensions, steps);
        restore.close();

        assertTrue(steps.discardedAll.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(steps.begun.isEmpty());
        assertEquals(Set.of("prepared-0", "prepared-1", "prepared-2"), Set.copyOf(steps.discarded));
    }

    @Test
    public void asyncInjectionReturnsAtOnceAndMutatesLevelsOnlyOnTheServerExecutor() throws Exception {
        ModdedDimensionRegistryStore.PersistentDimension dimension = dimensions(1).getFirst();
        RecordingSteps steps = new RecordingSteps(1);
        CountDownLatch release = new CountDownLatch(1);
        steps.prepareGate = release;
        ExecutorService server = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("test-server").factory());
        try {
            Thread serverThread = server.submit(Thread::currentThread).get();
            ModdedDimensionInjection.Async<String, String, String> async =
                    ModdedDimensionInjection.async(dimension, steps, server);
            async.start();

            assertFalse(async.result().isDone());
            release.countDown();
            ModdedDimensionInjection.Result result = async.result().get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertNull(result.failure());
            assertSame(dimension, result.dimension());
            assertEquals(List.of("level-0:engine-0"), steps.completed);
            assertEquals(Set.of(serverThread), steps.serverThreads);
            assertFalse(steps.prepareThreads.contains(serverThread));
            assertFalse(steps.prepareThreads.contains(Thread.currentThread()));
            assertFalse(steps.buildThreads.contains(serverThread));
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    public void asyncPreparationFailureCompletesWithoutCreatingALevel() throws Exception {
        ModdedDimensionRegistryStore.PersistentDimension dimension = dimensions(1).getFirst();
        RecordingSteps steps = new RecordingSteps(1);
        IllegalStateException failure = new IllegalStateException("pack refused");
        steps.prepareFailure = failure;
        steps.failingIndex = 0;
        ExecutorService server = Executors.newSingleThreadExecutor();
        try {
            ModdedDimensionInjection.Async<String, String, String> async =
                    ModdedDimensionInjection.async(dimension, steps, server);
            async.start();

            ModdedDimensionInjection.Result result = async.result().get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertSame(failure, result.failure());
            assertTrue(steps.begun.isEmpty());
            assertTrue(steps.completed.isEmpty());
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    public void asyncEngineFailureAbandonsTheCreatedLevel() throws Exception {
        ModdedDimensionRegistryStore.PersistentDimension dimension = dimensions(1).getFirst();
        RecordingSteps steps = new RecordingSteps(1);
        IllegalStateException failure = new IllegalStateException("engine refused");
        steps.buildFailure = failure;
        steps.failingIndex = 0;
        ExecutorService server = Executors.newSingleThreadExecutor();
        try {
            ModdedDimensionInjection.Async<String, String, String> async =
                    ModdedDimensionInjection.async(dimension, steps, server);
            async.start();

            ModdedDimensionInjection.Result result = async.result().get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertSame(failure, result.failure());
            assertEquals(List.of("level-0:null"), steps.abandoned);
            assertTrue(steps.completed.isEmpty());
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    public void cancellingWhilePreparingDiscardsThePreparationWithoutCreatingALevel() throws Exception {
        ModdedDimensionRegistryStore.PersistentDimension dimension = dimensions(1).getFirst();
        RecordingSteps steps = new RecordingSteps(1);
        CountDownLatch release = new CountDownLatch(1);
        steps.prepareGate = release;
        ExecutorService server = Executors.newSingleThreadExecutor();
        try {
            ModdedDimensionInjection.Async<String, String, String> async =
                    ModdedDimensionInjection.async(dimension, steps, server);
            async.start();

            server.submit(async::cancel).get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            release.countDown();
            ModdedDimensionInjection.Result result = async.result().get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertTrue(result.failure() instanceof CancellationException);
            assertTrue(steps.discardedAll.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(List.of("prepared-0"), steps.discarded);
            server.submit(() -> null).get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(steps.begun.isEmpty());
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    public void cancellingWhileBuildingWaitsForTheEngineAndAbandonsTheLevelWithIt() throws Exception {
        ModdedDimensionRegistryStore.PersistentDimension dimension = dimensions(1).getFirst();
        RecordingSteps steps = new RecordingSteps(1);
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        steps.buildStarted = building;
        steps.buildGate = release;
        ExecutorService server = Executors.newSingleThreadExecutor();
        try {
            ModdedDimensionInjection.Async<String, String, String> async =
                    ModdedDimensionInjection.async(dimension, steps, server);
            async.start();
            assertTrue(building.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            Future<?> cancelled = server.submit(async::cancel);
            Thread.ofPlatform().daemon(true).start(() -> {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                release.countDown();
            });
            cancelled.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ModdedDimensionInjection.Result result = async.result().get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            server.submit(() -> null).get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertTrue(result.failure() instanceof CancellationException);
            assertEquals(List.of("level-0:engine-0"), steps.abandoned);
            assertTrue(steps.completed.isEmpty());
        } finally {
            server.shutdownNow();
        }
    }

    private static List<ModdedDimensionRegistryStore.PersistentDimension> dimensions(int count) {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            dimensions.add(new ModdedDimensionRegistryStore.PersistentDimension(
                    "iris:world" + index, "overworld", "overworld", 1000L + index));
        }
        return dimensions;
    }

    private static int index(String value) {
        return Integer.parseInt(value.substring(value.lastIndexOf('-') + 1));
    }

    private static int index(ModdedDimensionRegistryStore.PersistentDimension dimension) {
        return Integer.parseInt(dimension.id().substring("iris:world".length()));
    }

    private static void await(CountDownLatch barrier) {
        if (barrier == null) {
            return;
        }
        barrier.countDown();
        try {
            if (!barrier.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Restore steps did not run concurrently");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent restore steps", interrupted);
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        if (gate == null) {
            return;
        }
        try {
            if (!gate.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Restore step gate never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a restore step gate", interrupted);
        }
    }

    private static final class RecordingSteps implements ModdedDimensionInjection.Steps<String, String, String> {
        private final Set<Thread> prepareThreads = ConcurrentHashMap.newKeySet();
        private final Set<Thread> buildThreads = ConcurrentHashMap.newKeySet();
        private final Set<Thread> serverThreads = ConcurrentHashMap.newKeySet();
        private final List<String> begun = Collections.synchronizedList(new ArrayList<>());
        private final List<String> completed = Collections.synchronizedList(new ArrayList<>());
        private final List<String> abandoned = Collections.synchronizedList(new ArrayList<>());
        private final List<String> discarded = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch discardedAll;
        private final AtomicInteger prepared = new AtomicInteger();
        private CountDownLatch prepareGate;
        private CountDownLatch prepareBarrier;
        private CountDownLatch buildBarrier;
        private CountDownLatch buildStarted;
        private CountDownLatch buildGate;
        private RuntimeException prepareFailure;
        private RuntimeException beginFailure;
        private RuntimeException buildFailure;
        private int failingIndex = -1;

        private RecordingSteps(int dimensions) {
            this.discardedAll = new CountDownLatch(dimensions);
        }

        @Override
        public String prepare(ModdedDimensionRegistryStore.PersistentDimension dimension) {
            prepareThreads.add(Thread.currentThread());
            await(prepareBarrier);
            awaitGate(prepareGate);
            int index = index(dimension);
            if (prepareFailure != null && index == failingIndex) {
                throw prepareFailure;
            }
            prepared.incrementAndGet();
            return "prepared-" + index;
        }

        @Override
        public String begin(ModdedDimensionRegistryStore.PersistentDimension dimension, String prepared) {
            serverThreads.add(Thread.currentThread());
            int index = index(dimension);
            if (beginFailure != null && index == failingIndex) {
                throw beginFailure;
            }
            String level = "level-" + index;
            begun.add(level);
            return level;
        }

        @Override
        public String build(String level, String prepared) {
            buildThreads.add(Thread.currentThread());
            await(buildBarrier);
            if (buildStarted != null) {
                buildStarted.countDown();
            }
            awaitGate(buildGate);
            int index = index(level);
            if (buildFailure != null && index == failingIndex) {
                throw buildFailure;
            }
            return "engine-" + index;
        }

        @Override
        public void complete(String level, String engine) {
            serverThreads.add(Thread.currentThread());
            completed.add(level + ":" + engine);
        }

        @Override
        public void abandon(String level, String engine, Throwable failure) {
            serverThreads.add(Thread.currentThread());
            abandoned.add(level + ":" + engine);
        }

        @Override
        public void discard(String prepared) {
            discarded.add(prepared);
            discardedAll.countDown();
        }
    }
}
