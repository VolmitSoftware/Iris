package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

final class HydrologyRegionalSourceTrials {
    private static final int LENGTH_BANDS = 4;

    private final HydrologySampledGrid grid;
    private final List<Catchment> catchments;
    private int cursor;

    HydrologyRegionalSourceTrials(HydrologySampledGrid grid, HydrologyRegionalGraph.Tree tree, List<Integer> sources) {
        this.grid = grid;
        this.catchments = new ArrayList<>(tree.outlets().size());
        ArrayList<Integer> sorted = new ArrayList<>(sources);
        sorted.sort(Comparator.comparingDouble((Integer source) -> tree.length(source)).reversed()
                .thenComparingLong(source -> grid.node(source).id()));
        for (int root = 0; root < tree.outlets().size(); root++) {
            Catchment catchment = new Catchment(tree.outlets().get(root).outlet().id());
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = 0D;
            for (int source : sorted) {
                if (tree.root(source) == root) {
                    minimum = Math.min(minimum, tree.length(source));
                    maximum = Math.max(maximum, tree.length(source));
                }
            }
            if (maximum == 0D) {
                continue;
            }
            double range = Math.max(1D, maximum - minimum);
            for (int source : sorted) {
                if (tree.root(source) == root) {
                    int band = Math.min(LENGTH_BANDS - 1,
                            (int) ((maximum - tree.length(source)) * LENGTH_BANDS / range));
                    catchment.bands.get(band).add(source);
                }
            }
            catchments.add(catchment);
        }
    }

    OptionalInt next(Set<Long> usedOutlets) {
        for (int checked = 0; checked < catchments.size(); checked++) {
            Catchment catchment = catchments.get(cursor++ % catchments.size());
            if (usedOutlets.contains(catchment.outlet)) {
                continue;
            }
            for (int band = 0; band < LENGTH_BANDS; band++) {
                List<Integer> candidates = catchment.bands.get(catchment.cursor++ % LENGTH_BANDS);
                if (!candidates.isEmpty()) {
                    int source = candidates.remove(farthest(candidates, catchment.attempted));
                    catchment.attempted.add(grid.node(source).naturalPoint());
                    return OptionalInt.of(source);
                }
            }
        }
        return OptionalInt.empty();
    }

    private int farthest(List<Integer> candidates, List<HydrologyPoint> attempted) {
        int selected = 0;
        long separation = -1L;
        for (int index = 0; index < candidates.size(); index++) {
            HydrologyPoint point = grid.node(candidates.get(index)).naturalPoint();
            long nearest = Long.MAX_VALUE;
            for (HydrologyPoint previous : attempted) {
                nearest = Math.min(nearest, point.distanceSquared2D(previous));
            }
            if (nearest > separation) {
                selected = index;
                separation = nearest;
            }
        }
        return selected;
    }

    private static final class Catchment {
        private final long outlet;
        private final List<List<Integer>> bands;
        private final List<HydrologyPoint> attempted;
        private int cursor;

        private Catchment(long outlet) {
            this.outlet = outlet;
            this.bands = new ArrayList<>(LENGTH_BANDS);
            this.attempted = new ArrayList<>();
            for (int band = 0; band < LENGTH_BANDS; band++) {
                bands.add(new ArrayList<>());
            }
        }
    }
}
