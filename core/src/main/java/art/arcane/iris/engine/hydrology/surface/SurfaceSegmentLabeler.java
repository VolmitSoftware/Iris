package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydraulicChannelProfile;
import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyHash;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyPoint;

import java.util.ArrayList;
import java.util.List;

public final class SurfaceSegmentLabeler {
    private static final long SEGMENT_SALT = 0x5345474d454e54L;

    private SurfaceSegmentLabeler() {
    }

    public static List<HydraulicSegment> label(
            long worldSeed,
            long courseId,
            int[] x,
            int[] z,
            int[] head,
            double[] width,
            double[] depth,
            HydrologyPlannerSettings.Banks banks
    ) {
        int pairs = head.length - 1;
        if (pairs < 1) {
            return List.of();
        }
        ArrayList<Run> runs = new ArrayList<>();
        for (int pair = 0; pair < pairs; pair++) {
            HydrologyFeatureType type = pairType(head[pair] - head[pair + 1], banks.waterfallMinimumDrop());
            if (!runs.isEmpty() && runs.getLast().type == type) {
                runs.getLast().end = pair;
            } else {
                runs.add(new Run(pair, pair, type));
            }
        }
        for (Run run : runs) {
            if (run.type == HydrologyFeatureType.RIFFLE && run.pairs() >= 2) {
                run.type = HydrologyFeatureType.CASCADE;
            }
        }
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int index = 1; index + 1 < runs.size(); index++) {
                Run previous = runs.get(index - 1);
                Run current = runs.get(index);
                Run next = runs.get(index + 1);
                if (current.type == HydrologyFeatureType.SURFACE_POOL
                        && current.pairs() < banks.cascadeRun() * 2
                        && isGraded(previous.type)
                        && isGraded(next.type)) {
                    previous.end = next.end;
                    previous.type = HydrologyFeatureType.CASCADE;
                    runs.remove(index + 1);
                    runs.remove(index);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                for (int index = 1; index < runs.size(); index++) {
                    Run previous = runs.get(index - 1);
                    Run current = runs.get(index);
                    if (previous.type == current.type) {
                        previous.end = current.end;
                        runs.remove(index);
                        merged = true;
                        break;
                    }
                }
            }
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(runs.size());
        for (Run run : runs) {
            int startStation = run.start;
            int endStation = run.end + 1;
            ArrayList<HydrologyPoint> centerline = new ArrayList<>(endStation - startStation + 1);
            for (int station = startStation; station <= endStation; station++) {
                centerline.add(new HydrologyPoint(x[station], head[station], z[station]));
            }
            HydraulicChannelProfile channelProfile = HydraulicChannelProfile.range(width, depth, startStation, endStation + 1);
            int segmentWidth = Math.max(1, (int) StrictMath.ceil(channelProfile.maximumWidth()));
            int segmentDepth = Math.max(1, (int) StrictMath.ceil(channelProfile.maximumDepth()));
            int index = segments.size();
            segments.add(new HydraulicSegment(
                    HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, index, run.type.ordinal()),
                    courseId,
                    run.type,
                    head[startStation],
                    head[endStation],
                    segmentWidth,
                    segmentDepth,
                    false,
                    false,
                    centerline,
                    channelProfile
            ));
        }
        return List.copyOf(segments);
    }

    private static HydrologyFeatureType pairType(int drop, int waterfallMinimumDrop) {
        if (drop <= 0) {
            return HydrologyFeatureType.SURFACE_POOL;
        }
        if (drop == 1) {
            return HydrologyFeatureType.RIFFLE;
        }
        return drop >= waterfallMinimumDrop ? HydrologyFeatureType.WATERFALL : HydrologyFeatureType.CASCADE;
    }

    private static boolean isGraded(HydrologyFeatureType type) {
        return type == HydrologyFeatureType.RIFFLE || type == HydrologyFeatureType.CASCADE;
    }

    private static final class Run {
        private final int start;
        private int end;
        private HydrologyFeatureType type;

        private Run(int start, int end, HydrologyFeatureType type) {
            this.start = start;
            this.end = end;
            this.type = type;
        }

        private int pairs() {
            return end - start + 1;
        }
    }
}
