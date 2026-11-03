package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

final class ReachGeometry {
    static final int BRINK_MIN_DROP = 4;
    static final double BRINK_CURVE_MIN = 0.25D;
    static final double BRINK_CURVE_MAX = 0.6D;
    static final double BRINK_MIN_SHIFT = 1D;
    static final double BRINK_MAX_SHIFT = 11D;
    static final double EDGE_MARGIN = 1D;
    private static final long SALT_BRINK = 0x4252494e4bL;
    private static final ReachGeometry STRAIGHT = new ReachGeometry(new double[0], new int[0], new double[0], new double[0], new double[0]);

    private final double[] along;
    private final int[] segments;
    private final double[] middles;
    private final double[] shifts;
    private final double[] starts;

    private ReachGeometry(double[] along, int[] segments, double[] middles, double[] shifts, double[] starts) {
        this.along = along;
        this.segments = segments;
        this.middles = middles;
        this.shifts = shifts;
        this.starts = starts;
    }

    static ReachGeometry of(Reach reach, Reach linked, long seed, int seaLevel) {
        int[] grade = reach.grade();
        int[] steps = new int[grade.length - 1];
        int count = 0;
        boolean falls = false;
        for (int segment = 0; segment + 1 < grade.length; segment++) {
            if (WaterHeads.snap(grade[segment], seaLevel) != WaterHeads.snap(grade[segment + 1], seaLevel)) {
                steps[count++] = segment;
                falls |= grade[segment] - grade[segment + 1] >= BRINK_MIN_DROP;
            }
        }
        if (!falls) {
            return STRAIGHT;
        }
        double[] along = along(reach);
        double end = along[along.length - 1];
        double held = protectedEnd(reach, along);
        double mouth = firstMouth(reach, along);
        int[] segments = new int[count];
        double[] middles = new double[count];
        double[] shifts = new double[count];
        double[] starts = new double[count];
        int curves = 0;
        int start = WaterHeads.snap(grade[0], seaLevel);
        boolean outlet = reach.style()[0] == ReachStyle.POND_OUTLET;
        boolean feeder = reach.downstreamId() != 0L;
        int landing = WaterHeads.snap(grade[grade.length - 1], seaLevel);
        for (int step = 0; step < count; step++) {
            int segment = steps[step];
            double middle = middle(along, segment);
            int upper = WaterHeads.snap(grade[segment], seaLevel);
            int lower = WaterHeads.snap(grade[segment + 1], seaLevel);
            if (grade[segment] - grade[segment + 1] < BRINK_MIN_DROP || linked.grade()[segment] - linked.grade()[segment + 1] < BRINK_MIN_DROP || middle <= held || outlet && upper == start || feeder && lower == landing
                    || reach.style()[segment] == ReachStyle.MOUTH || reach.style()[segment + 1] == ReachStyle.MOUTH) {
                continue;
            }
            double width = (reach.halfWidth()[segment] + reach.halfWidth()[segment + 1]) / 2D;
            if (width - EDGE_MARGIN < BRINK_MIN_SHIFT) {
                continue;
            }
            long hash = CellHash.mix(seed, SALT_BRINK, Math.round((reach.xs()[segment] + reach.xs()[segment + 1]) / 2f),
                    Math.round((reach.zs()[segment] + reach.zs()[segment + 1]) / 2f));
            double curve = BRINK_CURVE_MIN + (BRINK_CURVE_MAX - BRINK_CURVE_MIN) * CellHash.unit(hash);
            double wanted = Math.min(BRINK_MAX_SHIFT, Math.max(BRINK_MIN_SHIFT, curve * (width - EDGE_MARGIN)));
            if (Arrays.binarySearch(reach.straightFalls(), segment) >= 0) {
                continue;
            }
            boolean tongue = (hash & 1L) == 0L;
            double room = tongue
                    ? Math.min(step + 1 < count ? (middle(along, steps[step + 1]) - middle) / 2D : end - middle, mouth - middle)
                    : Math.min(step > 0 ? (middle - middle(along, steps[step - 1])) / 2D : middle, middle - held);
            double shift = Math.min(wanted, room);
            if (shift > 0D) {
                segments[curves] = segment;
                middles[curves] = middle;
                shifts[curves] = tongue ? shift : -shift;
                starts[curves] = tongue ? middle : middle - shift;
                curves++;
            }
        }
        if (curves == 0) {
            return STRAIGHT;
        }
        return new ReachGeometry(along, Arrays.copyOf(segments, curves), Arrays.copyOf(middles, curves), Arrays.copyOf(shifts, curves),
                Arrays.copyOf(starts, curves));
    }

    int point(ReachCursor.Choice choice) {
        int straight = choice.point();
        if (segments.length == 0) {
            return straight;
        }
        double inner = choice.halfWidth() - EDGE_MARGIN;
        if (choice.distance >= inner) {
            return straight;
        }
        double at = along[choice.segment] + choice.t * (along[choice.segment + 1] - along[choice.segment]);
        int found = Arrays.binarySearch(starts, at);
        int curve = found >= 0 ? found : -found - 2;
        if (curve < 0 || at > starts[curve] + Math.abs(shifts[curve])) {
            return straight;
        }
        double ratio = choice.distance / inner;
        double moved = shifts[curve] * (1D - ratio * ratio);
        if (shifts[curve] > 0D) {
            return at - moved >= middles[curve] ? straight : segments[curve];
        }
        return at - moved > middles[curve] ? segments[curve] + 1 : straight;
    }

    int curvedFall(ReachCursor.Choice choice) {
        if (segments.length == 0 || point(choice) == choice.point()) {
            return -1;
        }
        double at = along[choice.segment] + choice.t * (along[choice.segment + 1] - along[choice.segment]);
        int found = Arrays.binarySearch(starts, at);
        int curve = found >= 0 ? found : -found - 2;
        return segments[curve];
    }

    int[] tongueFalls() {
        int[] falls = new int[segments.length];
        int count = 0;
        for (int curve = 0; curve < segments.length; curve++) {
            if (shifts[curve] > 0D) {
                falls[count++] = segments[curve];
            }
        }
        return Arrays.copyOf(falls, count);
    }

    double tongueEnd(int fall) {
        int curve = Arrays.binarySearch(segments, fall);
        return middles[curve] + shifts[curve];
    }

    double along(int point) {
        return along[point];
    }

    int lowestGrade(Reach reach, int segment) {
        int[] grade = reach.grade();
        int lowest = Math.min(grade[segment], grade[segment + 1]);
        for (int curve = 0; curve < segments.length; curve++) {
            if (shifts[curve] < 0D && along[segment] < middles[curve] && along[segment + 1] > starts[curve]) {
                lowest = Math.min(lowest, grade[segments[curve] + 1]);
            }
        }
        return lowest;
    }

    private static double[] along(Reach reach) {
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        double[] along = new double[xs.length];
        for (int point = 1; point < xs.length; point++) {
            double dx = xs[point] - xs[point - 1];
            double dz = zs[point] - zs[point - 1];
            along[point] = along[point - 1] + Math.sqrt(dx * dx + dz * dz);
        }
        return along;
    }

    private static double middle(double[] along, int segment) {
        return (along[segment] + along[segment + 1]) / 2D;
    }

    private static double protectedEnd(Reach reach, double[] along) {
        double end = reach.style()[0] == ReachStyle.POND_OUTLET
                ? Math.max(ReachShaper.OUTLET_LENGTH, 2D * (reach.halfWidth()[0] + 1D))
                : Double.NEGATIVE_INFINITY;
        for (int point = 0; point < along.length; point++) {
            if (reach.style()[point] == ReachStyle.POND_OUTLET) {
                end = Math.max(end, along[point]);
            }
        }
        return end;
    }

    private static double firstMouth(Reach reach, double[] along) {
        for (int point = 0; point < along.length; point++) {
            if (reach.style()[point] == ReachStyle.MOUTH) {
                return along[point];
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    static final class Lookup {
        private final Map<Reach, ReachGeometry> geometries = new IdentityHashMap<>();
        private final Map<Reach, Reach> plans;
        private final long seed;
        private final int seaLevel;

        Lookup(long seed, int seaLevel) {
            this(seed, seaLevel, Map.of());
        }

        Lookup(long seed, int seaLevel, Map<Reach, Reach> plans) {
            this.seed = seed;
            this.seaLevel = seaLevel;
            this.plans = plans;
        }

        ReachGeometry of(Reach reach) {
            ReachGeometry known = geometries.get(reach);
            if (known == null) {
                Reach plan = plans.get(reach);
                known = ReachGeometry.of(plan == null ? reach : plan, reach, seed, seaLevel);
                geometries.put(reach, known);
            }
            return known;
        }

        int head(ReachCursor.Choice choice) {
            return WaterHeads.channelHead(choice, of(choice.reach), seaLevel);
        }

        int point(ReachCursor.Choice choice) {
            return of(choice.reach).point(choice);
        }

        int lowestGrade(Reach reach, int segment) {
            return of(reach).lowestGrade(reach, segment);
        }
    }
}
