package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

public final class NativeStructureOwnershipStore {
    private static final int MAX_CACHED_AUTHORITIES = 65_536;
    private static final Cache<Engine, State> STATES = Caffeine.newBuilder().weakKeys().build();

    private NativeStructureOwnershipStore() {
    }

    public static void record(Engine engine, NativeStructureOwnershipRecord record) {
        state(engine).record(record);
    }

    public static NativeStructureOwnershipRecord find(Engine engine, int targetChunkX, int targetChunkZ,
                                                       String structureKey, int originChunkX, int originChunkZ) {
        return state(engine).find(targetChunkX, targetChunkZ,
                structureKey, originChunkX, originChunkZ);
    }

    public static NativeStructureOwnershipRecord findPersisted(
            Engine engine, String structureKey, int originChunkX, int originChunkZ) {
        return state(engine).findPersisted(structureKey, originChunkX, originChunkZ);
    }

    public static void discard(Engine engine, String structureKey, int originChunkX, int originChunkZ) {
        state(engine).discard(structureKey, originChunkX, originChunkZ);
    }

    public static void flush(Engine engine) {
        State state = STATES.getIfPresent(Objects.requireNonNull(engine,
                "Native structure ownership store requires an engine"));
        if (state != null) {
            state.flush();
        }
    }

    public static void close(Engine engine) {
        Engine resolved = Objects.requireNonNull(engine,
                "Native structure ownership store requires an engine");
        State state = STATES.get(resolved, State::new);
        state.close();
    }

    private static State state(Engine engine) {
        Engine resolved = Objects.requireNonNull(engine,
                "Native structure ownership store requires an engine");
        return STATES.get(resolved, State::new);
    }

    static final class State {
        private final WeakReference<Engine> engine;
        private final Storage storage;
        private final Cache<NativeStructureOwnershipRecord.OwnershipKey, Authority> authorities;
        private final ReentrantReadWriteLock lifecycleLock;
        private volatile boolean dirty;
        private volatile boolean closed;

        private State(Engine engine) {
            this(engine, new MantleStorage(engine));
        }

        State(Engine engine, Storage storage) {
            this.engine = new WeakReference<>(Objects.requireNonNull(engine,
                    "Native structure ownership store requires an engine"));
            this.storage = Objects.requireNonNull(storage,
                    "Native structure ownership storage must not be null");
            this.authorities = Caffeine.newBuilder().maximumSize(MAX_CACHED_AUTHORITIES).build();
            this.lifecycleLock = new ReentrantReadWriteLock(true);
        }

        void record(NativeStructureOwnershipRecord record) {
            lifecycleLock.readLock().lock();
            try {
                requireOpen();
                NativeStructureOwnershipRecord resolved = Objects.requireNonNull(
                        record, "Native structure ownership record must not be null");
                authorities.asMap().compute(resolved.ownershipKey(), (ignored, current) -> {
                    storage.write(pack(resolved.originChunkX(), resolved.originChunkZ()), resolved);
                    dirty = true;
                    return Authority.present(resolved);
                });
            } finally {
                lifecycleLock.readLock().unlock();
            }
        }

        NativeStructureOwnershipRecord find(int targetChunkX, int targetChunkZ,
                                             String structureKey, int originChunkX, int originChunkZ) {
            lifecycleLock.readLock().lock();
            try {
                requireOpen();
                NativeStructureOwnershipRecord.OwnershipKey key =
                        new NativeStructureOwnershipRecord.OwnershipKey(
                                structureKey, originChunkX, originChunkZ);
                Authority authority = authorities.asMap().computeIfAbsent(
                        key, this::loadAuthority);
                NativeStructureOwnershipRecord record = authority.record();
                return record != null && record.covers(targetChunkX, targetChunkZ)
                        ? record : null;
            } finally {
                lifecycleLock.readLock().unlock();
            }
        }

        NativeStructureOwnershipRecord findPersisted(
                String structureKey, int originChunkX, int originChunkZ) {
            lifecycleLock.readLock().lock();
            try {
                requireOpen();
                NativeStructureOwnershipRecord.OwnershipKey key =
                        new NativeStructureOwnershipRecord.OwnershipKey(
                                structureKey, originChunkX, originChunkZ);
                return authorities.asMap().computeIfAbsent(
                        key, this::loadAuthority).record();
            } finally {
                lifecycleLock.readLock().unlock();
            }
        }

        void discard(String structureKey, int originChunkX, int originChunkZ) {
            lifecycleLock.readLock().lock();
            try {
                requireOpen();
                NativeStructureOwnershipRecord.OwnershipKey key =
                        new NativeStructureOwnershipRecord.OwnershipKey(
                                structureKey, originChunkX, originChunkZ);
                authorities.asMap().compute(key, (ignored, current) -> {
                    storage.remove(pack(originChunkX, originChunkZ),
                            structureKey, originChunkX, originChunkZ);
                    dirty = true;
                    return Authority.absent();
                });
            } finally {
                lifecycleLock.readLock().unlock();
            }
        }

        void flush() {
            lifecycleLock.writeLock().lock();
            try {
                if (closed) {
                    throw new IllegalStateException("Native structure ownership store is closed");
                }
                flushDirty();
            } finally {
                lifecycleLock.writeLock().unlock();
            }
        }

        void close() {
            lifecycleLock.writeLock().lock();
            try {
                if (closed) {
                    return;
                }
                if (!flushDirty()) {
                    throw new IllegalStateException(
                            "Cannot close native structure ownership storage while Mantle regions are in use");
                }
                closed = true;
                authorities.invalidateAll();
                storage.close();
            } finally {
                lifecycleLock.writeLock().unlock();
            }
        }

        private boolean flushDirty() {
            if (!dirty) {
                return true;
            }
            if (storage.flush()) {
                dirty = false;
                return true;
            }
            return false;
        }

        private Authority loadAuthority(
                NativeStructureOwnershipRecord.OwnershipKey key) {
            NativeStructureOwnershipBundle origin = storage.read(
                    key.originChunkX(), key.originChunkZ());
            NativeStructureOwnershipRecord record = origin == null ? null
                    : origin.find(key.structureKey(), key.originChunkX(), key.originChunkZ());
            return record == null ? Authority.absent() : Authority.present(record);
        }

        private void requireOpen() {
            engine();
            if (closed) {
                throw new IllegalStateException("Native structure ownership store is closed");
            }
        }

        private Engine engine() {
            Engine active = engine.get();
            if (active == null) {
                throw new IllegalStateException("Native structure ownership engine is unavailable");
            }
            return active;
        }
    }

    interface Storage {
        NativeStructureOwnershipBundle read(int chunkX, int chunkZ);

        void write(long target, NativeStructureOwnershipRecord record);

        void remove(long target, String structureKey, int originChunkX, int originChunkZ);

        boolean flush();

        default void close() {
        }
    }

    static final class MantleStorage implements Storage {
        private final WeakReference<Engine> engine;
        private final Map<PendingTarget, NativeStructureOwnershipBundle> pendingBundles;
        private final WeakReference<IrisEngine> retirementEngine;
        private final IntConsumer retirementListener;

        MantleStorage(Engine engine) {
            Engine required = Objects.requireNonNull(engine,
                    "Native structure ownership store requires an engine");
            this.engine = new WeakReference<>(required);
            this.pendingBundles = new ConcurrentHashMap<>();
            if (required instanceof IrisEngine irisEngine) {
                this.retirementEngine = new WeakReference<>(irisEngine);
                this.retirementListener = this::flushRuntime;
                irisEngine.addGenerationRuntimeRetirementListener(retirementListener);
            } else {
                this.retirementEngine = null;
                this.retirementListener = null;
            }
        }

        @Override
        public NativeStructureOwnershipBundle read(int chunkX, int chunkZ) {
            RuntimeMantleOwner owner = owner();
            MantleChunk<Matter> chunk = owner.mantle().getMantle().getChunk(chunkX, chunkZ).use();
            try {
                synchronized (chunk) {
                    return chunk.get(0, 0, 0, NativeStructureOwnershipBundle.class);
                }
            } finally {
                chunk.release();
            }
        }

        @Override
        public void write(long target, NativeStructureOwnershipRecord record) {
            RuntimeMantleOwner owner = owner();
            int targetChunkX = unpackX(target);
            int targetChunkZ = unpackZ(target);
            MantleChunk<Matter> chunk = owner.mantle().getMantle().getChunk(targetChunkX, targetChunkZ).use();
            try {
                synchronized (chunk) {
                    Matter section = chunk.getOrCreate(0);
                    MatterSlice<NativeStructureOwnershipBundle> slice =
                            section.slice(NativeStructureOwnershipBundle.class);
                    NativeStructureOwnershipBundle bundle = slice.get(0, 0, 0);
                    NativeStructureOwnershipBundle updated = Objects.requireNonNullElseGet(
                            bundle, NativeStructureOwnershipBundle::empty).with(record);
                    slice.set(0, 0, 0, updated);
                    pendingBundles.put(new PendingTarget(owner, target), updated);
                }
            } finally {
                chunk.release();
            }
        }

        @Override
        public void remove(long target, String structureKey,
                           int originChunkX, int originChunkZ) {
            RuntimeMantleOwner owner = owner();
            int targetChunkX = unpackX(target);
            int targetChunkZ = unpackZ(target);
            MantleChunk<Matter> chunk = owner.mantle().getMantle().getChunk(targetChunkX, targetChunkZ).use();
            try {
                synchronized (chunk) {
                    Matter section = chunk.getOrCreate(0);
                    MatterSlice<NativeStructureOwnershipBundle> slice =
                            section.getSlice(NativeStructureOwnershipBundle.class);
                    if (slice == null) {
                        return;
                    }
                    NativeStructureOwnershipBundle bundle = slice.get(0, 0, 0);
                    if (bundle == null) {
                        return;
                    }
                    NativeStructureOwnershipBundle updated = bundle.without(
                            structureKey, originChunkX, originChunkZ);
                    if (updated.equals(bundle)) {
                        return;
                    }
                    slice.set(0, 0, 0, updated.records().isEmpty() ? null : updated);
                    section.trimSlices();
                    pendingBundles.put(new PendingTarget(owner, target), updated);
                }
            } finally {
                chunk.release();
            }
        }

        @Override
        public boolean flush() {
            return flushMatching(null);
        }

        @Override
        public void close() {
            IrisEngine activeRetirementEngine = retirementEngine == null ? null : retirementEngine.get();
            if (activeRetirementEngine != null) {
                activeRetirementEngine.removeGenerationRuntimeRetirementListener(retirementListener);
            }
        }

        private void flushRuntime(int runtimeId) {
            if (!flushMatching(runtimeId)) {
                throw new IllegalStateException("Cannot retire generation runtime " + runtimeId
                        + " while native structure ownership regions are in use.");
            }
        }

        private boolean flushMatching(Integer runtimeId) {
            List<Map.Entry<PendingTarget, NativeStructureOwnershipBundle>> pending = new ArrayList<>();
            for (Map.Entry<PendingTarget, NativeStructureOwnershipBundle> entry : pendingBundles.entrySet()) {
                if (runtimeId == null || entry.getKey().owner().runtimeId() == runtimeId) {
                    pending.add(Map.entry(entry.getKey(), entry.getValue()));
                }
            }
            if (pending.isEmpty()) {
                return true;
            }
            pending.sort(Comparator
                    .comparingLong((Map.Entry<PendingTarget, NativeStructureOwnershipBundle> entry) ->
                            entry.getKey().owner().activationId())
                    .thenComparingInt(entry -> entry.getKey().owner().runtimeId())
                    .thenComparingLong(entry -> entry.getKey().target()));

            Map<RuntimeMantleOwner, Set<Long>> regionsByOwner = new LinkedHashMap<>();
            for (Map.Entry<PendingTarget, NativeStructureOwnershipBundle> entry : pending) {
                PendingTarget pendingTarget = entry.getKey();
                RuntimeMantleOwner owner = pendingTarget.owner();
                restorePendingBundle(owner.mantle().getMantle(), pendingTarget.target(), entry.getValue());
                regionsByOwner.computeIfAbsent(owner, ignored -> new TreeSet<>()).add(
                        regionKey(pendingTarget.target()));
            }

            boolean complete = true;
            for (Map.Entry<RuntimeMantleOwner, Set<Long>> ownerRegions : regionsByOwner.entrySet()) {
                RuntimeMantleOwner owner = ownerRegions.getKey();
                Set<Long> deferredRegions = owner.mantle().getMantle().saveIdleTectonicPlates(
                        ownerRegions.getValue());
                if (!deferredRegions.isEmpty()) {
                    complete = false;
                }
                for (Map.Entry<PendingTarget, NativeStructureOwnershipBundle> entry : pending) {
                    PendingTarget pendingTarget = entry.getKey();
                    if (!pendingTarget.owner().equals(owner)
                            || deferredRegions.contains(regionKey(pendingTarget.target()))) {
                        continue;
                    }
                    pendingBundles.remove(pendingTarget, entry.getValue());
                }
            }
            return complete;
        }

        private void restorePendingBundle(Mantle<Matter> mantle, long target,
                                          NativeStructureOwnershipBundle bundle) {
            MantleChunk<Matter> chunk = mantle.getChunk(unpackX(target), unpackZ(target)).use();
            try {
                synchronized (chunk) {
                    Matter section = chunk.getOrCreate(0);
                    MatterSlice<NativeStructureOwnershipBundle> slice =
                            section.slice(NativeStructureOwnershipBundle.class);
                    slice.set(0, 0, 0, bundle.records().isEmpty() ? null : bundle);
                    section.trimSlices();
                }
            } finally {
                chunk.release();
            }
        }

        private RuntimeMantleOwner owner() {
            Engine active = engine.get();
            if (active == null) {
                throw new IllegalStateException("Native structure ownership engine is unavailable");
            }
            EngineMantle mantle = active.getMantle();
            int runtimeId = active.getCacheID();
            if (!(active instanceof IrisEngine irisEngine)) {
                return new RuntimeMantleOwner(0L, runtimeId, null, mantle);
            }
            Optional<GenerationHistoryRuntimeRouter> attached = irisEngine.getGenerationHistoryRuntimeRouter();
            if (attached.isEmpty()) {
                return new RuntimeMantleOwner(0L, runtimeId, null, mantle);
            }
            GenerationHistoryRuntimeRouter.RuntimeOwnership routed = attached.get()
                    .currentRuntimeOwnership()
                    .orElseThrow(() -> new IllegalStateException(
                            "Native structure ownership requires an active generation-history runtime route."));
            if (routed.binding().runtimeId() != runtimeId) {
                throw new IllegalStateException("Native structure ownership runtime route changed during capture.");
            }
            return new RuntimeMantleOwner(routed.activationId(), runtimeId, routed.binding(), mantle);
        }

        private static long regionKey(long target) {
            return Mantle.key(unpackX(target) >> 5, unpackZ(target) >> 5);
        }
    }

    private record RuntimeMantleOwner(
            long activationId,
            int runtimeId,
            IrisEngine.GenerationRuntimeBinding binding,
            EngineMantle mantle
    ) {
        private RuntimeMantleOwner {
            if (activationId < 0L) {
                throw new IllegalArgumentException("Generation activation ID cannot be negative.");
            }
            Objects.requireNonNull(mantle, "native structure ownership mantle");
            if (activationId > 0L && binding == null) {
                throw new IllegalArgumentException("Generation-history ownership requires a runtime binding.");
            }
        }
    }

    private record PendingTarget(RuntimeMantleOwner owner, long target) {
        private PendingTarget {
            Objects.requireNonNull(owner, "native structure ownership runtime");
        }
    }

    private record Authority(NativeStructureOwnershipRecord record) {
        private static Authority present(NativeStructureOwnershipRecord record) {
            return new Authority(Objects.requireNonNull(record,
                    "Native structure ownership authority must not be null"));
        }

        private static Authority absent() {
            return new Authority(null);
        }
    }

    static long pack(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    static int unpackX(long packed) {
        return (int) packed;
    }

    static int unpackZ(long packed) {
        return (int) (packed >>> 32);
    }
}
