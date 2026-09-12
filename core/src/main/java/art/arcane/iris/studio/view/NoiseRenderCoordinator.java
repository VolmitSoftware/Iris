package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.function.NoiseProvider;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class NoiseRenderCoordinator implements AutoCloseable {
    private final int workerCount;
    private final Listener listener;
    private final ThreadFactory workerThreadFactory;
    private final ThreadFactory coordinatorThreadFactory;
    private final AtomicReference<RenderGeneration> latestRequested = new AtomicReference<>();
    private final AtomicLong latestRevision = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    NoiseRenderCoordinator(Listener listener) {
        this(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)), listener);
    }

    NoiseRenderCoordinator(int workerCount, Listener listener) {
        this.workerCount = Math.max(1, workerCount);
        this.listener = Objects.requireNonNull(listener, "listener");
        AtomicInteger threadIds = new AtomicInteger();
        workerThreadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Iris Noise Renderer " + threadIds.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
        coordinatorThreadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Iris Noise Coordinator " + threadIds.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    void request(Request request) {
        Objects.requireNonNull(request, "request");
        if (closed.get()) {
            return;
        }
        latestRevision.accumulateAndGet(request.revision(), Math::max);
        cancelActiveRender();
        RenderGeneration generation = createGeneration(request);
        latestRequested.set(generation);
        generation.start();
    }

    void cancel(long revision) {
        latestRevision.accumulateAndGet(revision, Math::max);
        cancelActiveRender();
    }

    boolean isClosed() {
        return closed.get();
    }

    static int sampleStepForBudget(int width, int height, long sampleBudget) {
        if (width < 1 || height < 1 || sampleBudget < 1L) {
            throw new IllegalArgumentException("Invalid noise sample budget");
        }
        long totalSamples = (long) width * height;
        int sampleStep = Math.max(1, (int) Math.ceil(Math.sqrt(totalSamples / (double) sampleBudget)));
        while (sampleCount(width, height, sampleStep) > sampleBudget) {
            sampleStep++;
        }
        return sampleStep;
    }

    static long sampleCount(int width, int height, int sampleStep) {
        if (width < 1 || height < 1 || sampleStep < 1) {
            throw new IllegalArgumentException("Invalid noise sample dimensions");
        }
        long outputWidth = ((long) width + sampleStep - 1L) / sampleStep;
        long outputHeight = ((long) height + sampleStep - 1L) / sampleStep;
        return outputWidth * outputHeight;
    }

    static int nextRefinementStep(int width, int height, int currentStep, long sampleBudget) {
        if (currentStep <= 1) {
            throw new IllegalArgumentException("Noise refinement requires a coarse input");
        }
        return Math.min(currentStep, sampleStepForBudget(width, height, sampleBudget));
    }

    static long timeBoundSampleBudget(long completedSamples, double milliseconds, double targetMilliseconds,
                                      long minimumBudget, long maximumBudget) {
        if (completedSamples < 1L
                || !Double.isFinite(milliseconds)
                || milliseconds <= 0D
                || !Double.isFinite(targetMilliseconds)
                || targetMilliseconds <= 0D
                || minimumBudget < 1L
                || maximumBudget < minimumBudget) {
            throw new IllegalArgumentException("Invalid timed noise sample budget");
        }
        double projectedSamples = Math.ceil((completedSamples / milliseconds) * targetMilliseconds);
        long timeBoundBudget = (long) Math.min(maximumBudget, projectedSamples);
        return Math.max(minimumBudget, timeBoundBudget);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        latestRevision.incrementAndGet();
        cancelActiveRender();
    }

    private void renderGeneration(RenderGeneration generation) {
        Request request = generation.request();
        if (!isCurrent(request)) {
            generation.close();
            return;
        }
        listener.onRenderStarted(request);
        try {
            Result result = render(generation);
            if (result != null && isCurrent(request)) {
                listener.onRenderCompleted(result);
            }
        } catch (Throwable error) {
            if (isCurrent(request)) {
                listener.onRenderFailed(request, error);
            }
        } finally {
            latestRequested.compareAndSet(generation, null);
            generation.close();
        }
    }

    private Result render(RenderGeneration generation) throws InterruptedException {
        Request request = generation.request();
        long started = System.nanoTime();
        int outputWidth = Math.max(1, (request.width() + request.sampleStep() - 1) / request.sampleStep());
        int outputHeight = Math.max(1, (request.height() + request.sampleStep() - 1) / request.sampleStep());
        BufferedImage image = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int bandCount = Math.min(workerCount, outputHeight);
        BandStats[] bandStats = new BandStats[bandCount];
        CountDownLatch completion = new CountDownLatch(bandCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int band = 0; band < bandCount; band++) {
            bandStats[band] = new BandStats();
        }
        for (int band = 0; band < bandCount; band++) {
            int bandIndex = band;
            Runnable task = () -> {
                try {
                    renderBand(request, image, pixels, bandIndex, bandCount, bandStats[bandIndex]);
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    completion.countDown();
                }
            };
            try {
                generation.executor().execute(task);
            } catch (RejectedExecutionException exception) {
                failure.compareAndSet(null, exception);
                completion.countDown();
            }
        }
        completion.await();
        Throwable renderFailure = failure.get();
        if (renderFailure != null) {
            if (renderFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (renderFailure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Noise rendering failed", renderFailure);
        }
        if (!isCurrent(request)) {
            return null;
        }
        RenderStats combined = combine(bandStats);
        double milliseconds = (System.nanoTime() - started) / 1_000_000D;
        return new Result(request, image, milliseconds, combined.samples, combined.minimum, combined.maximum,
                combined.centerValue, combined.underflow, combined.overflow, combined.invalid);
    }

    private void renderBand(Request request, BufferedImage image, int[] pixels, int bandIndex, int bandCount,
                            BandStats stats) {
        int outputWidth = image.getWidth();
        int outputHeight = image.getHeight();
        int fromY = (outputHeight * bandIndex) / bandCount;
        int toY = (outputHeight * (bandIndex + 1)) / bandCount;
        double sampleOffset = request.sampleStep() * 0.5D;
        double startWorldX = request.viewport().worldX(sampleOffset, request.width());
        double worldStep = request.viewport().blocksPerPixel() * request.sampleStep();
        NoisePalette palette = request.palette();
        double paletteMinimum = palette.minimum();
        double paletteMaximum = palette.maximum();
        for (int y = fromY; y < toY; y++) {
            double screenY = (y * (double) request.sampleStep()) + sampleOffset;
            double worldZ = request.viewport().worldZ(screenY, request.height());
            double worldX = startWorldX;
            int pixelIndex = y * outputWidth;
            boolean centerRow = y == outputHeight / 2;
            for (int x = 0; x < outputWidth; x++) {
                if ((x & 31) == 0 && (!isCurrent(request) || Thread.currentThread().isInterrupted())) {
                    return;
                }
                double value = request.sampler().noise(worldX, worldZ);
                boolean center = centerRow && x == outputWidth / 2;
                if (Double.isFinite(value)) {
                    pixels[pixelIndex++] = palette.colorFinite(value);
                    stats.acceptFinite(value, center, paletteMinimum, paletteMaximum);
                } else {
                    pixels[pixelIndex++] = NoisePalette.INVALID_COLOR;
                    stats.acceptInvalid(value, center);
                }
                worldX += worldStep;
            }
        }
    }

    private boolean isCurrent(Request request) {
        return !closed.get()
                && latestRequested.get() != null
                && latestRequested.get().request() == request
                && request.revision() >= latestRevision.get();
    }

    private void cancelActiveRender() {
        RenderGeneration generation = latestRequested.getAndSet(null);
        if (generation != null) {
            generation.close();
        }
    }

    private RenderGeneration createGeneration(Request request) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, workerCount)),
                workerThreadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(executor);
        }
        RenderGeneration generation = new RenderGeneration(request, executor);
        Thread actualRunner = coordinatorThreadFactory.newThread(() -> renderGeneration(generation));
        generation.setRunner(actualRunner);
        if (preservation != null) {
            preservation.register(actualRunner);
        }
        return generation;
    }

    private static RenderStats combine(BandStats[] bands) {
        long samples = 0L;
        long underflow = 0L;
        long overflow = 0L;
        long invalid = 0L;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double centerValue = Double.NaN;
        for (BandStats band : bands) {
            samples += band.samples;
            underflow += band.underflow;
            overflow += band.overflow;
            invalid += band.invalid;
            minimum = Math.min(minimum, band.minimum);
            maximum = Math.max(maximum, band.maximum);
            if (!Double.isNaN(band.centerValue)) {
                centerValue = band.centerValue;
            }
        }
        if (minimum == Double.POSITIVE_INFINITY) {
            minimum = Double.NaN;
            maximum = Double.NaN;
        }
        return new RenderStats(samples, minimum, maximum, centerValue, underflow, overflow, invalid);
    }

    interface Listener {
        void onRenderStarted(Request request);

        void onRenderCompleted(Result result);

        void onRenderFailed(Request request, Throwable error);
    }

    record Request(long revision, NoiseProvider sampler, NoiseViewport viewport, NoisePalette palette,
                   int width, int height, int sampleStep) {
        Request {
            Objects.requireNonNull(sampler, "sampler");
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(palette, "palette");
            if (revision < 0L || width < 1 || height < 1 || sampleStep < 1) {
                throw new IllegalArgumentException("Invalid noise render request");
            }
        }
    }

    record Result(Request request, BufferedImage image, double milliseconds, long samples, double minimum,
                  double maximum, double centerValue, long underflow, long overflow, long invalid) {
    }

    private static final class RenderGeneration {
        private final Request request;
        private final ThreadPoolExecutor executor;
        private volatile Thread runner;

        private RenderGeneration(Request request, ThreadPoolExecutor executor) {
            this.request = Objects.requireNonNull(request, "request");
            this.executor = Objects.requireNonNull(executor, "executor");
        }

        private Request request() {
            return request;
        }

        private ThreadPoolExecutor executor() {
            return executor;
        }

        private void setRunner(Thread runner) {
            this.runner = Objects.requireNonNull(runner, "runner");
        }

        private void start() {
            runner.start();
        }

        private void close() {
            Thread activeRunner = runner;
            if (activeRunner != null) {
                activeRunner.interrupt();
            }
            executor.shutdownNow();
        }
    }

    private static final class BandStats {
        private long samples;
        private long underflow;
        private long overflow;
        private long invalid;
        private double minimum = Double.POSITIVE_INFINITY;
        private double maximum = Double.NEGATIVE_INFINITY;
        private double centerValue = Double.NaN;

        private void acceptFinite(double value, boolean center, double paletteMinimum, double paletteMaximum) {
            samples++;
            if (center) {
                centerValue = value;
            }
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            if (value < paletteMinimum) {
                underflow++;
            } else if (value > paletteMaximum) {
                overflow++;
            }
        }

        private void acceptInvalid(double value, boolean center) {
            samples++;
            invalid++;
            if (center) {
                centerValue = value;
            }
        }
    }

    private record RenderStats(long samples, double minimum, double maximum, double centerValue,
                               long underflow, long overflow, long invalid) {
    }
}
