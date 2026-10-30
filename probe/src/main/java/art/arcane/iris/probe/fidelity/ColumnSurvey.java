package art.arcane.iris.probe.fidelity;

import art.arcane.iris.generation.validation.ChunkSpiralScan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ColumnSurvey {
    private ColumnSurvey() {
    }

    public static FidelityStats scan(ColumnSource source, SurveyWindow window, int threads) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(window, "window");
        List<int[]> rows = new ArrayList<>(window.perAxis());
        for (int row = 0; row < window.perAxis(); row++) {
            rows.add(new int[]{row, 0});
        }
        Accumulator accumulator = new Accumulator(source, window);
        int failures = runScan(source, rows, threads, accumulator);
        if (failures > 0) {
            throw new IllegalStateException(failures + " of " + rows.size() + " survey rows could not be sampled from "
                    + source.label(), accumulator.firstFailure());
        }
        return accumulator.stats();
    }

    private static int runScan(ColumnSource source, List<int[]> rows, int threads, Accumulator accumulator) {
        try {
            return ChunkSpiralScan.scan(rows, threads, accumulator::sample, accumulator);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Column survey of " + source.label() + " was interrupted", e);
        }
    }

    private static final class Accumulator implements ChunkSpiralScan.Listener {
        private final ColumnSource source;
        private final SurveyWindow window;
        private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        private FidelityStats stats;

        private Accumulator(ColumnSource source, SurveyWindow window) {
            this.source = source;
            this.window = window;
            this.stats = new FidelityStats(source.minY(), source.maxY());
        }

        @Override
        public void chunkDone(int row, int ignored, boolean ok) {
        }

        @Override
        public void chunkFailed(int row, int ignored, Throwable error) {
            firstFailure.compareAndSet(null, error);
        }

        private void sample(int row, int ignored) throws Exception {
            FidelityStats rowStats = new FidelityStats(source.minY(), source.maxY());
            int x = window.coordinate(window.centerX(), row);
            for (int column = 0; column < window.perAxis(); column++) {
                rowStats.accept(source.column(x, window.coordinate(window.centerZ(), column)));
            }
            merge(rowStats);
        }

        private synchronized void merge(FidelityStats rowStats) {
            stats = stats.merge(rowStats);
        }

        private synchronized FidelityStats stats() {
            return stats;
        }

        private Throwable firstFailure() {
            return firstFailure.get();
        }
    }
}
