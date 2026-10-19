/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.gui;

import com.volmit.iris.util.function.NoiseProvider;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class NoiseRenderCoordinator implements AutoCloseable {
    private static final int BANDS_PER_WORKER = 4;

    private final int workerCount;
    private final Listener listener;
    private final ThreadPoolExecutor workers;
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
        ThreadFactory workerThreadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Iris Noise Renderer " + threadIds.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
        workers = new ThreadPoolExecutor(this.workerCount, this.workerCount, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), workerThreadFactory);
        workers.allowCoreThreadTimeOut(true);
        GuiHost.interruptOnPluginShutdown(workers);
    }

    void request(Request request) {
        Objects.requireNonNull(request, "request");
        if (closed.get()) {
            return;
        }
        latestRevision.accumulateAndGet(request.revision(), Math::max);
        RenderGeneration generation = new RenderGeneration(request, Math.min(workerCount * BANDS_PER_WORKER, outputHeight(request)));
        latestRequested.set(generation);
        listener.onRenderStarted(request);
        for (int band = 0; band < generation.bandCount(); band++) {
            int bandIndex = band;
            try {
                workers.execute(() -> renderBand(generation, bandIndex));
            } catch (RejectedExecutionException rejected) {
                generation.fail(rejected);
                finishBand(generation);
            }
        }
    }

    void cancel(long revision) {
        latestRevision.accumulateAndGet(revision, Math::max);
        latestRequested.set(null);
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
        latestRequested.set(null);
        workers.shutdownNow();
    }

    // Bands of a frame that was replaced are skipped, so a new frame only waits for the rows already being drawn.
    private void renderBand(RenderGeneration generation, int bandIndex) {
        try {
            if (isCurrent(generation)) {
                renderRows(generation, bandIndex);
            }
        } catch (Throwable error) {
            generation.fail(error);
        } finally {
            finishBand(generation);
        }
    }

    private void finishBand(RenderGeneration generation) {
        if (!generation.completeBand() || !isCurrent(generation)) {
            return;
        }
        Throwable failure = generation.failure();
        if (failure != null) {
            listener.onRenderFailed(generation.request(), failure);
            return;
        }
        listener.onRenderCompleted(generation.result());
    }

    private void renderRows(RenderGeneration generation, int bandIndex) {
        Request request = generation.request();
        int outputWidth = generation.outputWidth();
        int outputHeight = generation.outputHeight();
        int[] pixels = generation.pixels();
        BandStats stats = generation.bandStats(bandIndex);
        int fromY = (outputHeight * bandIndex) / generation.bandCount();
        int toY = (outputHeight * (bandIndex + 1)) / generation.bandCount();
        double sampleOffset = request.sampleStep() * 0.5D;
        double startWorldX = request.viewport().worldX(sampleOffset, request.width());
        double worldStep = request.viewport().blocksPerPixel() * request.sampleStep();
        NoiseColorScale colorScale = request.colorScale();
        double paletteMinimum = colorScale.minimum();
        double paletteMaximum = colorScale.maximum();
        for (int y = fromY; y < toY; y++) {
            double screenY = (y * (double) request.sampleStep()) + sampleOffset;
            double worldZ = request.viewport().worldZ(screenY, request.height());
            double worldX = startWorldX;
            int pixelIndex = y * outputWidth;
            boolean centerRow = y == outputHeight / 2;
            for (int x = 0; x < outputWidth; x++) {
                if ((x & 31) == 0 && (!isCurrent(generation) || Thread.currentThread().isInterrupted())) {
                    return;
                }
                double value = request.sampler().noise(worldX, worldZ);
                boolean center = centerRow && x == outputWidth / 2;
                if (Double.isFinite(value)) {
                    pixels[pixelIndex++] = colorScale.colorAt(value, worldX, worldZ);
                    stats.acceptFinite(value, center, paletteMinimum, paletteMaximum);
                } else {
                    pixels[pixelIndex++] = colorScale.colorInvalid();
                    stats.acceptInvalid(value, center);
                }
                worldX += worldStep;
            }
        }
    }

    private boolean isCurrent(RenderGeneration generation) {
        return !closed.get()
                && latestRequested.get() == generation
                && generation.request().revision() >= latestRevision.get();
    }

    private static int outputWidth(Request request) {
        return Math.max(1, (request.width() + request.sampleStep() - 1) / request.sampleStep());
    }

    private static int outputHeight(Request request) {
        return Math.max(1, (request.height() + request.sampleStep() - 1) / request.sampleStep());
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

    record Request(long revision, NoiseProvider sampler, NoiseViewport viewport, NoiseColorScale colorScale,
                   int width, int height, int sampleStep) {
        Request {
            Objects.requireNonNull(sampler, "sampler");
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(colorScale, "colorScale");
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
        private final long startedNanos = System.nanoTime();
        private final int outputWidth;
        private final int outputHeight;
        private final int[] pixels;
        private final BandStats[] bandStats;
        private final AtomicInteger remainingBands;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private RenderGeneration(Request request, int bandCount) {
            this.request = request;
            this.outputWidth = NoiseRenderCoordinator.outputWidth(request);
            this.outputHeight = NoiseRenderCoordinator.outputHeight(request);
            this.pixels = new int[outputWidth * outputHeight];
            this.bandStats = new BandStats[Math.max(1, bandCount)];
            for (int band = 0; band < bandStats.length; band++) {
                bandStats[band] = new BandStats();
            }
            this.remainingBands = new AtomicInteger(bandStats.length);
        }

        private Request request() {
            return request;
        }

        private int bandCount() {
            return bandStats.length;
        }

        private int outputWidth() {
            return outputWidth;
        }

        private int outputHeight() {
            return outputHeight;
        }

        private int[] pixels() {
            return pixels;
        }

        private BandStats bandStats(int band) {
            return bandStats[band];
        }

        private void fail(Throwable error) {
            failure.compareAndSet(null, error);
        }

        private Throwable failure() {
            return failure.get();
        }

        // True for the band that finishes last, which is the one that publishes the frame.
        private boolean completeBand() {
            return remainingBands.decrementAndGet() == 0;
        }

        private Result result() {
            BufferedImage image = GuiHost.uncachedImage(pixels, outputWidth, outputHeight);
            RenderStats combined = combine(bandStats);
            double milliseconds = (System.nanoTime() - startedNanos) / 1_000_000D;
            return new Result(request, image, milliseconds, combined.samples, combined.minimum, combined.maximum,
                    combined.centerValue, combined.underflow, combined.overflow, combined.invalid);
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
