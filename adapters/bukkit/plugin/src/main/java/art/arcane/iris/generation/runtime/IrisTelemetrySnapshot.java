package art.arcane.iris.generation.runtime;


import java.util.List;

public record IrisTelemetrySnapshot(
        long sampledAtMs,
        List<EngineTelemetrySnapshot> worlds,
        EngineTelemetrySnapshot.Aggregate aggregate,
        int maintenanceActiveTasks,
        int maintenanceWorkers,
        int closingEngines,
        int pendingRegistrations,
        double heapUsage,
        double reclaimUrgency,
        CacheSnapshot caches,
        PregenSnapshot pregenerator
) {
    public static final IrisTelemetrySnapshot EMPTY = new IrisTelemetrySnapshot(
            0L,
            List.of(),
            EngineTelemetrySnapshot.aggregate(List.of()),
            0,
            0,
            0,
            0,
            0D,
            0D,
            CacheSnapshot.EMPTY,
            PregenSnapshot.INACTIVE
    );

    public IrisTelemetrySnapshot {
        worlds = worlds == null ? List.of() : List.copyOf(worlds);
        aggregate = aggregate == null ? EngineTelemetrySnapshot.aggregate(worlds) : aggregate;
        maintenanceActiveTasks = Math.max(0, maintenanceActiveTasks);
        maintenanceWorkers = Math.max(0, maintenanceWorkers);
        closingEngines = Math.max(0, closingEngines);
        pendingRegistrations = Math.max(0, pendingRegistrations);
        heapUsage = finiteRatio(heapUsage);
        reclaimUrgency = finiteRatio(reclaimUrgency);
        caches = caches == null ? CacheSnapshot.EMPTY : caches;
        pregenerator = pregenerator == null ? PregenSnapshot.INACTIVE : pregenerator;
    }

    private static double finiteRatio(double value) {
        return Double.isFinite(value) ? Math.max(0D, Math.min(1D, value)) : 0D;
    }

    public record CacheSnapshot(
            CacheBucket total,
            CacheBucket resource,
            CacheBucket stream2d,
            CacheBucket stream3d,
            CacheBucket other
    ) {
        public static final CacheSnapshot EMPTY = new CacheSnapshot(
                CacheBucket.EMPTY,
                CacheBucket.EMPTY,
                CacheBucket.EMPTY,
                CacheBucket.EMPTY,
                CacheBucket.EMPTY
        );

        public CacheSnapshot {
            total = total == null ? CacheBucket.EMPTY : total;
            resource = resource == null ? CacheBucket.EMPTY : resource;
            stream2d = stream2d == null ? CacheBucket.EMPTY : stream2d;
            stream3d = stream3d == null ? CacheBucket.EMPTY : stream3d;
            other = other == null ? CacheBucket.EMPTY : other;
        }
    }

    public record CacheBucket(int count, long entries, long capacity) {
        public static final CacheBucket EMPTY = new CacheBucket(0, 0L, 0L);

        public CacheBucket {
            count = Math.max(0, count);
            entries = Math.max(0L, entries);
            capacity = Math.max(0L, capacity);
        }

        public double usage() {
            return capacity <= 0L ? 0D : Math.min(1D, entries / (double) capacity);
        }

        CacheBucket plus(CacheBucket other) {
            if (other == null) {
                return this;
            }
            return new CacheBucket(count + other.count, entries + other.entries, capacity + other.capacity);
        }
    }

    public record PregenSnapshot(
            boolean active,
            String worldIdentity,
            String worldName,
            boolean paused,
            double progressPercent,
            long generated,
            long total,
            long remaining,
            double chunksPerSecond,
            long etaMs,
            long elapsedMs,
            long failed
    ) {
        public static final PregenSnapshot INACTIVE = new PregenSnapshot(
                false,
                "",
                "",
                false,
                0D,
                0L,
                0L,
                0L,
                0D,
                0L,
                0L,
                0L
        );

        public PregenSnapshot {
            worldIdentity = worldIdentity == null ? "" : worldIdentity.trim();
            worldName = worldName == null ? "" : worldName.trim();
            progressPercent = Double.isFinite(progressPercent)
                    ? Math.max(0D, Math.min(100D, progressPercent))
                    : 0D;
            generated = Math.max(0L, generated);
            total = Math.max(0L, total);
            remaining = Math.max(0L, remaining);
            chunksPerSecond = Double.isFinite(chunksPerSecond) ? Math.max(0D, chunksPerSecond) : 0D;
            etaMs = Math.max(0L, etaMs);
            elapsedMs = Math.max(0L, elapsedMs);
            failed = Math.max(0L, failed);
        }
    }
}
