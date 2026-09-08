package art.arcane.iris.engine.hydrology;

import art.arcane.iris.spi.IrisLogging;

/**
 * Wall-clock breakdown of one owner draft for the debug log: sampling and routing context, source
 * selection, settling, publication passes, and the time spent waiting on lower-rank neighbour drafts.
 */
final class DraftProfile {
    final long[] phaseNanos = new long[DraftPhase.values().length];
    long dependencyNanos;
    int dependencies;
    int admissions;
    int earlyOwners;
    int routeCalls;
    int routeSolves;
    long routeSolveNanos;
    int rasterCalls;
    long rasterNanos;
    int filterCalls;
    long filterNanos;

    long record(DraftPhase phase, long started) {
        long now = System.nanoTime();
        phaseNanos[phase.ordinal()] += now - started;
        return now;
    }

    void recordDependencies(int count, long started) {
        dependencyNanos += System.nanoTime() - started;
        dependencies += count;
        admissions++;
    }

    void log(HydrologyTileKey key, int rank, long totalNanos) {
        IrisLogging.debug(
                "Hydrology owner %d,%d rank=%d drafted in %dms: context=%dms select=%dms settle=%dms publish=%dms deps=%d wait=%dms admissions=%d earlyOwners=%d routes=%d reuses=%d routeSolve=%dms rasters=%d raster=%dms filters=%d filter=%dms on %s",
                key.tileX(),
                key.tileZ(),
                rank,
                totalNanos / 1_000_000L,
                phaseNanos[DraftPhase.CONTEXT.ordinal()] / 1_000_000L,
                phaseNanos[DraftPhase.SELECT.ordinal()] / 1_000_000L,
                phaseNanos[DraftPhase.SETTLE.ordinal()] / 1_000_000L,
                phaseNanos[DraftPhase.PUBLISH.ordinal()] / 1_000_000L,
                dependencies,
                dependencyNanos / 1_000_000L,
                admissions,
                earlyOwners,
                routeCalls,
                routeCalls - routeSolves,
                routeSolveNanos / 1_000_000L,
                rasterCalls,
                rasterNanos / 1_000_000L,
                filterCalls,
                filterNanos / 1_000_000L,
                Thread.currentThread().getName()
        );
    }
}
