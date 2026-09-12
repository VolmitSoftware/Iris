package art.arcane.iris.generation.runtime;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EngineTelemetrySnapshot(
        long sampledAtMs,
        String worldIdentity,
        String worldName,
        String dimensionKey,
        boolean active,
        boolean studio,
        boolean closing,
        boolean failed,
        long loadedChunks,
        long loadedEntities,
        double entitySaturation,
        long generatedSession,
        long generatedTotal,
        double chunksPerSecond,
        long blockUpdatesPerSecond,
        int parallelism,
        int activeGenerationLeases,
        long hotloadsTotal,
        long mantleResidentPlates,
        long mantleQueuedPlates,
        double mantleIdleMs,
        Map<String, Double> generationTimingsMs
) {
    public EngineTelemetrySnapshot {
        if (worldIdentity == null || worldIdentity.isBlank()) {
            throw new IllegalArgumentException("Engine telemetry requires a world identity");
        }
        worldIdentity = worldIdentity.trim();
        worldName = worldName == null || worldName.isBlank() ? worldIdentity : worldName.trim();
        dimensionKey = dimensionKey == null ? "" : dimensionKey.trim();
        loadedChunks = Math.max(0L, loadedChunks);
        loadedEntities = Math.max(0L, loadedEntities);
        entitySaturation = finiteNonNegative(entitySaturation);
        generatedSession = Math.max(0L, generatedSession);
        generatedTotal = Math.max(0L, generatedTotal);
        chunksPerSecond = finiteNonNegative(chunksPerSecond);
        blockUpdatesPerSecond = Math.max(0L, blockUpdatesPerSecond);
        parallelism = Math.max(0, parallelism);
        activeGenerationLeases = Math.max(0, activeGenerationLeases);
        hotloadsTotal = Math.max(0L, hotloadsTotal);
        mantleResidentPlates = Math.max(0L, mantleResidentPlates);
        mantleQueuedPlates = Math.max(0L, mantleQueuedPlates);
        mantleIdleMs = finiteNonNegative(mantleIdleMs);
        generationTimingsMs = sanitizeTimings(generationTimingsMs);
    }

    public static EngineTelemetrySnapshot capture(Engine engine, double chunksPerSecond, long sampledAtMs) {
        if (engine == null) {
            throw new IllegalArgumentException("Engine cannot be null");
        }

        EngineWorldManager worldManager = engine.getWorldManager();
        GenerationSessionManager sessions = engine.getGenerationSessions();
        IrisEngineStatistics statistics = engine.getEngineData().getStatistics();
        boolean closing = engine.isClosing();
        boolean closed = engine.isClosed();
        boolean failed = engine.hasFailed();
        return new EngineTelemetrySnapshot(
                sampledAtMs,
                engine.getWorld().identity(),
                engine.getWorld().name(),
                engine.getDimension().getLoadKey(),
                !closing && !closed && !failed,
                engine.isStudio(),
                closing,
                failed,
                worldManager == null ? 0L : worldManager.getChunkCount(),
                worldManager == null ? 0L : worldManager.getEntityCount(),
                worldManager == null ? 0D : worldManager.getEntitySaturation(),
                engine.getGenerated(),
                statistics == null ? 0L : statistics.getChunksGenerated(),
                chunksPerSecond,
                engine.getBlockUpdatesPerSecond(),
                engine.getParallelism(),
                sessions == null ? 0 : sessions.activeLeases(),
                statistics == null ? 0L : statistics.getTotalHotloads(),
                engine.getMantle().getLoadedRegionCount(),
                engine.getMantle().getUnloadRegionCount(),
                engine.getMantle().getAdjustedIdleDuration(),
                engine.getMetrics().telemetryAverages()
        );
    }

    public static Aggregate aggregate(List<EngineTelemetrySnapshot> snapshots) {
        List<EngineTelemetrySnapshot> safeSnapshots = snapshots == null ? List.of() : snapshots;
        int active = 0;
        int studio = 0;
        int closing = 0;
        int failed = 0;
        long loadedChunks = 0L;
        long loadedEntities = 0L;
        double entitySaturation = 0D;
        long generatedSession = 0L;
        long generatedTotal = 0L;
        double chunksPerSecond = 0D;
        long blockUpdatesPerSecond = 0L;
        int parallelism = 0;
        int activeGenerationLeases = 0;
        long hotloadsTotal = 0L;
        long mantleResidentPlates = 0L;
        long mantleQueuedPlates = 0L;
        double mantleIdleTotal = 0D;
        double mantleIdleMax = 0D;
        double mantleIdleMin = Double.POSITIVE_INFINITY;
        int mantleIdleCount = 0;
        int worldCount = 0;
        Map<String, Double> timingMaxima = new LinkedHashMap<>();

        for (EngineTelemetrySnapshot snapshot : safeSnapshots) {
            if (snapshot == null) {
                continue;
            }
            worldCount++;
            active += snapshot.active() ? 1 : 0;
            studio += snapshot.studio() ? 1 : 0;
            closing += snapshot.closing() ? 1 : 0;
            failed += snapshot.failed() ? 1 : 0;
            loadedChunks += snapshot.loadedChunks();
            loadedEntities += snapshot.loadedEntities();
            entitySaturation = Math.max(entitySaturation, snapshot.entitySaturation());
            generatedSession += snapshot.generatedSession();
            generatedTotal += snapshot.generatedTotal();
            chunksPerSecond += snapshot.chunksPerSecond();
            blockUpdatesPerSecond += snapshot.blockUpdatesPerSecond();
            parallelism += snapshot.parallelism();
            activeGenerationLeases += snapshot.activeGenerationLeases();
            hotloadsTotal += snapshot.hotloadsTotal();
            mantleResidentPlates += snapshot.mantleResidentPlates();
            mantleQueuedPlates += snapshot.mantleQueuedPlates();
            mantleIdleTotal += snapshot.mantleIdleMs();
            mantleIdleMax = Math.max(mantleIdleMax, snapshot.mantleIdleMs());
            mantleIdleMin = Math.min(mantleIdleMin, snapshot.mantleIdleMs());
            mantleIdleCount++;
            for (Map.Entry<String, Double> entry : snapshot.generationTimingsMs().entrySet()) {
                timingMaxima.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }

        return new Aggregate(
                worldCount,
                active,
                studio,
                closing,
                failed,
                loadedChunks,
                loadedEntities,
                entitySaturation,
                generatedSession,
                generatedTotal,
                chunksPerSecond,
                blockUpdatesPerSecond,
                parallelism,
                activeGenerationLeases,
                hotloadsTotal,
                mantleResidentPlates,
                mantleQueuedPlates,
                mantleIdleCount == 0 ? 0D : mantleIdleTotal / mantleIdleCount,
                mantleIdleMax,
                mantleIdleCount == 0 ? 0D : mantleIdleMin,
                Map.copyOf(timingMaxima)
        );
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0D ? value : 0D;
    }

    private static Map<String, Double> sanitizeTimings(Map<String, Double> timings) {
        if (timings == null || timings.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> sanitized = new LinkedHashMap<>(timings.size());
        for (Map.Entry<String, Double> entry : timings.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value) || value < 0D) {
                continue;
            }
            sanitized.put(key, value);
        }
        return Map.copyOf(sanitized);
    }

    public record Aggregate(
            int worldCount,
            int active,
            int studio,
            int closing,
            int failed,
            long loadedChunks,
            long loadedEntities,
            double entitySaturationMax,
            long generatedSession,
            long generatedTotal,
            double chunksPerSecond,
            long blockUpdatesPerSecond,
            int parallelism,
            int activeGenerationLeases,
            long hotloadsTotal,
            long mantleResidentPlates,
            long mantleQueuedPlates,
            double mantleIdleAverageMs,
            double mantleIdleMaxMs,
            double mantleIdleMinMs,
            Map<String, Double> generationTimingMaximaMs
    ) {
        public Aggregate {
            generationTimingMaximaMs = generationTimingMaximaMs == null
                    ? Map.of()
                    : Map.copyOf(generationTimingMaximaMs);
        }
    }
}
