package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.raster.ReachIndex;
import art.arcane.iris.spi.IrisLogging;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class HydrologyPlanCache {
    private static final int FRONT = 3;
    public static final int WINDOW_LATTICE_CELLS = frontCells(WindowKey.SIZE, WindowPlanner.CELL);
    public static final int FINE_LATTICE_CELLS = frontCells(WindowKey.SIZE, WindowPlanner.FINE_CELL);
    public static final int REGION_LATTICE_CELLS = frontCells(RegionKey.SIZE, RegionPlanner.CELL);
    private static final int WINDOW_CAPACITY = 128;
    private static final int REGION_CAPACITY = 64;
    private static final long DEMAND = Long.MIN_VALUE;
    private static final int SPECULATIVE_SHIFT = 24;
    private static final long MAX_PREPARED_WINDOWS = 1L << SPECULATIVE_SHIFT;

    private final Function<RegionKey, RegionPlan> regionPlanner;
    private final BiFunction<WindowKey, List<RegionPlan>, WindowPlan> windowPlanner;
    private final boolean persist;
    private final Object storeLock = new Object();
    private volatile HydrologyPlanStore store;
    private int writing;
    private final ExecutorService pool;
    private final int speculativeParallelism;
    private final BooleanSupplier mainThread;
    private final Object lock = new Object();
    private final PriorityQueue<Job> queue = new PriorityQueue<>(Comparator.comparingLong((Job job) -> job.priority).thenComparingLong(job -> job.order));
    private final AtomicLong clock = new AtomicLong();
    private final AtomicLong demands = new AtomicLong();
    private final AtomicLong speculations = new AtomicLong();
    private final AtomicLong previewStamps = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong readyRevision = new AtomicLong();
    private final Shelf<RegionKey, RegionPlan> regions = new Shelf<>(REGION_CAPACITY, this::startRegion);
    private final Shelf<WindowKey, IndexedWindow> windows = new Shelf<>(WINDOW_CAPACITY, this::startWindow);
    private final Map<Object, Set<Slot<?, ?>>> epochSlots = new HashMap<>();
    private int runningSpeculative;
    private volatile boolean closed;

    public HydrologyPlanCache(HydrologySettings settings, Function<RegionKey, RegionPlan> regionPlanner,
                              BiFunction<WindowKey, List<RegionPlan>, WindowPlan> windowPlanner, ExecutorService pool, BooleanSupplier mainThread) {
        this.regionPlanner = Objects.requireNonNull(regionPlanner, "regionPlanner");
        this.windowPlanner = Objects.requireNonNull(windowPlanner, "windowPlanner");
        this.persist = Objects.requireNonNull(settings, "settings").persist();
        this.pool = Objects.requireNonNull(pool, "pool");
        this.speculativeParallelism = speculativeParallelism(pool);
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    public static HydrologyPlanCache create(HydrologySettings settings, RegionPlanner regions, WindowPlanner windows, ExecutorService pool,
                                            BooleanSupplier mainThread) {
        return new HydrologyPlanCache(settings, Objects.requireNonNull(regions, "regions")::plan, Objects.requireNonNull(windows, "windows")::plan,
                pool, mainThread);
    }

    public IndexedWindow window(WindowKey key) {
        Objects.requireNonNull(key, "key");
        requireOffMainThread("window " + key.wx() + "," + key.wz());
        requireOpen();
        return await(windows.obtain(key, false, held(demandPriority())).future);
    }

    public CompletableFuture<IndexedWindow> demandWindow(WindowKey key) {
        return obtainWindow(key, demandPriority(), false);
    }

    public CompletableFuture<IndexedWindow> prefetchWindow(WindowKey key) {
        return obtainWindow(key, speculativePriority(), false);
    }

    public CompletableFuture<IndexedWindow> previewWindow(WindowKey key) {
        return obtainWindow(key, speculativePriority(), true);
    }

    public <T> T awaitPlan(CompletableFuture<T> plan) {
        Objects.requireNonNull(plan, "plan");
        requireOffMainThread("plan wait");
        return await(plan);
    }

    public Optional<IndexedWindow> windowIfReady(WindowKey key) {
        return windows.ready(Objects.requireNonNull(key, "key"));
    }

    public long windowEvictions() {
        return windows.evictions.get();
    }

    public long readyRevision() {
        return readyRevision.get();
    }

    public RegionPlan region(RegionKey key) {
        Objects.requireNonNull(key, "key");
        requireOffMainThread("region " + key.rx() + "," + key.rz());
        requireOpen();
        return await(regions.obtain(key, false, held(demandPriority())).future);
    }

    public CompletableFuture<RegionPlan> demandRegion(RegionKey key) {
        return obtainRegion(key, demandPriority(), false);
    }

    public CompletableFuture<RegionPlan> prefetchRegion(RegionKey key) {
        return obtainRegion(key, speculativePriority(), false);
    }

    public CompletableFuture<RegionPlan> previewRegion(RegionKey key) {
        return obtainRegion(key, speculativePriority(), true);
    }

    public Optional<RegionPlan> regionIfReady(RegionKey key) {
        return regions.ready(Objects.requireNonNull(key, "key"));
    }

    public CompletableFuture<Void> prepareWindowsAround(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int rings) {
        return prepare(minChunkX, minChunkZ, maxChunkX, maxChunkZ, rings, Interest.HELD);
    }

    public CompletableFuture<Void> prepareWindowsAround(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int rings, Object epoch) {
        return prepare(minChunkX, minChunkZ, maxChunkX, maxChunkZ, rings, Interest.of(Objects.requireNonNull(epoch, "epoch")));
    }

    public void cancel(Object epoch) {
        Objects.requireNonNull(epoch, "epoch");
        List<Slot<?, ?>> withdrawn = new ArrayList<>();
        synchronized (lock) {
            Set<Slot<?, ?>> tagged = epochSlots.remove(epoch);
            if (tagged == null) {
                return;
            }
            for (Slot<?, ?> slot : tagged) {
                slot.epochs.remove(epoch);
                if (slot.held || !slot.epochs.isEmpty() || slot.future.isDone()) {
                    continue;
                }
                Job job = slot.job;
                if (job != null && queue.remove(job)) {
                    slot.job = null;
                    slot.withdrawLocked();
                    withdrawn.add(slot);
                } else if (job == null && slot.waiting) {
                    slot.withdrawLocked();
                    withdrawn.add(slot);
                }
            }
        }
        CancellationException withdrawal = new CancellationException("Hydrology prefetch was withdrawn.");
        for (Slot<?, ?> slot : withdrawn) {
            slot.fail(withdrawal);
        }
    }

    public void runSpeculative(Runnable task, Runnable dropped) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(dropped, "dropped");
        Job job = new Job(null, task, dropped, clock.incrementAndGet());
        boolean queued;
        synchronized (lock) {
            queued = !closed;
            if (queued) {
                job.priority = speculativePriority();
                queue.add(job);
            }
        }
        if (!queued) {
            dropped.run();
            return;
        }
        dispatch();
    }

    private CompletableFuture<Void> prepare(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int rings, Interest interest) {
        if (rings < 0 || maxChunkX < minChunkX || maxChunkZ < minChunkZ) {
            throw new IllegalArgumentException("Invalid prepare area " + minChunkX + "," + minChunkZ + " to " + maxChunkX + "," + maxChunkZ
                    + " with " + rings + " rings");
        }
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        List<WindowKey> keys = nearestFirst(minChunkX, minChunkZ, maxChunkX, maxChunkZ, rings);
        long first = speculativePriority();
        CompletableFuture<?>[] futures = new CompletableFuture<?>[keys.size()];
        for (int index = 0; index < keys.size(); index++) {
            long priority = first + index;
            futures[index] = windows.obtain(keys.get(index), false, slot -> attachLocked(slot, priority, interest)).future;
        }
        return CompletableFuture.allOf(futures);
    }

    public void attachStore(HydrologyPlanStore attached) {
        Objects.requireNonNull(attached, "store");
        synchronized (storeLock) {
            if (store != null) {
                throw new IllegalStateException("The hydrology plan cache already has a store.");
            }
            if (persist && !closed) {
                store = attached;
            }
        }
    }

    public void detachStore() {
        boolean interrupted = false;
        synchronized (storeLock) {
            store = null;
            while (writing > 0) {
                try {
                    storeLock.wait();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void close() {
        List<Job> dropped;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            dropped = new ArrayList<>(queue);
            queue.clear();
        }
        CancellationException failure = new CancellationException("Hydrology plan cache is closed.");
        for (Job job : dropped) {
            job.drop(failure);
        }
        windows.clear(failure);
        regions.clear(failure);
        detachStore();
    }

    private CompletableFuture<IndexedWindow> obtainWindow(WindowKey key, long priority, boolean preview) {
        Objects.requireNonNull(key, "key");
        if (closed) {
            return CompletableFuture.failedFuture(new CancellationException("Hydrology plan cache is closed."));
        }
        return windows.obtain(key, preview, held(priority)).future.copy();
    }

    private CompletableFuture<RegionPlan> obtainRegion(RegionKey key, long priority, boolean preview) {
        Objects.requireNonNull(key, "key");
        if (closed) {
            return CompletableFuture.failedFuture(new CancellationException("Hydrology plan cache is closed."));
        }
        return regions.obtain(key, preview, held(priority)).future.copy();
    }

    private <K, V> Consumer<Slot<K, V>> held(long priority) {
        return slot -> attachLocked(slot, priority, Interest.HELD);
    }

    private void attachLocked(Slot<?, ?> slot, long priority, Interest interest) {
        promoteLocked(slot, priority);
        interestLocked(slot, interest);
    }

    private void attachNeedLocked(Slot<WindowKey, IndexedWindow> window, Slot<RegionKey, RegionPlan> need) {
        attachLocked(need, window.priority, window.interest());
        if (!window.withdrawn && !window.future.isDone()) {
            List<Slot<RegionKey, RegionPlan>> attached = new ArrayList<>(window.needs.size() + 1);
            attached.addAll(window.needs);
            attached.add(need);
            window.needs = attached;
        }
    }

    private long speculativePriority() {
        return Long.MAX_VALUE - (speculations.incrementAndGet() << SPECULATIVE_SHIFT);
    }

    private static int speculativeParallelism(ExecutorService pool) {
        int parallelism = switch (pool) {
            case MultiBurst burst -> burst.parallelism();
            case ForkJoinPool forkJoin -> forkJoin.getParallelism();
            case ThreadPoolExecutor threads -> threads.getCorePoolSize();
            default -> 1;
        };
        return Math.max(1, parallelism / 2);
    }

    private static int frontCells(int unit, int cell) {
        int cells = (FRONT + 2) * unit / cell;
        return cells * cells;
    }

    private static List<WindowKey> nearestFirst(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int rings) {
        int chunks = WindowKey.SIZE >> 4;
        int minX = Math.floorDiv(minChunkX, chunks);
        int minZ = Math.floorDiv(minChunkZ, chunks);
        int maxX = Math.floorDiv(maxChunkX, chunks);
        int maxZ = Math.floorDiv(maxChunkZ, chunks);
        long width = (long) maxX - minX + 1L + 2L * rings;
        long depth = (long) maxZ - minZ + 1L + 2L * rings;
        if (width > MAX_PREPARED_WINDOWS || depth > MAX_PREPARED_WINDOWS || width * depth > MAX_PREPARED_WINDOWS) {
            throw new IllegalArgumentException("Prepare area covers " + width + " x " + depth + " windows, above " + MAX_PREPARED_WINDOWS);
        }
        List<WindowKey> keys = new ArrayList<>();
        for (int wz = minZ - rings; wz <= maxZ + rings; wz++) {
            for (int wx = minX - rings; wx <= maxX + rings; wx++) {
                keys.add(new WindowKey(wx, wz));
            }
        }
        long centreX = (long) minX + maxX;
        long centreZ = (long) minZ + maxZ;
        keys.sort(Comparator.comparingInt((WindowKey key) -> ring(key, minX, minZ, maxX, maxZ))
                .thenComparingLong(key -> square(2L * key.wx() - centreX) + square(2L * key.wz() - centreZ))
                .thenComparingInt(WindowKey::wz)
                .thenComparingInt(WindowKey::wx));
        return keys;
    }

    private static int ring(WindowKey key, int minX, int minZ, int maxX, int maxZ) {
        int dx = Math.max(Math.max(minX - key.wx(), key.wx() - maxX), 0);
        int dz = Math.max(Math.max(minZ - key.wz(), key.wz() - maxZ), 0);
        return Math.max(dx, dz);
    }

    private static long square(long value) {
        return value * value;
    }

    private void startWindow(WindowKey key, Slot<WindowKey, IndexedWindow> slot) {
        HydrologyPlanStore current = store;
        if (current == null) {
            requestRegions(key, slot);
        } else {
            enqueue(slot, () -> loadWindow(key, slot, current));
        }
    }

    private void loadWindow(WindowKey key, Slot<WindowKey, IndexedWindow> slot, HydrologyPlanStore current) {
        Optional<WindowPlan> stored = current.readWindow(key);
        if (stored.isEmpty()) {
            requestRegions(key, slot);
            return;
        }
        IndexedWindow window = indexed(stored.get());
        requireUninterrupted(key);
        slot.succeed(window);
    }

    private void requestRegions(WindowKey key, Slot<WindowKey, IndexedWindow> slot) {
        List<RegionKey> keys = WindowPlanner.lakeRegions(key);
        synchronized (lock) {
            if (slot.withdrawn) {
                return;
            }
            slot.waiting = true;
        }
        List<Slot<RegionKey, RegionPlan>> needs = new ArrayList<>(keys.size());
        CompletableFuture<?>[] futures = new CompletableFuture<?>[keys.size()];
        for (int index = 0; index < keys.size(); index++) {
            if (slot.withdrawn) {
                return;
            }
            Slot<RegionKey, RegionPlan> need = regions.obtain(keys.get(index), slot.preview, attached -> attachNeedLocked(slot, attached));
            needs.add(need);
            futures[index] = need.future;
        }
        CompletableFuture.allOf(futures).whenComplete((ignored, failure) -> {
            if (failure == null) {
                synchronized (lock) {
                    slot.waiting = false;
                }
                enqueue(slot, () -> planWindow(key, slot, needs));
            } else {
                slot.fail(failure);
            }
        });
    }

    private void planWindow(WindowKey key, Slot<WindowKey, IndexedWindow> slot, List<Slot<RegionKey, RegionPlan>> needs) {
        List<RegionPlan> plans = new ArrayList<>(needs.size());
        for (Slot<RegionKey, RegionPlan> need : needs) {
            plans.add(need.future.join());
        }
        WindowPlan plan = windowPlanner.apply(key, plans);
        IndexedWindow window = indexed(plan);
        requireUninterrupted(key);
        persist(target -> target.writeWindow(plan), key);
        slot.succeed(window);
    }

    private void startRegion(RegionKey key, Slot<RegionKey, RegionPlan> slot) {
        enqueue(slot, () -> planRegion(key, slot));
    }

    private void planRegion(RegionKey key, Slot<RegionKey, RegionPlan> slot) {
        HydrologyPlanStore current = store;
        Optional<RegionPlan> stored = current == null ? Optional.empty() : current.readRegion(key);
        if (stored.isPresent()) {
            requireUninterrupted(key);
            slot.succeed(stored.get());
            return;
        }
        RegionPlan plan = regionPlanner.apply(key);
        requireUninterrupted(key);
        persist(target -> target.writeRegion(plan), key);
        slot.succeed(plan);
    }

    private static IndexedWindow indexed(WindowPlan plan) {
        return new IndexedWindow(plan, ReachIndex.build(plan.reaches(), plan.ponds()));
    }

    private void persist(Consumer<HydrologyPlanStore> write, Object key) {
        HydrologyPlanStore target;
        synchronized (storeLock) {
            target = store;
            if (target == null || closed) {
                return;
            }
            writing++;
        }
        try {
            write.accept(target);
        } catch (UncheckedIOException failure) {
            IrisLogging.reportError("Hydrology plan " + key + " could not be saved.", failure);
        } finally {
            synchronized (storeLock) {
                writing--;
                if (writing == 0) {
                    storeLock.notifyAll();
                }
            }
        }
    }

    private void enqueue(Slot<?, ?> slot, Runnable body) {
        Job job = new Job(slot, body, null, clock.incrementAndGet());
        boolean queued;
        synchronized (lock) {
            if (slot.future.isDone() || slot.withdrawn) {
                return;
            }
            queued = !closed;
            if (queued) {
                job.priority = slot.priority;
                slot.job = job;
                queue.add(job);
            }
        }
        if (!queued) {
            slot.fail(new CancellationException("Hydrology plan cache is closed."));
            return;
        }
        dispatch();
    }

    private void runNext() {
        Job job;
        boolean speculative;
        synchronized (lock) {
            job = queue.peek();
            if (job == null) {
                return;
            }
            speculative = job.priority >= 0;
            if (speculative && runningSpeculative >= speculativeParallelism) {
                return;
            }
            queue.remove();
            if (speculative) {
                runningSpeculative++;
            }
            if (job.slot != null) {
                job.slot.job = null;
            }
        }
        try {
            if (closed) {
                job.drop(new CancellationException("Hydrology plan cache is closed."));
                return;
            }
            job.body.run();
        } catch (Throwable failure) {
            if (job.slot != null) {
                job.slot.fail(failure);
            } else if (!closed) {
                IrisLogging.reportError("A speculative hydrology task failed.", failure);
            }
        } finally {
            if (speculative) {
                boolean pending;
                synchronized (lock) {
                    runningSpeculative--;
                    pending = !closed && !queue.isEmpty();
                }
                if (pending) {
                    dispatch();
                }
            }
        }
    }

    private void dispatch() {
        try {
            pool.execute(this::runNext);
        } catch (RejectedExecutionException rejected) {
            abandonQueue(rejected);
        }
    }

    private void abandonQueue(Throwable failure) {
        List<Job> dropped;
        synchronized (lock) {
            dropped = new ArrayList<>(queue);
            queue.clear();
            for (Job job : dropped) {
                if (job.slot != null) {
                    job.slot.job = null;
                }
            }
        }
        for (Job job : dropped) {
            job.drop(failure);
        }
    }

    private void promoteLocked(Slot<?, ?> slot, long priority) {
        if (priority >= slot.priority) {
            return;
        }
        slot.priority = priority;
        Job job = slot.job;
        if (job != null && queue.remove(job)) {
            boolean demanded = job.priority >= 0 && priority < 0;
            job.priority = priority;
            queue.add(job);
            if (demanded) {
                dispatch();
            }
        }
        for (Slot<RegionKey, RegionPlan> need : slot.needs) {
            promoteLocked(need, priority);
        }
    }

    private void interestLocked(Slot<?, ?> slot, Interest interest) {
        if (slot.future.isDone() || slot.withdrawn) {
            return;
        }
        boolean widened = false;
        if (interest.held() && !slot.held) {
            slot.held = true;
            widened = true;
        }
        if (!slot.held) {
            for (Object epoch : interest.epochs()) {
                if (slot.epochs.add(epoch)) {
                    epochSlots.computeIfAbsent(epoch, ignored -> new HashSet<>()).add(slot);
                    widened = true;
                }
            }
        }
        if (widened) {
            for (Slot<RegionKey, RegionPlan> need : slot.needs) {
                interestLocked(need, interest);
            }
        }
    }

    private long demandPriority() {
        return DEMAND + demands.incrementAndGet();
    }

    private void requireOffMainThread(String request) {
        if (mainThread.getAsBoolean()) {
            throw new IllegalStateException("Hydrology " + request + " was requested on the main thread.");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new CancellationException("Hydrology plan cache is closed.");
        }
    }

    private static void requireUninterrupted(Object key) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Hydrology plan " + key + " was interrupted.");
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            CancellationException cancelled = new CancellationException("Hydrology plan wait was interrupted.");
            cancelled.initCause(interruption);
            throw cancelled;
        } catch (ExecutionException failure) {
            Throwable cause = unwrap(failure.getCause());
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Hydrology planning failed.", cause);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private final class Shelf<K, V> {
        private final ConcurrentHashMap<K, Slot<K, V>> slots = new ConcurrentHashMap<>();
        private final AtomicLong evictions = new AtomicLong();
        private final int capacity;
        private final BiConsumer<K, Slot<K, V>> start;

        private Shelf(int capacity, BiConsumer<K, Slot<K, V>> start) {
            this.capacity = capacity;
            this.start = start;
        }

        private Optional<V> ready(K key) {
            Slot<K, V> slot = slots.get(key);
            if (slot == null || !slot.ready()) {
                return Optional.empty();
            }
            return Optional.of(slot.future.join());
        }

        private Slot<K, V> obtain(K key, boolean preview, Consumer<Slot<K, V>> attach) {
            while (true) {
                Slot<K, V> fresh = new Slot<>(this, key, preview);
                Slot<K, V> existing = slots.putIfAbsent(key, fresh);
                if (existing == null) {
                    synchronized (lock) {
                        attach.accept(fresh);
                    }
                    try {
                        start.accept(key, fresh);
                    } catch (Throwable failure) {
                        fresh.fail(failure);
                    }
                    return fresh;
                }
                synchronized (lock) {
                    if (!existing.withdrawn) {
                        if (!preview) {
                            existing.touch();
                        }
                        attach.accept(existing);
                        return existing;
                    }
                }
            }
        }

        private void evict() {
            if (slots.size() <= capacity) {
                return;
            }
            synchronized (this) {
                List<Aged<K, V>> ready = new ArrayList<>();
                for (Slot<K, V> slot : slots.values()) {
                    if (slot.ready()) {
                        ready.add(new Aged<>(slot, slot.used));
                    }
                }
                int excess = ready.size() - capacity;
                if (excess <= 0) {
                    return;
                }
                ready.sort(Comparator.comparingLong(Aged::used));
                for (int index = 0; index < excess; index++) {
                    Slot<K, V> slot = ready.get(index).slot();
                    if (slots.remove(slot.key, slot)) {
                        evictions.incrementAndGet();
                    }
                }
            }
        }

        private void clear(CancellationException failure) {
            for (Slot<K, V> slot : slots.values()) {
                slot.fail(failure);
            }
            slots.clear();
        }
    }

    private final class Slot<K, V> {
        private final Shelf<K, V> shelf;
        private final K key;
        private final CompletableFuture<V> future = new CompletableFuture<>();
        private volatile long used;
        private volatile boolean preview;
        private volatile boolean withdrawn;
        private long priority = Long.MAX_VALUE;
        private Job job;
        private List<Slot<RegionKey, RegionPlan>> needs = List.of();
        private final Set<Object> epochs = new HashSet<>();
        private boolean held;
        private boolean waiting;

        private Slot(Shelf<K, V> shelf, K key, boolean preview) {
            this.shelf = shelf;
            this.key = key;
            this.preview = preview;
            this.used = preview ? previewStamps.incrementAndGet() : clock.incrementAndGet();
        }

        private void touch() {
            used = clock.incrementAndGet();
            preview = false;
        }

        private void withdrawLocked() {
            withdrawn = true;
            shelf.slots.remove(key, this);
        }

        private boolean ready() {
            return future.isDone() && !future.isCompletedExceptionally();
        }

        private Interest interest() {
            return held ? Interest.HELD : new Interest(false, Set.copyOf(epochs));
        }

        private void succeed(V value) {
            release();
            if (future.complete(value)) {
                readyRevision.incrementAndGet();
            }
            shelf.evict();
        }

        private void fail(Throwable failure) {
            release();
            shelf.slots.remove(key, this);
            future.completeExceptionally(unwrap(failure));
        }

        private void release() {
            synchronized (lock) {
                needs = List.of();
                waiting = false;
                for (Object epoch : epochs) {
                    Set<Slot<?, ?>> tagged = epochSlots.get(epoch);
                    if (tagged != null && tagged.remove(this) && tagged.isEmpty()) {
                        epochSlots.remove(epoch);
                    }
                }
                epochs.clear();
            }
        }
    }

    private final class Job {
        private final Slot<?, ?> slot;
        private final Runnable body;
        private final Runnable dropped;
        private final long order;
        private long priority;

        private Job(Slot<?, ?> slot, Runnable body, Runnable dropped, long order) {
            this.slot = slot;
            this.body = body;
            this.dropped = dropped;
            this.order = order;
        }

        private void drop(Throwable failure) {
            if (slot != null) {
                slot.fail(failure);
                return;
            }
            try {
                dropped.run();
            } catch (Throwable cleanup) {
                IrisLogging.reportError("A dropped speculative hydrology task could not release its work.", cleanup);
            }
        }
    }

    private record Aged<K, V>(HydrologyPlanCache.Slot<K, V> slot, long used) {
    }

    private record Interest(boolean held, Set<Object> epochs) {
        private static final Interest HELD = new Interest(true, Set.of());

        private static Interest of(Object epoch) {
            return new Interest(false, Set.of(epoch));
        }
    }
}
