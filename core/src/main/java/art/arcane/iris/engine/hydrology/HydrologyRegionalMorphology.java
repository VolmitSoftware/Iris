package art.arcane.iris.engine.hydrology;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HydrologyRegionalMorphology {
    private static final int MAXIMUM_STATIONS = 129;
    private static final double MAXIMUM_LATERAL_REACH = 64D;
    private static final double GENTLE_GRADE = 1D / 64D;
    private static final double STEEP_GRADE = 0.25D;
    private static final double[] LATERAL_OFFSETS = {-1D, -0.5D, 0.5D, 1D};

    private final Location[] locations;
    private final double[] freedom;

    private HydrologyRegionalMorphology(Location[] locations, double[] freedom) {
        this.locations = locations;
        this.freedom = freedom;
    }

    static HydrologyRegionalMorphology sample(List<HydrologyPoint> guide, HydrologyTerrainSampler sampler,
                                              HydrologyPlannerSettings settings) {
        double length = 0D;
        for (int index = 1; index < guide.size(); index++) {
            length += StrictMath.hypot((double) guide.get(index).x() - guide.get(index - 1).x(),
                    (double) guide.get(index).z() - guide.get(index - 1).z());
        }
        if (length <= 0D || length > settings.routing().maximumRouteLength()) {
            return new HydrologyRegionalMorphology(new Location[0], new double[0]);
        }
        int intervals = Math.min(MAXIMUM_STATIONS - 1,
                Math.max(1, (int) StrictMath.ceil(length / settings.routing().regional().sampleSpacing())));
        Location[] locations = locations(guide, length, intervals);
        HydrologyTerrainSample[] centers = new HydrologyTerrainSample[locations.length];
        Map<Long, HydrologyTerrainSample> sampled = new HashMap<>();
        for (int index = 0; index < locations.length; index++) {
            Location location = locations[index];
            centers[index] = sample(sampler, sampled, location.x(), location.z());
        }
        double reach = Math.min(MAXIMUM_LATERAL_REACH, Math.max(settings.surface().maximumWidth() * 2D,
                settings.routing().regional().sampleSpacing() / 8D));
        double[] freedom = new double[locations.length];
        for (int index = 0; index < locations.length; index++) {
            HydrologyTerrainSample center = centers[index];
            if (!land(center, settings.seaLevel())) {
                continue;
            }
            int before = Math.max(0, index - 1);
            int after = Math.min(locations.length - 1, index + 1);
            if (centers[before] == null || centers[after] == null) {
                continue;
            }
            double distance = length / intervals;
            double grade = Math.max(StrictMath.abs((double) center.naturalHeight() - centers[before].naturalHeight()),
                    StrictMath.abs((double) centers[after].naturalHeight() - center.naturalHeight())) / distance;
            double gradeFreedom = 1D - smooth((grade - GENTLE_GRADE) / (STEEP_GRADE - GENTLE_GRADE));
            freedom[index] = gradeFreedom * room(locations[index], center, sampler, sampled, settings, reach);
        }
        return new HydrologyRegionalMorphology(locations, smooth(freedom));
    }

    double freedomAt(double x, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        double selected = 0D;
        for (int index = 0; index + 1 < locations.length; index++) {
            Location first = locations[index];
            Location second = locations[index + 1];
            double deltaX = (double) second.x() - first.x();
            double deltaZ = (double) second.z() - first.z();
            double squared = deltaX * deltaX + deltaZ * deltaZ;
            double progress = squared == 0D ? 0D : Math.max(0D, Math.min(1D,
                    ((x - first.x()) * deltaX + (z - first.z()) * deltaZ) / squared));
            double offsetX = x - first.x() - deltaX * progress;
            double offsetZ = z - first.z() - deltaZ * progress;
            double distance = offsetX * offsetX + offsetZ * offsetZ;
            if (distance < nearest) {
                nearest = distance;
                selected = freedom[index] + (freedom[index + 1] - freedom[index]) * smooth(progress);
            }
        }
        return Math.max(0D, Math.min(1D, selected));
    }

    private static Location[] locations(List<HydrologyPoint> guide, double length, int intervals) {
        Location[] locations = new Location[intervals + 1];
        int segment = 0;
        double traversed = 0D;
        for (int index = 0; index <= intervals; index++) {
            double distance = length * index / intervals;
            HydrologyPoint first = guide.get(segment);
            HydrologyPoint second = guide.get(segment + 1);
            double span = StrictMath.hypot((double) second.x() - first.x(), (double) second.z() - first.z());
            while (segment + 2 < guide.size() && (span == 0D || traversed + span < distance)) {
                traversed += span;
                segment++;
                first = guide.get(segment);
                second = guide.get(segment + 1);
                span = StrictMath.hypot((double) second.x() - first.x(), (double) second.z() - first.z());
            }
            double progress = span == 0D ? 0D : Math.max(0D, Math.min(1D, (distance - traversed) / span));
            locations[index] = new Location(
                    (int) StrictMath.round(first.x() + ((double) second.x() - first.x()) * progress),
                    (int) StrictMath.round(first.z() + ((double) second.z() - first.z()) * progress),
                    span == 0D ? 1D : (second.x() - first.x()) / span,
                    span == 0D ? 0D : (second.z() - first.z()) / span);
        }
        return locations;
    }

    private static double room(Location location, HydrologyTerrainSample center, HydrologyTerrainSampler sampler,
                               Map<Long, HydrologyTerrainSample> sampled, HydrologyPlannerSettings settings, double reach) {
        int incision = center.surfacePolicy().maximumIncision(settings.surface().maximumIncision());
        double allowance = Math.max(1D, Math.min(incision, StrictMath.floor(incision * center.incisionMultiplier())));
        double room = 1D;
        for (double scale : LATERAL_OFFSETS) {
            double offset = reach * scale;
            int x = (int) StrictMath.round(location.x() - location.tangentZ() * offset);
            int z = (int) StrictMath.round(location.z() + location.tangentX() * offset);
            HydrologyTerrainSample side = sample(sampler, sampled, x, z);
            if (!land(side, settings.seaLevel()) || !center.drainsInto(side) || !side.drainsInto(center)
                    || !HydrologySurfaceProfiles.sharesProfile(center, side)) {
                return 0D;
            }
            double relief = StrictMath.abs((double) side.naturalHeight() - center.naturalHeight());
            room = Math.min(room, 1D - smooth((relief - allowance / 4D) / allowance));
        }
        return room;
    }

    private static boolean land(HydrologyTerrainSample sample, int seaLevel) {
        return sample != null && !sample.ocean() && sample.naturalHeight() >= seaLevel && sample.transitAllowed();
    }

    private static HydrologyTerrainSample sample(HydrologyTerrainSampler sampler, Map<Long, HydrologyTerrainSample> sampled,
                                                 int x, int z) {
        long key = RiverFootprint.pack(x, z);
        if (!sampled.containsKey(key)) {
            sampled.put(key, sampler.sample(x, z));
        }
        return sampled.get(key);
    }

    private static double[] smooth(double[] values) {
        double[] smoothed = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            double total = 0D;
            int weight = 0;
            for (int sample = Math.max(0, index - 2); sample <= Math.min(values.length - 1, index + 2); sample++) {
                int strength = 3 - Math.abs(sample - index);
                total += values[sample] * strength;
                weight += strength;
            }
            smoothed[index] = total / weight;
        }
        return smoothed;
    }

    private static double smooth(double value) {
        double bounded = Math.max(0D, Math.min(1D, value));
        return bounded * bounded * (3D - 2D * bounded);
    }

    private record Location(int x, int z, double tangentX, double tangentZ) {
    }
}
