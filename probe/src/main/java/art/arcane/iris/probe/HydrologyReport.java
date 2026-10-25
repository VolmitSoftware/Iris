package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.WindowKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

final class HydrologyReport {
    static final double POND_LEVEL_TILES = 9D;
    private static final int EXAMPLES_PER_KIND = 8;

    private HydrologyReport() {
    }

    record WindowMetrics(WindowKey key, double coldFirstChunkMs, long coldNaturalSamples, long coldBaseSamples, double windowCpuMs,
                         double regionCpuMs, int maxDependencyDepth, double landTiles, int rivers, double streamBlocks, int ponds,
                         Set<Integer> pondLevels, int pondsPerched8, long containmentLeaks, long risingWaterSegments, long seamMismatches,
                         long tickConversionCells, long missingSurface, long bermOverCap, int inlets, long inletFailures, int cavePools, long cavePoolFailures,
                         long generatedChunks, String planDigest) {
        String line() {
            return String.format(Locale.ROOT, "IRIS_HYDROLOGY_WINDOW wx=%d wz=%d coldFirstChunkMs=%.1f windowCpuMs=%.1f regionCpuMs=%.1f "
                            + "maxDependencyDepth=%d riversPerTile=%.2f streamBlocksPerTile=%.0f pondsPerTile=%.2f pondLevelsDistinct=%d "
                            + "pondsPerched8=%d containmentLeaks=%d risingWaterSegments=%d seamMismatches=%d tickConversionCells=%d "
                            + "missingSurface=%d bermOverCap=%d inlets=%d inletFailures=%d cavePools=%d cavePoolFailures=%d landTiles=%.3f rivers=%d ponds=%d "
                            + "coldNaturalSamples=%d coldBaseSamples=%d generatedChunks=%d planDigest=%s",
                    key.wx(), key.wz(), coldFirstChunkMs, windowCpuMs, regionCpuMs, maxDependencyDepth, perTile(rivers, landTiles),
                    perTile(streamBlocks, landTiles), perTile(ponds, landTiles), pondLevels.size(), pondsPerched8, containmentLeaks,
                    risingWaterSegments, seamMismatches, tickConversionCells, missingSurface, bermOverCap, inlets, inletFailures, cavePools, cavePoolFailures,
                    landTiles, rivers, ponds, coldNaturalSamples, coldBaseSamples, generatedChunks, planDigest);
        }
    }

    record Result(List<WindowMetrics> windows, double regionCpuMs, int regionsPlanned, int windowsPlanned, long naturalSamples,
                  long baseSamples, double warmupMs, long generationErrors, String planDigest, List<String> failures, List<String> examples) {
        boolean passes() {
            return maxDependencyDepth() == 0 && sum(WindowMetrics::containmentLeaks) == 0 && sum(WindowMetrics::risingWaterSegments) == 0
                    && sum(WindowMetrics::seamMismatches) == 0 && sum(WindowMetrics::tickConversionCells) == 0
                    && sum(WindowMetrics::missingSurface) == 0
                    && sum(WindowMetrics::inletFailures) == 0 && sum(WindowMetrics::cavePoolFailures) == 0 && generationErrors == 0
                    && failures.isEmpty();
        }

        Result withGenerationErrors(long errors) {
            return new Result(windows, regionCpuMs, regionsPlanned, windowsPlanned, naturalSamples, baseSamples, warmupMs, generationErrors + errors,
                    planDigest, failures, examples);
        }

        int maxDependencyDepth() {
            int deepest = 0;
            for (WindowMetrics window : windows) {
                deepest = Math.max(deepest, window.maxDependencyDepth());
            }
            return deepest;
        }

        long sum(Function<WindowMetrics, Long> field) {
            long total = 0L;
            for (WindowMetrics window : windows) {
                total += field.apply(window);
            }
            return total;
        }

        double coldMax() {
            double max = 0D;
            for (WindowMetrics window : windows) {
                max = Math.max(max, window.coldFirstChunkMs());
            }
            return max;
        }

        double coldMedian() {
            double[] values = new double[windows.size()];
            for (int index = 0; index < values.length; index++) {
                values[index] = windows.get(index).coldFirstChunkMs();
            }
            Arrays.sort(values);
            return values.length == 0 ? 0D : values[values.length / 2];
        }

        List<String> missedTargets() {
            List<String> missed = new ArrayList<>();
            if (maxDependencyDepth() != 0) {
                missed.add("maxDependencyDepth");
            }
            if (perTile(riversTotal(), landTiles()) < 1D) {
                missed.add("riversPerTile");
            }
            if (perTile(pondsTotal(), landTiles()) < 6D) {
                missed.add("pondsPerTile");
            }
            if (POND_LEVEL_TILES * perTile(pondLevels().size(), landTiles()) < 6D) {
                missed.add("pondLevelsDistinct");
            }
            for (String gate : List.of("containmentLeaks", "risingWaterSegments", "seamMismatches", "tickConversionCells", "missingSurface",
                    "bermOverCap", "inletFailures", "cavePoolFailures")) {
                if (gate(gate) != 0L) {
                    missed.add(gate);
                }
            }
            return missed;
        }

        private long gate(String name) {
            return switch (name) {
                case "containmentLeaks" -> sum(WindowMetrics::containmentLeaks);
                case "risingWaterSegments" -> sum(WindowMetrics::risingWaterSegments);
                case "seamMismatches" -> sum(WindowMetrics::seamMismatches);
                case "tickConversionCells" -> sum(WindowMetrics::tickConversionCells);
                case "missingSurface" -> sum(WindowMetrics::missingSurface);
                case "bermOverCap" -> sum(WindowMetrics::bermOverCap);
                case "inletFailures" -> sum(WindowMetrics::inletFailures);
                case "cavePoolFailures" -> sum(WindowMetrics::cavePoolFailures);
                default -> throw new IllegalArgumentException(name);
            };
        }

        double landTiles() {
            double total = 0D;
            for (WindowMetrics window : windows) {
                total += window.landTiles();
            }
            return total;
        }

        int riversTotal() {
            int total = 0;
            for (WindowMetrics window : windows) {
                total += window.rivers();
            }
            return total;
        }

        int pondsTotal() {
            int total = 0;
            for (WindowMetrics window : windows) {
                total += window.ponds();
            }
            return total;
        }

        Set<Integer> pondLevels() {
            Set<Integer> levels = new TreeSet<>();
            for (WindowMetrics window : windows) {
                levels.addAll(window.pondLevels());
            }
            return levels;
        }

        List<String> lines() {
            List<String> lines = new ArrayList<>();
            for (WindowMetrics window : windows) {
                lines.add(window.line());
            }
            double streamBlocks = 0D;
            double windowCpu = 0D;
            int perched = 0;
            long generated = 0L;
            int inlets = 0;
            int cavePools = 0;
            for (WindowMetrics window : windows) {
                streamBlocks += window.streamBlocks();
                windowCpu += window.windowCpuMs();
                perched += window.pondsPerched8();
                generated += window.generatedChunks();
                inlets += window.inlets();
                cavePools += window.cavePools();
            }
            List<String> missed = missedTargets();
            lines.add(String.format(Locale.ROOT, "IRIS_HYDROLOGY_TOTAL version=1 status=%s windows=%d coldFirstChunkMs=%.1f "
                            + "coldFirstChunkMedianMs=%.1f windowCpuMs=%.1f regionCpuMs=%.1f maxDependencyDepth=%d riversPerTile=%.2f "
                            + "streamBlocksPerTile=%.0f pondsPerTile=%.2f pondLevelsDistinct=%d pondsPerched8=%d containmentLeaks=%d "
                            + "risingWaterSegments=%d seamMismatches=%d tickConversionCells=%d missingSurface=%d bermOverCap=%d inlets=%d inletFailures=%d "
                            + "cavePools=%d cavePoolFailures=%d landTiles=%.3f "
                            + "rivers=%d ponds=%d windowsPlanned=%d regionsPlanned=%d naturalSamples=%d baseSamples=%d coldWarmupMs=%.1f "
                            + "generatedChunks=%d generationErrors=%d failures=%d planDigest=%s missedTargets=%s",
                    passes() ? "PASS" : "FAIL", windows.size(), coldMax(), coldMedian(), windows.isEmpty() ? 0D : windowCpu / windows.size(),
                    regionCpuMs, maxDependencyDepth(), perTile(riversTotal(), landTiles()), perTile(streamBlocks, landTiles()),
                    perTile(pondsTotal(), landTiles()), pondLevels().size(), perched, sum(WindowMetrics::containmentLeaks),
                    sum(WindowMetrics::risingWaterSegments), sum(WindowMetrics::seamMismatches), sum(WindowMetrics::tickConversionCells),
                    sum(WindowMetrics::missingSurface),
                    sum(WindowMetrics::bermOverCap), inlets, sum(WindowMetrics::inletFailures), cavePools, sum(WindowMetrics::cavePoolFailures),
                    landTiles(), riversTotal(), pondsTotal(), windowsPlanned, regionsPlanned, naturalSamples,
                    baseSamples, warmupMs, generated, generationErrors, failures.size(), planDigest, missed.isEmpty() ? "none" : String.join(",", missed)));
            for (String failure : failures) {
                lines.add("IRIS_HYDROLOGY_FAILURE " + failure);
            }
            for (String example : examples) {
                lines.add("IRIS_HYDROLOGY_EXAMPLE " + example);
            }
            return lines;
        }
    }

    static final class Examples implements Consumer<String> {
        private final List<String> values = new ArrayList<>();
        private final Map<String, Integer> counts = new HashMap<>();

        @Override
        public synchronized void accept(String value) {
            String kind = value.substring(0, Math.max(0, value.indexOf(' ')));
            if (counts.merge(kind, 1, Integer::sum) <= EXAMPLES_PER_KIND) {
                values.add(value);
            }
        }

        synchronized List<String> values() {
            return List.copyOf(values);
        }
    }

    static double perTile(double count, double landTiles) {
        return landTiles <= 0D ? 0D : count / landTiles;
    }
}
