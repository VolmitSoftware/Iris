package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.raster.NaturalHeights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class SeaApproach {
    static final float GENTLE_SLOPE = 0.125f;
    static final float STEEPEST_SLOPE = 0.25f;
    static final float LENGTH = 128f;
    static final float FIRST_STEP = 12f;
    static final float COAST_REACH = 64f;
    static final int CLIFF_HEIGHT = 16;
    static final float CLIFF_BANK_REACH = 16f;

    private final Map<Long, List<Reach>> feeders = new HashMap<>();
    private final Map<Reach, int[]> lowered = new IdentityHashMap<>();
    private final List<Pond> ponds;
    private final NaturalHeights natural;
    private final int seaLevel;
    private int base;
    private float slope;
    private float firstStep;

    private SeaApproach(List<Reach> reaches, List<Pond> ponds, NaturalHeights natural, int seaLevel) {
        this.ponds = ponds;
        this.natural = natural;
        this.seaLevel = seaLevel;
        for (Reach reach : reaches) {
            if (reach.downstreamId() != 0L) {
                feeders.computeIfAbsent(reach.downstreamId(), id -> new ArrayList<>()).add(reach);
            }
        }
    }

    public static List<Reach> apply(List<Reach> reaches, List<Pond> ponds, NaturalHeights natural, int seaLevel) {
        SeaApproach approach = new SeaApproach(reaches, ponds, natural, seaLevel);
        for (Reach reach : reaches) {
            int last = reach.style().length - 1;
            if (reach.downstreamId() == 0L && reach.style()[last] == ReachStyle.MOUTH) {
                approach.lowerToward(reach);
            }
        }
        if (approach.lowered.isEmpty()) {
            return reaches;
        }
        List<Reach> result = new ArrayList<>(reaches.size());
        for (Reach reach : reaches) {
            int[] grade = approach.lowered.get(reach);
            result.add(grade == null ? reach : rebuilt(reach, grade, seaLevel));
        }
        return result;
    }

    private void lowerToward(Reach mouth) {
        int start = estuaryStart(mouth);
        if (highCoast(mouth, start)) {
            return;
        }
        float width = mouth.halfWidth()[start];
        base = seaLevel + mouth.tier().stepHeight();
        firstStep = Math.max(FIRST_STEP, width);
        slope = slope(mouth, start, Math.min(GENTLE_SLOPE, 1f / Math.max(1f, width)));
        walk(mouth, start, 0f, mouth.grade()[start]);
    }

    private static int estuaryStart(Reach mouth) {
        int start = mouth.style().length - 1;
        while (start > 0 && mouth.style()[start - 1] == ReachStyle.MOUTH) {
            start--;
        }
        return start;
    }

    private boolean highCoast(Reach mouth, int start) {
        Reach reach = mouth;
        int point = start - 1;
        if (start == 0) {
            reach = largestFeeder(mouth);
            if (reach == null) {
                return false;
            }
            point = reach.xs().length - 2;
        }
        if (point < 0) {
            return false;
        }
        int next = Math.min(reach.xs().length - 1, point + 1);
        float dx = reach.xs()[next] - reach.xs()[point];
        float dz = reach.zs()[next] - reach.zs()[point];
        float span = Math.max(1e-3f, (float) Math.hypot(dx, dz));
        return bankPeak(reach, point, -dz / span, dx / span) - seaLevel >= CLIFF_HEIGHT
                && bankPeak(reach, point, dz / span, -dx / span) - seaLevel >= CLIFF_HEIGHT;
    }

    private double bankPeak(Reach reach, int point, float nx, float nz) {
        double peak = Double.NEGATIVE_INFINITY;
        float half = reach.halfWidth()[point];
        for (float out = half + 2f; out <= half + CLIFF_BANK_REACH; out += 2f) {
            peak = Math.max(peak, natural.height((int) Math.floor(reach.xs()[point] + nx * out), (int) Math.floor(reach.zs()[point] + nz * out)));
        }
        return peak;
    }

    private float slope(Reach mouth, int start, float gentle) {
        float rise = Float.POSITIVE_INFINITY;
        Reach reach = mouth;
        int point = start;
        float along = 0f;
        int below = mouth.grade()[start];
        while (along <= LENGTH) {
            if (point == 0) {
                reach = largestFeeder(reach);
                if (reach == null) {
                    break;
                }
                point = reach.xs().length - 1;
                continue;
            }
            along += distance(reach, point - 1, point);
            point--;
            int level = reach.grade()[point];
            if (stops(reach, point, along, level, below)) {
                break;
            }
            if (along > COAST_REACH) {
                rise = Math.min(rise, (level - base) / Math.max(1f, along - firstStep));
            }
            below = level;
        }
        return rise == Float.POSITIVE_INFINITY ? gentle : Math.clamp(rise, gentle, STEEPEST_SLOPE);
    }

    private void walk(Reach reach, int from, float start, int firstBelow) {
        float along = start;
        int below = firstBelow;
        for (int point = from - 1; point >= 0; point--) {
            along += distance(reach, point, point + 1);
            int level = reach.grade()[point];
            if (along > LENGTH || stops(reach, point, along, level, below)) {
                return;
            }
            int allowed = base + (int) Math.floor(slope * Math.max(0f, along - firstStep));
            if (level > allowed) {
                lowered.computeIfAbsent(reach, known -> known.grade().clone())[point] = allowed;
            } else if (along > COAST_REACH) {
                return;
            }
            below = level;
        }
        for (Reach feeder : feeders.getOrDefault(reach.id(), List.of())) {
            int end = feeder.grade().length - 1;
            int[] grade = lowered.get(reach);
            int ceiling = grade == null ? reach.grade()[0] : grade[0];
            if (feeder.grade()[end] > ceiling) {
                lowered.computeIfAbsent(feeder, known -> known.grade().clone())[end] = ceiling;
            }
            walk(feeder, end, along, feeder.grade()[end]);
        }
    }

    private boolean stops(Reach reach, int point, float along, int level, int below) {
        if (reach.style()[point] == ReachStyle.POND_OUTLET || besidePond(reach, point)) {
            return true;
        }
        if (level - below < reach.tier().waterfallMinDrop()) {
            return false;
        }
        return along > COAST_REACH || level - seaLevel >= CLIFF_HEIGHT;
    }

    private boolean besidePond(Reach reach, int point) {
        for (Pond pond : ponds) {
            if (ReachShaper.nearLip(pond.mask(), reach.xs()[point], reach.zs()[point], reach.halfWidth()[point])) {
                return true;
            }
        }
        return false;
    }

    private Reach largestFeeder(Reach reach) {
        Reach largest = null;
        for (Reach feeder : feeders.getOrDefault(reach.id(), List.of())) {
            if (largest == null || feeder.catchment() > largest.catchment()) {
                largest = feeder;
            }
        }
        return largest;
    }

    private static float distance(Reach reach, int from, int to) {
        return (float) Math.hypot(reach.xs()[to] - reach.xs()[from], reach.zs()[to] - reach.zs()[from]);
    }

    private static Reach rebuilt(Reach reach, int[] grade, int seaLevel) {
        int count = grade.length;
        boolean[] ocean = new boolean[count];
        boolean[] outlet = new boolean[count];
        for (int point = 0; point < count; point++) {
            ocean[point] = reach.style()[point] == ReachStyle.MOUTH;
            outlet[point] = reach.style()[point] == ReachStyle.POND_OUTLET;
        }
        ReachStyle[] styled = ReachShaper.styles(grade, ocean, outlet, reach.tier().waterfallMinDrop(), seaLevel, reach.tier().stepHeight());
        ReachStyle[] style = reach.style().clone();
        for (int point = 0; point < count; point++) {
            boolean touched = grade[point] != reach.grade()[point] || point > 0 && grade[point - 1] != reach.grade()[point - 1]
                    || point + 1 < count && grade[point + 1] != reach.grade()[point + 1];
            if (touched) {
                style[point] = styled[point];
            }
        }
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), grade, reach.halfWidth(), reach.depth(), style, reach.catchment())
                .withStraightFalls(reach.straightFalls());
    }
}
