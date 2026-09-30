package art.arcane.iris.generation.runtime;

final class GenerationRateWindow {
    static final long TIMING_IDLE_MILLIS = 10_000L;

    private long lastSampleAtMs;
    private int lastGeneratedCount;
    private long lastGenerationAtMs;

    double sampleChunksPerSecond(int generatedCount, long sampledAtMs) {
        int safeGeneratedCount = Math.max(0, generatedCount);
        long previousAt = lastSampleAtMs;
        int previousCount = lastGeneratedCount;
        lastSampleAtMs = sampledAtMs;
        lastGeneratedCount = safeGeneratedCount;
        if (previousAt <= 0L) {
            if (safeGeneratedCount > 0) {
                lastGenerationAtMs = sampledAtMs;
            }
            return 0D;
        }
        if (sampledAtMs <= previousAt) {
            return 0D;
        }
        int generatedDelta = Math.max(0, safeGeneratedCount - previousCount);
        if (generatedDelta > 0) {
            lastGenerationAtMs = sampledAtMs;
        }
        return generatedDelta * 1000D / (sampledAtMs - previousAt);
    }

    boolean generationTimingsFresh(long sampledAtMs) {
        return lastGenerationAtMs > 0L && sampledAtMs - lastGenerationAtMs <= TIMING_IDLE_MILLIS;
    }
}
