package art.arcane.iris.modded;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ModdedDimensionRestoreTest {
    private static final long STEP_TIMEOUT_SECONDS = 10L;

    @Test
    public void everyDimensionPreparesConcurrentlyOffTheCallingThread() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(4);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        steps.prepareBarrier = new CountDownLatch(dimensions.size());

        List<ModdedDimensionRestore.Result> results;
        try (ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps)) {
            results = restore.finish();
        }

        assertEquals(dimensions.size(), results.size());
        for (ModdedDimensionRestore.Result result : results) {
            assertNull(result.failure());
        }
        assertFalse(steps.prepareThreads.contains(Thread.currentThread()));
    }

    @Test
    public void engineBuildsRunConcurrentlyWhileLevelMutationStaysOnTheCallingThread() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(3);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        steps.buildBarrier = new CountDownLatch(dimensions.size());

        try (ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps)) {
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

        List<ModdedDimensionRestore.Result> results;
        try (ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps)) {
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

        List<ModdedDimensionRestore.Result> results;
        try (ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps)) {
            results = restore.finish();
        }

        assertSame(failure, results.get(0).failure());
        assertNull(results.get(1).failure());
        assertEquals(List.of("level-0"), steps.abandoned);
        assertEquals(List.of("level-1:engine-1"), steps.completed);
    }

    @Test
    public void aLevelThatCannotBeCreatedDiscardsItsPreparation() {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions = dimensions(2);
        RecordingSteps steps = new RecordingSteps(dimensions.size());
        IllegalStateException failure = new IllegalStateException("dimension type missing");
        steps.beginFailure = failure;
        steps.failingIndex = 1;

        List<ModdedDimensionRestore.Result> results;
        try (ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps)) {
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

        try (ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps)) {
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

        ModdedDimensionRestore<String, String, String> restore = ModdedDimensionRestore.start(dimensions, steps);
        restore.close();

        assertTrue(steps.discardedAll.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(steps.begun.isEmpty());
        assertEquals(Set.of("prepared-0", "prepared-1", "prepared-2"), Set.copyOf(steps.discarded));
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
                throw new IllegalStateException("Preparation gate never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the preparation gate", interrupted);
        }
    }

    private static final class RecordingSteps implements ModdedDimensionRestore.Steps<String, String, String> {
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
        public void abandon(String level, Throwable failure) {
            serverThreads.add(Thread.currentThread());
            abandoned.add(level);
        }

        @Override
        public void discard(String prepared) {
            discarded.add(prepared);
            discardedAll.countDown();
        }
    }
}
