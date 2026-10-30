package art.arcane.iris.probe.fidelity;

import art.arcane.iris.generation.validation.ChunkSpiralScan;
import art.arcane.volmlib.util.math.ChunkSpiral;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class FidelityScanner {
    private FidelityScanner() {
    }

    public static FidelityStats scan(FidelitySource source, int centerChunkX, int centerChunkZ, int radiusChunks, int stride, int threads) {
        Objects.requireNonNull(source, "source");
        FidelityStats.requireStride(stride);
        List<int[]> targets = ChunkSpiral.centerOut(centerChunkX, centerChunkZ, Math.max(0, radiusChunks));
        Accumulator accumulator = new Accumulator(source, stride);
        int failures = runScan(source, targets, threads, accumulator);
        if (failures > 0) {
            throw new IllegalStateException(failures + " of " + targets.size() + " chunks could not be sampled from "
                    + source.label(), accumulator.firstFailure());
        }
        return accumulator.stats();
    }

    private static int runScan(FidelitySource source, List<int[]> targets, int threads, Accumulator accumulator) {
        try {
            return ChunkSpiralScan.scan(targets, threads, accumulator::sample, accumulator);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fidelity scan of " + source.label() + " was interrupted", e);
        }
    }

    private static final class Accumulator implements ChunkSpiralScan.Listener {
        private final FidelitySource source;
        private final int stride;
        private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        private FidelityStats stats;

        private Accumulator(FidelitySource source, int stride) {
            this.source = source;
            this.stride = stride;
            this.stats = new FidelityStats(source.minY(), source.maxY());
        }

        @Override
        public void chunkDone(int chunkX, int chunkZ, boolean ok) {
        }

        @Override
        public void chunkFailed(int chunkX, int chunkZ, Throwable error) {
            firstFailure.compareAndSet(null, error);
        }

        private void sample(int chunkX, int chunkZ) throws Exception {
            FidelityStats chunkStats = new FidelityStats(source.minY(), source.maxY());
            chunkStats.accept(source.sample(chunkX, chunkZ), stride);
            merge(chunkStats);
        }

        private synchronized void merge(FidelityStats chunkStats) {
            stats = stats.merge(chunkStats);
        }

        private synchronized FidelityStats stats() {
            return stats;
        }

        private Throwable firstFailure() {
            return firstFailure.get();
        }
    }
}
