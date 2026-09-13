/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.studio.render.IrisRenderer;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class VisionRenderController implements AutoCloseable {
    static final int TILE_PIXELS = 64;
    static final double MINIMUM_BLOCKS_PER_PIXEL = 1D;
    static final double MAXIMUM_BLOCKS_PER_PIXEL = 4_096D;

    private static final int MAXIMUM_WORKERS = 3;
    private static final int MAXIMUM_VISIBLE_TILES = 32_768;
    private static final int RENDER_QUEUE_CAPACITY = 12;
    private static final int PROBE_QUEUE_CAPACITY = 1;
    private static final long CACHE_BYTES = 64L * 1024L * 1024L;

    private final Runnable listener;
    private final int renderWorkerCount;
    private final ThreadPoolExecutor renderExecutor;
    private final ThreadPoolExecutor probeExecutor;
    private final WeightedTileCache cache;
    private final AtomicLong viewSequence;
    private final AtomicLong probeSequence;
    private final AtomicBoolean closed;
    private final AtomicBoolean publicationQueued;
    private final AtomicBoolean publicationDirty;
    private final Set<Future<?>> activeRenderTasks;
    private volatile Frame currentFrame;
    private volatile WorkState currentWork;
    private volatile CancellationToken currentToken;

    VisionRenderController(Runnable listener) {
        this(listener, RuntimeOptions.production());
    }

    VisionRenderController(Runnable listener, RuntimeOptions options) {
        this.listener = Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(options, "options");
        AtomicInteger threadSequence = new AtomicInteger();
        this.renderWorkerCount = options.workers();
        this.renderExecutor = new ThreadPoolExecutor(
                options.workers(),
                options.workers(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(options.renderQueueCapacity()),
                daemonFactory("Iris Vision Render", Thread.NORM_PRIORITY, threadSequence),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.probeExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(PROBE_QUEUE_CAPACITY),
                daemonFactory("Iris Vision Probe", Thread.MIN_PRIORITY, threadSequence),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        this.cache = new WeightedTileCache(CACHE_BYTES);
        this.viewSequence = new AtomicLong();
        this.probeSequence = new AtomicLong();
        this.closed = new AtomicBoolean();
        this.publicationQueued = new AtomicBoolean();
        this.publicationDirty = new AtomicBoolean();
        this.activeRenderTasks = ConcurrentHashMap.newKeySet();
        if (options.registerPreservation()) {
            PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
            if (preservation != null) {
                preservation.register(renderExecutor);
                preservation.register(probeExecutor);
            }
        }
    }

    synchronized Frame request(RenderSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (closed.get()) {
            throw new IllegalStateException("Vision render controller is closed");
        }

        CancellationToken previousToken = currentToken;
        if (previousToken != null) {
            previousToken.cancel();
        }
        renderExecutor.getQueue().clear();
        cancelActiveRenderTasks();
        probeSequence.incrementAndGet();
        probeExecutor.getQueue().clear();

        List<VisibleTile> tiles = visibleTiles(spec);
        Frame frame = new Frame(viewSequence.incrementAndGet(), spec, tiles);
        for (VisibleTile tile : tiles) {
            tile.setResult(cache.get(frame.key(tile)));
        }
        CancellationToken token = new CancellationToken();
        WorkState work = new WorkState(frame, token);
        currentFrame = frame;
        currentToken = token;
        currentWork = work;
        schedule(work);
        publish(frame, token);
        return frame;
    }

    Frame currentFrame() {
        return currentFrame;
    }

    BufferedImage image(Frame frame, VisibleTile tile) {
        if (frame == null || tile == null) {
            return null;
        }
        return tile.image();
    }

    Progress progress(Frame frame) {
        if (frame == null) {
            return new Progress(0, 0, 0, 0);
        }
        int ready = 0;
        for (VisibleTile tile : frame.tiles()) {
            if (tile.complete()) {
                ready++;
            }
        }
        return new Progress(frame.tiles().size(), ready, renderExecutor.getActiveCount(), renderExecutor.getQueue().size());
    }

    <T> void submitProbe(Frame frame, Callable<T> probe, Consumer<T> consumer) {
        Objects.requireNonNull(probe, "probe");
        Objects.requireNonNull(consumer, "consumer");
        if (frame == null || closed.get() || currentFrame != frame) {
            return;
        }
        long probeRevision = probeSequence.incrementAndGet();
        probeExecutor.getQueue().clear();
        try {
            probeExecutor.execute(() -> runProbe(frame, probeRevision, probe, consumer));
        } catch (RejectedExecutionException ignored) {
            probeExecutor.getQueue().clear();
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        CancellationToken token = currentToken;
        if (token != null) {
            token.cancel();
        }
        currentWork = null;
        currentFrame = null;
        renderExecutor.getQueue().clear();
        cancelActiveRenderTasks();
        probeExecutor.getQueue().clear();
        renderExecutor.shutdownNow();
        probeExecutor.shutdownNow();
        cache.clear();
    }

    static List<VisibleTile> visibleTiles(RenderSpec spec) {
        Objects.requireNonNull(spec, "spec");
        double blocksPerPixel = spec.blocksPerPixel();
        double tileSpan = TILE_PIXELS * blocksPerPixel;
        double halfWidth = spec.width() * blocksPerPixel * 0.5D;
        double halfHeight = spec.height() * blocksPerPixel * 0.5D;
        long minimumX = floorTile(spec.centerX() - halfWidth, tileSpan);
        long maximumX = floorTile(Math.nextDown(spec.centerX() + halfWidth), tileSpan);
        long minimumZ = floorTile(spec.centerZ() - halfHeight, tileSpan);
        long maximumZ = floorTile(Math.nextDown(spec.centerZ() + halfHeight), tileSpan);
        long tileCount = Math.multiplyExact(maximumX - minimumX + 1L, maximumZ - minimumZ + 1L);
        if (tileCount > MAXIMUM_VISIBLE_TILES) {
            throw new IllegalArgumentException("Vision viewport contains too many tiles");
        }

        ArrayList<VisibleTile> tiles = new ArrayList<>((int) tileCount);
        double centerTileX = spec.centerX() / tileSpan;
        double centerTileZ = spec.centerZ() / tileSpan;
        for (long tileZ = minimumZ; ; tileZ++) {
            for (long tileX = minimumX; ; tileX++) {
                int screenX = (int) Math.round(spec.width() * 0.5D + (tileX * tileSpan - spec.centerX()) / blocksPerPixel);
                int screenY = (int) Math.round(spec.height() * 0.5D + (tileZ * tileSpan - spec.centerZ()) / blocksPerPixel);
                double deltaX = tileX + 0.5D - centerTileX;
                double deltaZ = tileZ + 0.5D - centerTileZ;
                tiles.add(new VisibleTile(tileX, tileZ, screenX, screenY, deltaX * deltaX + deltaZ * deltaZ));
                if (tileX == maximumX) {
                    break;
                }
            }
            if (tileZ == maximumZ) {
                break;
            }
        }
        tiles.sort(Comparator.comparingDouble(VisibleTile::distanceSquared)
                .thenComparingLong(VisibleTile::tileZ)
                .thenComparingLong(VisibleTile::tileX));
        return List.copyOf(tiles);
    }

    static long sampleCount(int tileCount) {
        if (tileCount < 1) {
            throw new IllegalArgumentException("Vision tile count must be positive");
        }
        return Math.multiplyExact((long) tileCount, (long) TILE_PIXELS * TILE_PIXELS);
    }

    private static long floorTile(double coordinate, double tileSpan) {
        double tile = Math.floor(coordinate / tileSpan);
        if (tile < Long.MIN_VALUE || tile > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Vision viewport exceeds tile coordinate range");
        }
        return (long) tile;
    }

    private static ThreadFactory daemonFactory(String name, int priority, AtomicInteger sequence) {
        return (Runnable runnable) -> {
            Thread thread = new Thread(runnable, name + " " + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(priority);
            thread.setUncaughtExceptionHandler((Thread failedThread, Throwable error) -> IrisLogging.reportError(error));
            return thread;
        };
    }

    private void schedule(WorkState work) {
        synchronized (work) {
            if (!isCurrent(work)) {
                return;
            }
            int admissionLimit = work.refining() || work.frame().spec().type() == RenderType.RIVER ? 1 : renderWorkerCount;
            while (work.inFlight() < admissionLimit) {
                VisibleTile tile = work.nextMissing();
                if (tile == null) {
                    if (work.inFlight() == 0 && work.beginRefinement()) {
                        admissionLimit = 1;
                        continue;
                    }
                    return;
                }
                boolean refining = work.refining();
                work.incrementInFlight();
                TrackedRenderTask task = new TrackedRenderTask(() -> render(work, tile, refining), activeRenderTasks);
                activeRenderTasks.add(task);
                try {
                    renderExecutor.execute(task);
                } catch (RejectedExecutionException ignored) {
                    task.cancel(false);
                    work.decrementInFlight();
                    return;
                }
            }
        }
    }

    private void render(WorkState work, VisibleTile tile, boolean refining) {
        Frame frame = work.frame();
        CancellationToken token = work.token();
        try {
            if (!isCurrent(frame, token)) {
                return;
            }
            double tileSpan = TILE_PIXELS * frame.spec().blocksPerPixel();
            double startX = tile.tileX() * tileSpan;
            double startZ = tile.tileZ() * tileSpan;
            IrisRenderer renderer = frame.spec().renderer();
            BufferedImage image;
            if (refining) {
                image = renderer.refineStudioBiome(startX, startZ, tileSpan, tile.image(),
                        () -> !isCurrent(frame, token));
            } else if (frame.spec().type() == RenderType.BIOME) {
                image = renderer.renderStudioBase(startX, startZ, tileSpan, TILE_PIXELS, frame.spec().type(),
                        () -> !isCurrent(frame, token));
            } else {
                image = renderer.renderStudio(startX, startZ, tileSpan, TILE_PIXELS, frame.spec().type(),
                        () -> !isCurrent(frame, token));
            }
            if (!isCurrent(frame, token)) {
                return;
            }
            TileResult result = new TileResult(image, refining || frame.spec().type() != RenderType.BIOME);
            cache.put(frame.key(tile), result);
            tile.setResult(result);
            publish(frame, token);
        } catch (CancellationException ignored) {
        } catch (Throwable error) {
            IrisLogging.debug("Vision tile render failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            complete(work);
        }
    }

    private void complete(WorkState work) {
        synchronized (work) {
            work.decrementInFlight();
            if (!isCurrent(work)) {
                return;
            }
        }
        schedule(work);
    }

    private <T> void runProbe(Frame frame, long probeRevision, Callable<T> probe, Consumer<T> consumer) {
        try {
            if (!isProbeCurrent(frame, probeRevision)) {
                return;
            }
            T result = probe.call();
            if (!isProbeCurrent(frame, probeRevision)) {
                return;
            }
            EventQueue.invokeLater(() -> {
                if (isProbeCurrent(frame, probeRevision)) {
                    consumer.accept(result);
                }
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            IrisLogging.debug("Vision probe failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private boolean isProbeCurrent(Frame frame, long probeRevision) {
        return !closed.get() && currentFrame == frame && probeSequence.get() == probeRevision;
    }

    private boolean isCurrent(Frame frame, CancellationToken token) {
        return !closed.get() && !token.cancelled() && currentFrame == frame;
    }

    private boolean isCurrent(WorkState work) {
        return currentWork == work && isCurrent(work.frame(), work.token());
    }

    private void publish(Frame frame, CancellationToken token) {
        if (!isCurrent(frame, token)) {
            return;
        }
        publicationDirty.set(true);
        queuePublication();
    }

    private void queuePublication() {
        if (!publicationQueued.compareAndSet(false, true)) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                if (publicationDirty.getAndSet(false) && !closed.get()) {
                    listener.run();
                }
            } finally {
                publicationQueued.set(false);
                if (publicationDirty.get() && !closed.get()) {
                    queuePublication();
                }
            }
        });
    }

    private void cancelActiveRenderTasks() {
        for (Future<?> task : activeRenderTasks) {
            task.cancel(true);
        }
        activeRenderTasks.clear();
    }

    record RenderSpec(
            IrisRenderer renderer,
            RenderType type,
            long contentRevision,
            double centerX,
            double centerZ,
            double blocksPerPixel,
            int width,
            int height
    ) {
        RenderSpec {
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(type, "type");
            if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
                throw new IllegalArgumentException("Vision center must be finite");
            }
            if (!Double.isFinite(blocksPerPixel)
                    || blocksPerPixel < MINIMUM_BLOCKS_PER_PIXEL
                    || blocksPerPixel > MAXIMUM_BLOCKS_PER_PIXEL) {
                throw new IllegalArgumentException("Vision scale is outside the supported range");
            }
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Vision viewport must be positive");
            }
        }

    }

    record Frame(long viewRevision, RenderSpec spec, List<VisibleTile> tiles) {
        Frame {
            Objects.requireNonNull(spec, "spec");
            tiles = List.copyOf(tiles);
        }

        TileKey key(VisibleTile tile) {
            return new TileKey(spec.contentRevision(), spec.type(), spec.blocksPerPixel(), tile.tileX(), tile.tileZ());
        }
    }

    static final class VisibleTile {
        private final long tileX;
        private final long tileZ;
        private final int screenX;
        private final int screenY;
        private final double distanceSquared;
        private volatile TileResult result;

        private VisibleTile(long tileX, long tileZ, int screenX, int screenY, double distanceSquared) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.screenX = screenX;
            this.screenY = screenY;
            this.distanceSquared = distanceSquared;
        }

        long tileX() {
            return tileX;
        }

        long tileZ() {
            return tileZ;
        }

        int screenX() {
            return screenX;
        }

        int screenY() {
            return screenY;
        }

        double distanceSquared() {
            return distanceSquared;
        }

        BufferedImage image() {
            TileResult current = result;
            return current == null ? null : current.image();
        }

        boolean complete() {
            TileResult current = result;
            return current != null && current.complete();
        }

        void setResult(TileResult result) {
            this.result = result;
        }
    }

    record TileKey(long contentRevision, RenderType type, double blocksPerPixel, long tileX, long tileZ) {
        TileKey {
            Objects.requireNonNull(type, "type");
            if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
                throw new IllegalArgumentException("Vision cache scale must be finite and positive");
            }
        }
    }

    record Progress(int total, int ready, int active, int queued) {
        double completion() {
            if (total == 0) {
                return 1D;
            }
            return Math.min(1D, ready / (double) total);
        }
    }

    record RuntimeOptions(int workers, int renderQueueCapacity, boolean registerPreservation) {
        RuntimeOptions {
            if (workers < 1 || renderQueueCapacity < 1) {
                throw new IllegalArgumentException("Vision render runtime options are invalid");
            }
        }

        static RuntimeOptions production() {
            int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
            int workers = Math.max(1, Math.min(MAXIMUM_WORKERS, processors));
            return new RuntimeOptions(workers, RENDER_QUEUE_CAPACITY, true);
        }
    }

    private static final class CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        void cancel() {
            cancelled.set(true);
        }

        boolean cancelled() {
            return cancelled.get();
        }
    }

    private static final class TrackedRenderTask extends FutureTask<Void> {
        private final Set<Future<?>> tasks;

        private TrackedRenderTask(Runnable task, Set<Future<?>> tasks) {
            super(task, null);
            this.tasks = tasks;
        }

        @Override
        protected void done() {
            tasks.remove(this);
        }
    }

    private static final class WorkState {
        private final Frame frame;
        private final CancellationToken token;
        private int index;
        private int inFlight;
        private boolean refining;

        private WorkState(Frame frame, CancellationToken token) {
            this.frame = frame;
            this.token = token;
        }

        Frame frame() {
            return frame;
        }

        CancellationToken token() {
            return token;
        }

        VisibleTile nextMissing() {
            while (index < frame.tiles().size()) {
                VisibleTile tile = frame.tiles().get(index++);
                if (refining ? tile.image() != null && !tile.complete() : tile.image() == null) {
                    return tile;
                }
            }
            return null;
        }

        boolean refining() {
            return refining;
        }

        boolean beginRefinement() {
            if (refining || frame.spec().type() != RenderType.BIOME) {
                return false;
            }
            refining = true;
            index = 0;
            return true;
        }

        int inFlight() {
            return inFlight;
        }

        void incrementInFlight() {
            inFlight++;
        }

        void decrementInFlight() {
            inFlight--;
        }
    }

    private static final class WeightedTileCache {
        private final long maximumBytes;
        private final LinkedHashMap<TileKey, CacheEntry> entries;
        private long bytes;

        private WeightedTileCache(long maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.entries = new LinkedHashMap<>(128, 0.75F, true);
        }

        synchronized TileResult get(TileKey key) {
            CacheEntry entry = entries.get(key);
            return entry == null ? null : entry.result();
        }

        synchronized void put(TileKey key, TileResult result) {
            BufferedImage image = result.image();
            long imageBytes = (long) image.getWidth() * image.getHeight() * Integer.BYTES;
            CacheEntry previous = entries.put(key, new CacheEntry(result, imageBytes));
            if (previous != null) {
                bytes -= previous.bytes();
            }
            bytes += imageBytes;
            while (bytes > maximumBytes && !entries.isEmpty()) {
                Iterator<Map.Entry<TileKey, CacheEntry>> iterator = entries.entrySet().iterator();
                Map.Entry<TileKey, CacheEntry> eldest = iterator.next();
                bytes -= eldest.getValue().bytes();
                iterator.remove();
            }
        }

        synchronized void clear() {
            entries.clear();
            bytes = 0L;
        }
    }

    private record TileResult(BufferedImage image, boolean complete) {
        private TileResult {
            Objects.requireNonNull(image, "image");
        }
    }

    private record CacheEntry(TileResult result, long bytes) {
    }
}
