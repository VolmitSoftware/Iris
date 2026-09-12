package art.arcane.iris.world.pregen;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisPregeneratorInitTest {
    @Test
    public void initDoesNotSaveBeforeGenerationStarts() throws Exception {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        TrackingPregeneratorMethod method = new TrackingPregeneratorMethod();
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(16)
                .radiusZ(16)
                .build();
        try {
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener());
            Method initMethod = IrisPregenerator.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);

            initMethod.invoke(pregenerator);

            assertEquals(1, method.initCalls.get());
            assertEquals(0, method.saveCalls.get());
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
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener());

            pregenerator.start();

            int boundsIndex = method.lifecycle.indexOf("bounds");
            int startIndex = method.lifecycle.indexOf("start:128,128");
            int chunkIndex = method.lifecycle.indexOf("chunk");
            assertTrue(boundsIndex >= 0);
            assertTrue(startIndex > boundsIndex);
            assertTrue(chunkIndex > startIndex);
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
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener());
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
                    new NoOpPregenListener(listenerObservedInterrupt));

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
            IrisPregenerator pregenerator = new IrisPregenerator(task, method, new NoOpPregenListener());

            pregenerator.start();

            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(previousOut);
            IrisSettings.settings = previousSettings;
        }
    }

    private static final class TrackingPregeneratorMethod implements PregeneratorMethod {
        private final AtomicInteger initCalls = new AtomicInteger();
        private final AtomicInteger saveCalls = new AtomicInteger();
        private final List<String> lifecycle = new ArrayList<>();
        private final boolean interruptOnClose;

        private TrackingPregeneratorMethod() {
            this(false);
        }

        private TrackingPregeneratorMethod(boolean interruptOnClose) {
            this.interruptOnClose = interruptOnClose;
        }

        @Override
        public void init() {
            initCalls.incrementAndGet();
        }

        @Override
        public void close() {
            if (interruptOnClose) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void save() {
            saveCalls.incrementAndGet();
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
            lifecycle.add("chunk");
        }

        @Override
        public void onRegionBounds(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
            lifecycle.add("bounds");
        }

        @Override
        public void onPregenStart(int centerBlockX, int centerBlockZ) {
            lifecycle.add("start:" + centerBlockX + "," + centerBlockZ);
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
