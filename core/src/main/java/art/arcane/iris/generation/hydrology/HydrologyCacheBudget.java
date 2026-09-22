package art.arcane.iris.generation.hydrology;

final class HydrologyCacheBudget {
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final HydrologyCacheBudget REGIONAL_RUNTIME = forHeap(Runtime.getRuntime().maxMemory(), true);
    private static final HydrologyCacheBudget LOCAL_RUNTIME = forHeap(Runtime.getRuntime().maxMemory(), false);
    private final long plannerBytes;
    private final long publicationBytes;
    private final boolean regionalEnabled;

    private HydrologyCacheBudget(long retainedBytes, boolean regionalEnabled) {
        plannerBytes = retainedBytes / 2L;
        publicationBytes = retainedBytes - plannerBytes;
        this.regionalEnabled = regionalEnabled;
    }

    static HydrologyCacheBudget runtime() {
        return REGIONAL_RUNTIME;
    }

    static HydrologyCacheBudget runtime(boolean regionalEnabled) {
        return regionalEnabled ? REGIONAL_RUNTIME : LOCAL_RUNTIME;
    }

    static HydrologyCacheBudget forHeap(long maximumHeapBytes, boolean regionalEnabled) {
        if (maximumHeapBytes <= 0L) {
            throw new IllegalArgumentException("Maximum heap size must be positive.");
        }
        return new HydrologyCacheBudget(Math.clamp(maximumHeapBytes / 8L, 8L * MEBIBYTE, 256L * MEBIBYTE), regionalEnabled);
    }

    static long sharedStudioBytes() {
        return Math.clamp(Runtime.getRuntime().maxMemory() / 128L, MEBIBYTE, 16L * MEBIBYTE);
    }

    long tileBytes() {
        return publicationBytes * 3L / 4L;
    }

    long columnBytes() {
        return publicationBytes / 8L;
    }

    long diagnosticBytes() {
        return publicationBytes / 8L;
    }

    long routingBytes() {
        return plannerBytes * 3L / 16L;
    }

    long ownerBytes() {
        return regionalEnabled ? plannerBytes / 4L : plannerBytes * 3L / 4L;
    }

    long edgeBytes() {
        return plannerBytes / 16L;
    }

    long regionalDraftBytes() {
        return regionalEnabled ? plannerBytes / 8L : 0L;
    }

    long regionalCoarseBytes() {
        return regionalEnabled ? plannerBytes / 8L : 0L;
    }

    long regionalTerrainBytes() {
        return regionalEnabled ? plannerBytes / 4L : 0L;
    }

}
