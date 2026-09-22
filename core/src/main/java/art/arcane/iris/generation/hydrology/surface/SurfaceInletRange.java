package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.generation.hydrology.RiverCourse;
import art.arcane.iris.generation.hydrology.RiverCourseType;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.List;

public final class SurfaceInletRange {
    private final int[] offsets;
    private final int firstStation;
    private final Long2ObjectOpenHashMap<LongOpenHashSet> fallingStations;

    private SurfaceInletRange(int[] offsets, int firstStation,
                              Long2ObjectOpenHashMap<LongOpenHashSet> fallingStations) {
        this.offsets = offsets;
        this.firstStation = firstStation;
        this.fallingStations = fallingStations;
    }

    public static SurfaceInletRange forCourse(HydrologyPlannerSettings settings, RiverCourse course,
                                              HydrologyTerrainSampler receivingSampler) {
        List<HydraulicSegment> segments = course.segments();
        int[] offsets = offsets(segments);
        Long2ObjectOpenHashMap<LongOpenHashSet> fallingStations = new Long2ObjectOpenHashMap<>();
        if (course.type() != RiverCourseType.SURFACE || segments.isEmpty()
                || segments.getLast().type() != HydrologyFeatureType.MOUTH) {
            return new SurfaceInletRange(offsets, Integer.MAX_VALUE, fallingStations);
        }
        int exposed = exposedStations(settings.seaLevel(), segments, offsets, receivingSampler);
        HydrologyPlannerSettings.Inlet inlet = settings.surface().banks().inlet();
        int reach = Math.min(inlet.length(), (int) StrictMath.floor(exposed * inlet.courseFraction()));
        int firstStation = Math.max(0, exposed - reach - reach / 2);
        for (int index = 0; index < segments.size(); index++) {
            HydraulicSegment segment = segments.get(index);
            if (!segment.type().isSurface() || !segment.fallingFluid()) {
                continue;
            }
            SurfaceCenterline centerline = SurfaceCenterline.densify(segment.centerline());
            int first = Math.max(0, firstStation - offsets[index]);
            if (first >= centerline.size()) {
                continue;
            }
            LongOpenHashSet positions = new LongOpenHashSet(centerline.size() - first);
            for (int station = first; station < centerline.size(); station++) {
                positions.add(RiverFootprint.pack(centerline.x()[station], centerline.z()[station]));
            }
            fallingStations.put(segment.id(), positions);
        }
        return new SurfaceInletRange(offsets, firstStation, fallingStations);
    }

    public int offset(int segmentIndex) {
        return offsets[segmentIndex];
    }

    public int firstStation(int segmentIndex) {
        return Math.max(0, firstStation - offsets[segmentIndex]);
    }

    public boolean allowsFallingStation(long segmentId, int x, int z) {
        LongOpenHashSet positions = fallingStations.get(segmentId);
        return positions != null && positions.contains(RiverFootprint.pack(x, z));
    }

    private static int[] offsets(List<HydraulicSegment> segments) {
        int[] offsets = new int[segments.size()];
        int station = 0;
        HydrologyPoint previous = null;
        for (int index = 0; index < segments.size(); index++) {
            List<HydrologyPoint> points = segments.get(index).centerline();
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                HydrologyPoint point = points.get(pointIndex);
                if (previous != null) {
                    station += Math.max(Math.abs(point.x() - previous.x()), Math.abs(point.z() - previous.z()));
                }
                if (pointIndex == 0) {
                    offsets[index] = station;
                }
                previous = point;
            }
        }
        return offsets;
    }

    private static int exposedStations(int seaLevel, List<HydraulicSegment> segments, int[] offsets,
                                       HydrologyTerrainSampler receivingSampler) {
        int mouth = segments.size() - 1;
        SurfaceCenterline centerline = SurfaceCenterline.densify(segments.get(mouth).centerline());
        for (int station = centerline.size() - 1; station >= 0; station--) {
            if (!receivingSampler.receivingWater(centerline.x()[station], centerline.z()[station], seaLevel)) {
                return offsets[mouth] + station + 1;
            }
        }
        return offsets[mouth];
    }
}
