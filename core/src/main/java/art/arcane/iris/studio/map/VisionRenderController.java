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

package art.arcane.iris.studio.map;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.MapLabels;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapTile;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Renders the map as tiles on fixed zoom levels, so zooming within a level and panning reuse tiles that are already drawn.
 * Tiles keep rendering while the view moves, as long as they are still on screen.
 */
final class VisionRenderController implements AutoCloseable {
    static final int TILE_PIXELS = 64;
    static final double MINIMUM_VIEW_BLOCKS_PER_PIXEL = 1D / 8D;
    static final double MAXIMUM_BLOCKS_PER_PIXEL = 4_096D;
    static final long FIRST_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);
    static final long LAST_RETRY_NANOS = TimeUnit.SECONDS.toNanos(8L);

    private static final double MINIMUM_RENDER_BLOCKS_PER_PIXEL = 1D;
    private static final int MAXIMUM_VISIBLE_TILES = 32_768;
    private static final int PROBE_QUEUE_CAPACITY = 1;
    private static final int COARSER_FALLBACK_LEVELS = 6;
    private static final long CACHE_BYTES = 256L * 1024L * 1024L;

    private final Runnable listener;
    private final int workerCount;
    private final ThreadPoolExecutor renderExecutor;
    private final ThreadPoolExecutor probeExecutor;
    private final WeightedTileCache cache;
    private final Set<TileKey> tilesInFlight = new HashSet<>();
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final AtomicLong viewSequence = new AtomicLong();
    private final AtomicLong probeSequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean publicationQueued = new AtomicBoolean();
    private final AtomicBoolean publicationDirty = new AtomicBoolean();
    private volatile Frame currentFrame;

    VisionRenderController(Runnable listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        this.workerCount = Math.max(1, Math.min(6, processors / 2));
        AtomicInteger threadSequence = new AtomicInteger();
        this.renderExecutor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                daemonFactory("Iris Vision Render", Thread.NORM_PRIORITY, threadSequence)
        );
        this.renderExecutor.allowCoreThreadTimeOut(true);
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
        MapHost.interruptOnPluginShutdown(renderExecutor);
        MapHost.interruptOnPluginShutdown(probeExecutor);
    }

    // Snapping to powers of two keeps every zoom within a level on the same tiles; nearest keeps tiles within 1.41x of screen size.
    static double renderBlocksPerPixel(double viewBlocksPerPixel) {
        double level = Math.pow(2D, Math.round(Math.log(viewBlocksPerPixel) / Math.log(2D)));
        return Math.max(MINIMUM_RENDER_BLOCKS_PER_PIXEL, Math.min(MAXIMUM_BLOCKS_PER_PIXEL, level));
    }

    synchronized Frame request(RenderSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (closed.get()) {
            throw new IllegalStateException("Vision render controller is closed");
        }
        double renderScale = renderBlocksPerPixel(spec.blocksPerPixel());
        List<VisibleTile> tiles = visibleTiles(spec, renderScale);
        Frame frame = new Frame(viewSequence.incrementAndGet(), spec, renderScale, tiles);
        for (VisibleTile tile : tiles) {
            tile.setTile(cachedTileServing(tile.key()));
        }
        currentFrame = frame;
        probeSequence.incrementAndGet();
        probeExecutor.getQueue().clear();
        startWorkers();
        publish();
        return frame;
    }

    synchronized void retryProvisional(Frame frame) {
        if (closed.get() || frame == null || frame != currentFrame) {
            return;
        }
        frame.nextCandidate = 0;
        startWorkers();
    }

    /**
     * A tile from another zoom level covering the given one, to draw while the real tile renders.
     */
    Fallback fallback(Frame frame, VisibleTile tile) {
        MapTile withoutHeights = cache.get(tile.key().withHeights(false));
        if (withoutHeights != null) {
            return new Fallback(List.of(new FallbackPiece(withoutHeights, 0, 0, TILE_PIXELS, 0D, 0D, 1D)));
        }
        Fallback coarser = coarserFallback(frame, tile);
        return coarser != null ? coarser : finerFallback(frame, tile);
    }

    // A tile drawn with heights serves just as well where no heights are needed, so turning shading off redraws nothing.
    private MapTile cachedTileServing(TileKey key) {
        MapTile exact = cache.get(key);
        if (exact != null || key.heights()) {
            return exact;
        }
        return cache.get(key.withHeights(true));
    }

    Progress progress(Frame frame) {
        if (frame == null) {
            return new Progress(0, 0, 0);
        }
        int ready = 0;
        for (VisibleTile tile : frame.tiles()) {
            if (tile.tile() != null && !tile.provisional) {
                ready++;
            }
        }
        return new Progress(frame.tiles().size(), ready, activeWorkers.get());
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
            probeExecutor.execute(() -> runProbe(probeRevision, probe, consumer));
        } catch (RejectedExecutionException ignored) {
            probeExecutor.getQueue().clear();
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        currentFrame = null;
        renderExecutor.shutdownNow();
        probeExecutor.shutdownNow();
        cache.clear();
        tilesInFlight.clear();
    }

    static List<VisibleTile> visibleTiles(RenderSpec spec, double renderScale) {
        Objects.requireNonNull(spec, "spec");
        double blocksPerPixel = spec.blocksPerPixel();
        double tileSpan = TILE_PIXELS * renderScale;
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
        for (long tileZ = minimumZ; tileZ <= maximumZ; tileZ++) {
            for (long tileX = minimumX; tileX <= maximumX; tileX++) {
                double deltaX = tileX + 0.5D - centerTileX;
                double deltaZ = tileZ + 0.5D - centerTileZ;
                TileKey key = new TileKey(spec.contentRevision(), spec.layer(), spec.heights(), renderScale, tileX, tileZ);
                tiles.add(new VisibleTile(key, deltaX * deltaX + deltaZ * deltaZ));
            }
        }
        tiles.sort(Comparator.comparingDouble(VisibleTile::distanceSquared)
                .thenComparingLong(VisibleTile::tileZ)
                .thenComparingLong(VisibleTile::tileX));
        return List.copyOf(tiles);
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

    private void startWorkers() {
        while (!closed.get()) {
            int active = activeWorkers.get();
            if (active >= workerCount || !hasWork()) {
                return;
            }
            if (!activeWorkers.compareAndSet(active, active + 1)) {
                continue;
            }
            try {
                renderExecutor.execute(this::renderUntilIdle);
            } catch (RejectedExecutionException ignored) {
                activeWorkers.decrementAndGet();
                return;
            }
        }
    }

    // Each worker keeps taking the most central missing tile of whatever frame is current, until the screen is covered.
    private void renderUntilIdle() {
        try {
            TileJob job;
            while ((job = claimNextTile()) != null) {
                render(job);
            }
        } finally {
            activeWorkers.decrementAndGet();
        }
        startWorkers();
    }

    private synchronized boolean hasWork() {
        Frame frame = currentFrame;
        return frame != null && frame.nextMissing(tilesInFlight, false) != null;
    }

    private synchronized TileJob claimNextTile() {
        Frame frame = currentFrame;
        if (frame == null || closed.get()) {
            return null;
        }
        VisibleTile tile = frame.nextMissing(tilesInFlight, true);
        if (tile == null) {
            return null;
        }
        tilesInFlight.add(tile.key());
        return new TileJob(frame.spec().renderer(), tile.key());
    }

    private synchronized void release(TileKey key) {
        tilesInFlight.remove(key);
    }

    private void render(TileJob job) {
        TileKey key = job.key();
        try {
            double tileSpan = TILE_PIXELS * key.blocksPerPixel();
            long revision = ProvisionalSampling.revision();
            MapTile tile = job.renderer().renderTile(
                    key.tileX() * tileSpan,
                    key.tileZ() * tileSpan,
                    key.blocksPerPixel(),
                    TILE_PIXELS,
                    key.layer(),
                    key.heights(),
                    () -> !isWanted(key)
            );
            boolean provisional = revision != ProvisionalSampling.revision();
            synchronized (this) {
                if (!isWanted(key)) {
                    return;
                }
                VisibleTile visible = currentFrame.tile(key);
                if (provisional) {
                    visible.markProvisional(tile, System.nanoTime());
                } else {
                    cache.put(key, tile);
                    visible.setTile(tile);
                }
            }
            publish();
        } catch (CancellationException ignored) {
        } catch (Throwable error) {
            IrisLogging.debug("Vision tile render failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            release(key);
        }
    }

    private boolean isWanted(TileKey key) {
        Frame frame = currentFrame;
        return !closed.get() && frame != null && frame.tile(key) != null;
    }

    private Fallback coarserFallback(Frame frame, VisibleTile tile) {
        TileKey key = tile.key();
        for (int level = 1; level <= COARSER_FALLBACK_LEVELS; level++) {
            long scale = 1L << level;
            int sourceSize = TILE_PIXELS >> level;
            double parentScale = key.blocksPerPixel() * scale;
            if (sourceSize < 1 || parentScale > MAXIMUM_BLOCKS_PER_PIXEL) {
                return null;
            }
            long parentX = Math.floorDiv(key.tileX(), scale);
            long parentZ = Math.floorDiv(key.tileZ(), scale);
            MapTile parent = cache.get(new TileKey(key.contentRevision(), key.layer(), key.heights(), parentScale, parentX,
                    parentZ));
            if (parent != null) {
                int sourceX = (int) (key.tileX() - parentX * scale) * sourceSize;
                int sourceZ = (int) (key.tileZ() - parentZ * scale) * sourceSize;
                return new Fallback(List.of(new FallbackPiece(parent, sourceX, sourceZ, sourceSize, 0D, 0D, 1D)));
            }
        }
        return null;
    }

    private Fallback finerFallback(Frame frame, VisibleTile tile) {
        TileKey key = tile.key();
        double childScale = key.blocksPerPixel() / 2D;
        if (childScale < MINIMUM_RENDER_BLOCKS_PER_PIXEL) {
            return null;
        }
        List<FallbackPiece> pieces = new ArrayList<>(4);
        for (int childZ = 0; childZ < 2; childZ++) {
            for (int childX = 0; childX < 2; childX++) {
                MapTile child = cache.get(new TileKey(key.contentRevision(), key.layer(), key.heights(), childScale,
                        key.tileX() * 2L + childX, key.tileZ() * 2L + childZ));
                if (child != null) {
                    pieces.add(new FallbackPiece(child, 0, 0, TILE_PIXELS, childX * 0.5D, childZ * 0.5D, 0.5D));
                }
            }
        }
        return pieces.isEmpty() ? null : new Fallback(List.copyOf(pieces));
    }

    private <T> void runProbe(long probeRevision, Callable<T> probe, Consumer<T> consumer) {
        try {
            if (!isProbeCurrent(probeRevision)) {
                return;
            }
            T result = probe.call();
            if (!isProbeCurrent(probeRevision)) {
                return;
            }
            EventQueue.invokeLater(() -> {
                if (isProbeCurrent(probeRevision)) {
                    consumer.accept(result);
                }
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            IrisLogging.debug("Vision probe failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private boolean isProbeCurrent(long probeRevision) {
        return !closed.get() && probeSequence.get() == probeRevision;
    }

    private void publish() {
        if (closed.get()) {
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

    record RenderSpec(
            MapTileRenderer renderer,
            MapLayer layer,
            long contentRevision,
            double centerX,
            double centerZ,
            double blocksPerPixel,
            int width,
            int height,
            boolean heights
    ) {
        RenderSpec {
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(layer, "layer");
            if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
                throw new IllegalArgumentException("Vision center must be finite");
            }
            if (!Double.isFinite(blocksPerPixel)
                    || blocksPerPixel < MINIMUM_VIEW_BLOCKS_PER_PIXEL
                    || blocksPerPixel > MAXIMUM_BLOCKS_PER_PIXEL) {
                throw new IllegalArgumentException("Vision scale is outside the supported range");
            }
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Vision viewport must be positive");
            }
        }
    }

    static final class Frame {
        private final long viewRevision;
        private final RenderSpec spec;
        private final double renderBlocksPerPixel;
        private final List<VisibleTile> tiles;
        private final Map<TileKey, VisibleTile> tilesByKey;
        private int nextCandidate;

        private Frame(long viewRevision, RenderSpec spec, double renderBlocksPerPixel, List<VisibleTile> tiles) {
            this.viewRevision = viewRevision;
            this.spec = Objects.requireNonNull(spec, "spec");
            this.renderBlocksPerPixel = renderBlocksPerPixel;
            this.tiles = tiles;
            this.tilesByKey = new HashMap<>(tiles.size() * 2);
            for (VisibleTile tile : tiles) {
                tilesByKey.put(tile.key(), tile);
            }
        }

        long viewRevision() {
            return viewRevision;
        }

        RenderSpec spec() {
            return spec;
        }

        double renderBlocksPerPixel() {
            return renderBlocksPerPixel;
        }

        List<VisibleTile> tiles() {
            return tiles;
        }

        private VisibleTile tile(TileKey key) {
            return tilesByKey.get(key);
        }

        /**
         * The tile covering a world position together with the pixel of it there, or null when that tile is not drawn yet.
         */
        TilePixel pixelAt(double worldX, double worldZ) {
            double tileSpan = TILE_PIXELS * renderBlocksPerPixel;
            long tileX = (long) Math.floor(worldX / tileSpan);
            long tileZ = (long) Math.floor(worldZ / tileSpan);
            VisibleTile visible = tilesByKey.get(new TileKey(spec.contentRevision(), spec.layer(), spec.heights(),
                    renderBlocksPerPixel, tileX, tileZ));
            MapTile tile = visible == null ? null : visible.tile();
            if (tile == null) {
                return null;
            }
            int pixelX = pixelWithin(worldX - tileX * tileSpan);
            int pixelZ = pixelWithin(worldZ - tileZ * tileSpan);
            return new TilePixel(tile, pixelX, pixelZ);
        }

        private int pixelWithin(double offsetBlocks) {
            return Math.max(0, Math.min(TILE_PIXELS - 1, (int) Math.floor(offsetBlocks / renderBlocksPerPixel)));
        }

        // Tiles are sorted centre first, and everything before the cursor is drawn or claimed, so each search resumes there.
        private VisibleTile nextMissing(Set<TileKey> inFlight, boolean advance) {
            for (int index = nextCandidate; index < tiles.size(); index++) {
                VisibleTile tile = tiles.get(index);
                if (tile.needsRender(System.nanoTime()) && !inFlight.contains(tile.key())) {
                    if (advance) {
                        nextCandidate = index + 1;
                    }
                    return tile;
                }
            }
            if (advance) {
                nextCandidate = tiles.size();
            }
            return null;
        }
    }

    static final class VisibleTile {
        private final TileKey key;
        private final double distanceSquared;
        private volatile MapTile tile;
        private volatile boolean provisional;
        private long retryDelay;
        private long retryAt;

        private VisibleTile(TileKey key, double distanceSquared) {
            this.key = key;
            this.distanceSquared = distanceSquared;
        }

        TileKey key() {
            return key;
        }

        long tileX() {
            return key.tileX();
        }

        long tileZ() {
            return key.tileZ();
        }

        double distanceSquared() {
            return distanceSquared;
        }

        MapTile tile() {
            return tile;
        }

        private void setTile(MapTile tile) {
            this.tile = tile;
            provisional = false;
            retryDelay = 0L;
        }

        private void markProvisional(MapTile tile, long now) {
            this.tile = tile;
            retryDelay = retryDelay == 0L ? FIRST_RETRY_NANOS : Math.min(LAST_RETRY_NANOS, retryDelay * 2L);
            retryAt = now + retryDelay;
            provisional = true;
        }

        private boolean needsRender(long now) {
            return tile == null || provisional && now - retryAt >= 0L;
        }
    }

    /**
     * @param heights whether the tile also carries terrain heights, for shading
     */
    record TileKey(long contentRevision, MapLayer layer, boolean heights, double blocksPerPixel, long tileX, long tileZ) {
        TileKey {
            Objects.requireNonNull(layer, "layer");
            if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
                throw new IllegalArgumentException("Vision cache scale must be finite and positive");
            }
        }

        TileKey withHeights(boolean nextHeights) {
            return new TileKey(contentRevision, layer, nextHeights, blocksPerPixel, tileX, tileZ);
        }
    }

    record TilePixel(MapTile tile, int pixelX, int pixelZ) {
        MapLabels.Label subject() {
            return tile.labels().label(tile.subjectAt(pixelX, pixelZ));
        }

        MapLabels.Label region() {
            return tile.labels().label(tile.regionAt(pixelX, pixelZ));
        }
    }

    /**
     * Pieces of cached tiles that together cover a missing tile; offsets and size are fractions of the tile.
     */
    record Fallback(List<FallbackPiece> pieces) {
    }

    record FallbackPiece(MapTile tile, int sourceX, int sourceZ, int sourceSize, double offsetX, double offsetZ,
                         double size) {
    }

    record Progress(int total, int ready, int activeWorkers) {
        double completion() {
            if (total == 0) {
                return 1D;
            }
            return Math.min(1D, ready / (double) total);
        }
    }

    private record TileJob(MapTileRenderer renderer, TileKey key) {
    }

    private static final class WeightedTileCache {
        private final long maximumBytes;
        private final LinkedHashMap<TileKey, CacheEntry> entries;
        private long bytes;

        private WeightedTileCache(long maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.entries = new LinkedHashMap<>(128, 0.75F, true);
        }

        synchronized MapTile get(TileKey key) {
            CacheEntry entry = entries.get(key);
            return entry == null ? null : entry.tile();
        }

        synchronized void put(TileKey key, MapTile tile) {
            long tileBytes = tile.bytes();
            CacheEntry previous = entries.put(key, new CacheEntry(tile, tileBytes));
            if (previous != null) {
                bytes -= previous.bytes();
            }
            bytes += tileBytes;
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

    private record CacheEntry(MapTile tile, long bytes) {
    }
}
