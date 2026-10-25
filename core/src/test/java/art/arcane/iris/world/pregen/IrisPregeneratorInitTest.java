package art.arcane.iris.world.pregen;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisPregeneratorInitTest {
    @Test
    public void initDoesNotSaveBeforeGenerationStarts() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        TrackingPregeneratorMethod method = new TrackingPregeneratorMethod();
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(16)
                .radiusZ(16)
                .build();
        try {
            new IrisPregenerator(task, method, new NoOpPregenListener(), null).start();

            assertEquals(1, method.initCalls.get());
            int init = method.lifecycle.indexOf("init");
            int firstChunk = firstIndex(method.lifecycle, "chunk:");
            int firstSave = method.lifecycle.indexOf("save");
            assertTrue(init >= 0 && init < firstChunk);
            assertTrue("a save ran before generation started: " + method.lifecycle, firstSave < 0 || firstSave > firstChunk);
        } finally {
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void pregenPreparationRunsBeforeTheFirstChunkSubmission() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        TrackingPregeneratorMethod method = new TrackingPregeneratorMethod();
        PregenTask task = PregenTask.builder()
                .center(new Position2(128, 128))
                .radiusX(1)
                .radiusZ(1)
                .build();
        try {
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener(), null);

            pregenerator.start();

            int boundsIndex = method.lifecycle.indexOf("bounds");
            int startIndex = method.lifecycle.indexOf("ahead:0,0");
            int chunkIndex = firstIndex(method.lifecycle, "chunk:");
            assertTrue(boundsIndex >= 0);
            assertTrue(startIndex > boundsIndex);
            assertTrue(chunkIndex > startIndex);
        } finally {
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void pregenAnnouncesEachRegionItWillGenerateOnceRightAfterThePreviousRegionStarts() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        Set<Position2> cached = Set.of(new Position2(0, 0), new Position2(-1, 0), new Position2(1, 1));
        TrackingPregeneratorMethod method = new TrackingPregeneratorMethod(cached);
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(600)
                .radiusZ(600)
                .build();
        try {
            new IrisPregenerator(task, method, new NoOpPregenListener(), null).start();

            assertEquals(13, method.generated.size());
            for (String region : method.generated) {
                assertFalse(cached.contains(position(region)));
            }
            assertAnnouncedOnceBetweenRegions(method, method.generated);
        } finally {
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void regionsSavedOnDiskAreNeverAnnouncedWithoutTheCacheWrapper() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        Set<Position2> saved = Set.of(new Position2(0, 0), new Position2(-1, 0), new Position2(1, 1));
        TrackingPregeneratorMethod method = new TrackingPregeneratorMethod(Set.of());
        PregenSavedChunkStatus status = new PregenSavedChunkStatus((chunkX, chunkZ) -> saved.contains(new Position2(chunkX >> 5, chunkZ >> 5)));
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(600)
                .radiusZ(600)
                .build();
        try {
            new IrisPregenerator(task, method, new NoOpPregenListener(), status).start();

            List<String> pending = new ArrayList<>();
            for (String region : method.generated) {
                if (!saved.contains(position(region))) {
                    pending.add(region);
                }
            }
            assertEquals(16, method.generated.size());
            assertEquals(13, pending.size());
            assertAnnouncedOnceBetweenRegions(method, pending);
        } finally {
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void completionSummaryWaitsForAsyncCloseDrain() {
        String output = runCompletionOnClose(false);

        assertTrue(output.contains("Pregen finished: generated=9 total=9 failed=0"));
    }

    @Test
    public void completionSummaryIncludesFailureDrainedOnClose() {
        String output = runCompletionOnClose(true);

        assertTrue(output.contains("Pregen finished: generated=8 total=9 failed=1"));
    }

    @Test
    public void cancellationSummaryDoesNotReportPartialRunAsFinished() {
        IrisSettings previousSettings = IrisSettings.settings;
        PrintStream previousOut = System.out;
        IrisSettings.settings = new IrisSettings();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        AtomicReference<IrisPregenerator> pregeneratorReference = new AtomicReference<>();
        CancelAfterFirstChunkMethod method = new CancelAfterFirstChunkMethod(pregeneratorReference);
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(1)
                .radiusZ(1)
                .build();
        try {
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener(), null);
            pregeneratorReference.set(pregenerator);

            pregenerator.start();

            String summary = output.toString(StandardCharsets.UTF_8);
            assertTrue(summary.contains("Pregen cancelled: generated=1 total=9 failed=0 remaining=8"));
            assertFalse(summary.contains("Pregen finished:"));
        } finally {
            System.setOut(previousOut);
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void shutdownConsumesCancellationInterruptBetweenCleanupSteps() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        AtomicBoolean listenerObservedInterrupt = new AtomicBoolean();
        TrackingPregeneratorMethod method = new TrackingPregeneratorMethod(true);
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(1)
                .radiusZ(1)
                .build();
        try {
            IrisPregenerator pregenerator = new IrisPregenerator(
                    task,
                    method,
                    new NoOpPregenListener(listenerObservedInterrupt),
                    null);

            pregenerator.start();

            assertFalse(listenerObservedInterrupt.get());
            assertFalse(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            IrisSettings.settings = previousSettings;
        }
    }

    private String runCompletionOnClose(boolean failLast) {
        IrisSettings previousSettings = IrisSettings.settings;
        PrintStream previousOut = System.out;
        IrisSettings.settings = new IrisSettings();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        CompletionOnCloseMethod method = new CompletionOnCloseMethod(failLast);
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(1)
                .radiusZ(1)
                .build();
        try {
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener(), null);

            pregenerator.start();

            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(previousOut);
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void outerHeapReclaimReleasesServerChunksBeforeMantleAndSurvivesChunkFailure() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        try {
            for (boolean failReclaim : new boolean[]{false, true}) {
                assertHeapReclaim(failReclaim);
            }
        } finally {
            IrisSettings.settings = previousSettings;
        }
    }

    private static void assertHeapReclaim(boolean failReclaim) {
        PregeneratorMethod generator = mock(PregeneratorMethod.class);
        Mantle mantle = mock(Mantle.class);
        when(generator.getMantle()).thenReturn(mantle);
        when(generator.getMethod(anyInt(), anyInt())).thenReturn("test");
        IllegalStateException failure = new IllegalStateException("chunk reclaim failed");
        if (failReclaim) {
            doThrow(failure).when(generator).reclaimMemory();
        }
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(1)
                .radiusZ(1)
                .build();
        IrisPregenerator pregenerator = new IrisPregenerator(task, generator, new NoOpPregenListener(), null);
        try (MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            pressure.when(MantleHeapPressure::overHighWater).thenReturn(true, false);
            pregenerator.start();
            InOrder order = inOrder(generator, mantle);
            order.verify(generator).reclaimMemory();
            order.verify(generator).getMantle();
            order.verify(mantle).saveOldestIdleTectonicPlate();
            pressure.verify(MantleHeapPressure::requestPanicReclaim);
            if (failReclaim) {
                logging.verify(() -> IrisLogging.reportError(
                        "Pregen could not reclaim completed server chunks under memory pressure.", failure));
            }
        }
    }

    private static void assertAnnouncedOnceBetweenRegions(TrackingPregeneratorMethod method, List<String> pending) {
        List<String> announced = new ArrayList<>();
        for (List<String> batch : method.announced) {
            announced.addAll(batch);
        }
        assertEquals(pending, announced);
        for (int index = 0; index < pending.size(); index++) {
            int announcement = method.lifecycle.indexOf("ahead:" + pending.get(index));
            assertTrue(pending.get(index) + " is announced before its chunks",
                    announcement >= 0 && announcement < method.lifecycle.indexOf("chunk:" + pending.get(index)));
            if (index > 0) {
                assertEquals(pending.get(index) + " is announced right after the first chunk of " + pending.get(index - 1),
                        method.lifecycle.indexOf("chunk:" + pending.get(index - 1)) + 1, announcement);
            }
        }
    }

    private static int firstIndex(List<String> lifecycle, String prefix) {
        for (int index = 0; index < lifecycle.size(); index++) {
            if (lifecycle.get(index).startsWith(prefix)) {
                return index;
            }
        }
        return -1;
    }

    private static Position2 position(String region) {
        String[] parts = region.split(",");
        return new Position2(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static final class TrackingPregeneratorMethod implements PregeneratorMethod {
        private final AtomicInteger initCalls = new AtomicInteger();
        private final List<String> lifecycle = Collections.synchronizedList(new ArrayList<>());
        private final List<List<String>> announced = new ArrayList<>();
        private final List<String> generated = new ArrayList<>();
        private final boolean interruptOnClose;
        private final Set<Position2> cachedRegions;

        private TrackingPregeneratorMethod() {
            this(false);
        }

        private TrackingPregeneratorMethod(boolean interruptOnClose) {
            this.interruptOnClose = interruptOnClose;
            this.cachedRegions = Set.of();
        }

        private TrackingPregeneratorMethod(Set<Position2> cachedRegions) {
            this.interruptOnClose = false;
            this.cachedRegions = cachedRegions;
        }

        @Override
        public void init() {
            initCalls.incrementAndGet();
            lifecycle.add("init");
        }

        @Override
        public void close() {
            if (interruptOnClose) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void save() {
            lifecycle.add("save");
        }

        @Override
        public boolean supportsRegions(int x, int z, PregenListener listener) {
            return cachedRegions.contains(new Position2(x, z));
        }

        @Override
        public String getMethod(int x, int z) {
            return "test";
        }

        @Override
        public void generateRegion(int x, int z, PregenListener listener) {
        }

        @Override
        public void generateChunk(int x, int z, PregenListener listener) {
            String region = (x >> 5) + "," + (z >> 5);
            lifecycle.add("chunk:" + region);
            if (generated.isEmpty() || !generated.getLast().equals(region)) {
                generated.add(region);
            }
        }

        @Override
        public void onRegionBounds(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
            lifecycle.add("bounds");
        }

        @Override
        public void onRegionsAhead(List<long[]> regions) {
            StringBuilder entry = new StringBuilder("ahead:");
            List<String> batch = new ArrayList<>();
            for (long[] region : regions) {
                entry.append(entry.length() > 6 ? ";" : "").append(region[0]).append(',').append(region[1]);
                batch.add(region[0] + "," + region[1]);
            }
            lifecycle.add(entry.toString());
            announced.add(batch);
        }

        @Override
        public Mantle getMantle() {
            return null;
        }
    }

    private static final class CompletionOnCloseMethod implements PregeneratorMethod {
        private final boolean failLast;
        private int pending;
        private PregenListener listener;

        private CompletionOnCloseMethod(boolean failLast) {
            this.failLast = failLast;
        }

        @Override
        public void init() {
        }

        @Override
        public void close() {
            for (int index = 0; index < pending; index++) {
                if (failLast && index == pending - 1) {
                    listener.onChunkFailed(0, 0);
                } else {
                    listener.onChunkGenerated(0, 0);
                }
            }
        }

        @Override
        public void save() {
        }

        @Override
        public boolean supportsRegions(int x, int z, PregenListener listener) {
            return false;
        }

        @Override
        public String getMethod(int x, int z) {
            return "test";
        }

        @Override
        public void generateRegion(int x, int z, PregenListener listener) {
        }

        @Override
        public void generateChunk(int x, int z, PregenListener listener) {
            pending++;
            this.listener = listener;
        }

        @Override
        public Mantle getMantle() {
            return null;
        }
    }

    private static final class CancelAfterFirstChunkMethod implements PregeneratorMethod {
        private final AtomicReference<IrisPregenerator> pregeneratorReference;
        private boolean cancelled;

        private CancelAfterFirstChunkMethod(AtomicReference<IrisPregenerator> pregeneratorReference) {
            this.pregeneratorReference = pregeneratorReference;
        }

        @Override
        public void init() {
        }

        @Override
        public void close() {
        }

        @Override
        public void save() {
        }

        @Override
        public boolean supportsRegions(int x, int z, PregenListener listener) {
            return false;
        }

        @Override
        public String getMethod(int x, int z) {
            return "test";
        }

        @Override
        public void generateRegion(int x, int z, PregenListener listener) {
        }

        @Override
        public void generateChunk(int x, int z, PregenListener listener) {
            listener.onChunkGenerated(x, z);
            if (!cancelled) {
                cancelled = true;
                pregeneratorReference.get().close();
            }
        }

        @Override
        public Mantle getMantle() {
            return null;
        }
    }

    private static final class NoOpPregenListener implements PregenListener {
        private final AtomicBoolean closeInterrupted;

        private NoOpPregenListener() {
            this(null);
        }

        private NoOpPregenListener(AtomicBoolean closeInterrupted) {
            this.closeInterrupted = closeInterrupted;
        }

        @Override
        public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, long generated, long totalChunks, long chunksRemaining, long eta, long elapsed, String method, boolean cached) {
        }

        @Override
        public void onChunkGenerating(int x, int z) {
        }

        @Override
        public void onChunkGenerated(int x, int z, boolean cached) {
        }

        @Override
        public void onRegionGenerated(int x, int z) {
        }

        @Override
        public void onRegionGenerating(int x, int z) {
        }

        @Override
        public void onChunkCleaned(int x, int z) {
        }

        @Override
        public void onRegionSkipped(int x, int z) {
        }

        @Override
        public void onNetworkStarted(int x, int z) {
        }

        @Override
        public void onNetworkFailed(int x, int z) {
        }

        @Override
        public void onNetworkReclaim(int revert) {
        }

        @Override
        public void onNetworkGeneratedChunk(int x, int z) {
        }

        @Override
        public void onNetworkDownloaded(int x, int z) {
        }

        @Override
        public void onClose() {
            if (closeInterrupted != null) {
                closeInterrupted.set(Thread.currentThread().isInterrupted());
            }
        }

        @Override
        public void onSaving() {
        }

        @Override
        public void onChunkExistsInRegionGen(int x, int z) {
        }
    }
}
