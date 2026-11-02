package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.ChannelCoverage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class DeltaPlanner {
    private static final long SALT_DELTA = 0x44454c5441L;
    private static final float CLEARANCE = 2f;
    private static final double BEND = 0.5D;
    private static final double JITTER = 0.25D;

    private final HydrologySettings settings;

    DeltaPlanner(HydrologySettings settings) {
        this.settings = settings;
    }

    List<Reach> plan(List<ReachDraft> drafts, List<Reach> reaches, List<Pond> ponds, HeightLattice fine, long seed) {
        List<Integer> mouths = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            ReachDraft draft = drafts.get(index);
            if (draft.tier.delta().channels() > 0 && draft.chain.end() == ReachTracer.End.OCEAN) {
                mouths.add(index);
            }
        }
        if (mouths.isEmpty()) {
            return List.of();
        }
        mouths.sort(Comparator.comparingLong((Integer index) -> drafts.get(index).id).thenComparingDouble(index -> drafts.get(index).xs[0])
                .thenComparingDouble(index -> drafts.get(index).zs[0]));
        List<Reach> accepted = new ArrayList<>();
        for (int index : mouths) {
            Reach parent = reaches.get(index);
            List<Reach> earlier = List.copyOf(accepted);
            for (Reach channel : distributaries(drafts.get(index), fine, seed)) {
                if (!crosses(channel, parent, reaches, earlier, ponds)) {
                    accepted.add(channel);
                }
            }
        }
        return accepted;
    }

    private List<Reach> distributaries(ReachDraft mouth, HeightLattice fine, long seed) {
        HydrologyTier.Delta delta = mouth.tier.delta();
        int last = mouth.xs.length - 1;
        int start = estuaryStart(mouth.style);
        int channels = delta.channels();
        float halfWidth = Math.min(mouth.tier.maxHalfWidth(), mouth.halfWidth[start] / (float) Math.sqrt(channels));
        float[] distance = ReachShaper.distances(mouth.xs, mouth.zs);
        float lip = start == 0 ? 0f : 0.5f * (distance[start] - distance[start - 1]);
        float branch = distance[start] + halfWidth + CLEARANCE + 1f + lip;
        double axisX = mouth.xs[last] - mouth.xs[start];
        double axisZ = mouth.zs[last] - mouth.zs[start];
        if (start == last || branch >= distance[last] || Math.hypot(axisX, axisZ) < 1e-3D) {
            return List.of();
        }
        float[] at = pointAt(mouth.xs, mouth.zs, distance, start, branch);
        double heading = Math.atan2(axisZ, axisX);
        long choices = CellHash.mix(seed, SALT_DELTA, (int) Math.floor(mouth.xs[last]), (int) Math.floor(mouth.zs[last]));
        int firstSide = CellHash.unit(CellHash.mix(choices, SALT_DELTA, -1, 0)) < 0.5D ? 1 : -1;
        List<Reach> made = new ArrayList<>(channels);
        for (int channel = 0; channel < channels; channel++) {
            long channelChoices = CellHash.mix(choices, SALT_DELTA, channel, 0);
            Fan fan = new Fan(CellHash.mix(mouth.id, SALT_DELTA, channel, channels), at[0], at[1], heading,
                    angle(delta, channelChoices, channel, firstSide), BEND * CellHash.unit(CellHash.mix(channelChoices, SALT_DELTA, 2, 0)),
                    halfWidth);
            Reach reach = distributary(mouth, fine, fan);
            if (reach != null) {
                made.add(reach);
            }
        }
        return made;
    }

    private static int estuaryStart(ReachStyle[] style) {
        int start = style.length - 1;
        if (style[start] != ReachStyle.MOUTH) {
            return start;
        }
        while (start > 0 && style[start - 1] == ReachStyle.MOUTH) {
            start--;
        }
        return start;
    }

    private static float[] pointAt(float[] xs, float[] zs, float[] distance, int from, float along) {
        int segment = from;
        while (distance[segment + 1] < along) {
            segment++;
        }
        float share = (along - distance[segment]) / Math.max(1e-6f, distance[segment + 1] - distance[segment]);
        return new float[]{xs[segment] + share * (xs[segment + 1] - xs[segment]), zs[segment] + share * (zs[segment + 1] - zs[segment])};
    }

    private static double angle(HydrologyTier.Delta delta, long channelChoices, int channel, int firstSide) {
        int side = channel % 2 == 0 ? firstSide : -firstSide;
        int onSide = side == firstSide ? (delta.channels() + 1) / 2 : delta.channels() / 2;
        double turn = CellHash.unit(CellHash.mix(channelChoices, SALT_DELTA, 1, 0));
        return side * Math.toRadians(delta.spreadDegrees()) * (channel / 2 + 1 - JITTER * turn) / onSide;
    }

    private Reach distributary(ReachDraft mouth, HeightLattice fine, Fan fan) {
        int step = HydrologySettings.DELTA_STEP;
        int length = mouth.tier.delta().length();
        int stations = (length + step - 1) / step + 1;
        float[] xs = new float[stations];
        float[] zs = new float[stations];
        xs[0] = fan.x();
        zs[0] = fan.z();
        for (int station = 1; station < stations; station++) {
            double direction = fan.heading() + fan.angle() * (1D - fan.bend() * ((station - 1) * step) / length);
            xs[station] = (float) (xs[station - 1] + step * Math.cos(direction));
            zs[station] = (float) (zs[station - 1] + step * Math.sin(direction));
        }
        int cell = fine.cell();
        int[] nodeX = new int[3 * stations];
        int[] nodeZ = new int[3 * stations];
        for (int station = 0; station < stations; station++) {
            int next = Math.min(stations - 1, station + 1);
            int previous = Math.max(0, station - 1);
            double dx = xs[next] - xs[previous];
            double dz = zs[next] - zs[previous];
            double run = Math.max(1e-6D, Math.hypot(dx, dz));
            for (int offset = -1; offset <= 1; offset++) {
                int node = 3 * station + offset + 1;
                nodeX[node] = (int) Math.floor((xs[station] - offset * fan.halfWidth() * dz / run) / cell + 0.5D);
                nodeZ[node] = (int) Math.floor((zs[station] + offset * fan.halfWidth() * dx / run) / cell + 0.5D);
            }
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(nodeX.length);
        fine.fill(nodeX, nodeZ, samples);
        float highest = settings.seaLevel() + settings.deformation().maxFill();
        int sea = -1;
        for (int station = 0; station < stations; station++) {
            if (samples.ocean()[3 * station + 1]) {
                sea = station;
                break;
            }
            for (int node = 3 * station; node < 3 * station + 3; node++) {
                if (samples.height()[node] > highest) {
                    return null;
                }
            }
        }
        if (sea < 2) {
            return null;
        }
        int end = Math.min(stations - 1, sea + 1);
        return reach(mouth, fan, Arrays.copyOf(xs, end + 1), Arrays.copyOf(zs, end + 1));
    }

    private Reach reach(ReachDraft mouth, Fan fan, float[] xs, float[] zs) {
        HydrologyTier tier = mouth.tier;
        int count = xs.length;
        float[] distance = ReachShaper.distances(xs, zs);
        int[] grade = new int[count];
        float[] halfWidth = new float[count];
        float[] depth = new float[count];
        ReachStyle[] style = new ReachStyle[count];
        float channelDepth = tier.depth(fan.halfWidth());
        for (int point = 0; point < count; point++) {
            grade[point] = settings.seaLevel();
            halfWidth[point] = fan.halfWidth();
            depth[point] = channelDepth + (HydrologyTier.MINIMUM_DEPTH - channelDepth) * distance[point] / distance[count - 1];
            style[point] = ReachStyle.MOUTH;
        }
        return new Reach(fan.id(), tier, 0L, xs, zs, grade, halfWidth, depth, style, mouth.catchment / tier.delta().channels());
    }

    private boolean crosses(Reach channel, Reach parent, List<Reach> reaches, List<Reach> earlier, List<Pond> ponds) {
        float reach = channel.halfWidth()[0] + CLEARANCE;
        int minX = (int) Math.floor(minimum(channel.xs()) - reach);
        int minZ = (int) Math.floor(minimum(channel.zs()) - reach);
        int maxX = (int) Math.ceil(maximum(channel.xs()) + reach);
        int maxZ = (int) Math.ceil(maximum(channel.zs()) + reach);
        List<Reach> others = new ArrayList<>(reaches.size() + earlier.size());
        for (Reach other : reaches) {
            if (other != parent) {
                others.add(other);
            }
        }
        others.addAll(earlier);
        ChannelCoverage otherWater = new ChannelCoverage(others, minX, minZ, maxX, maxZ);
        ChannelCoverage parentWater = new ChannelCoverage(List.of(parent), minX, minZ, maxX, maxZ);
        List<Pond> nearby = new ArrayList<>();
        for (Pond pond : ponds) {
            if (near(pond.mask(), minX, minZ, maxX, maxZ)) {
                nearby.add(pond);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (distanceTo(channel, x, z) > reach) {
                    continue;
                }
                for (Pond pond : nearby) {
                    if (pond.mask().contains(x, z)) {
                        return true;
                    }
                }
                if (otherWater.grade(x, z) != Integer.MIN_VALUE || parentWater.grade(x, z) > settings.seaLevel()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distanceTo(Reach reach, double x, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double ax = reach.xs()[segment];
            double az = reach.zs()[segment];
            double dx = reach.xs()[segment + 1] - ax;
            double dz = reach.zs()[segment + 1] - az;
            double length = dx * dx + dz * dz;
            double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
            nearest = Math.min(nearest, Math.hypot(x - (ax + t * dx), z - (az + t * dz)));
        }
        return nearest;
    }

    private static boolean near(PondMask mask, int minX, int minZ, int maxX, int maxZ) {
        int half = mask.step() >> 1;
        return mask.originX() - half <= maxX && mask.originX() + (mask.width() - 1) * mask.step() + half >= minX
                && mask.originZ() - half <= maxZ && mask.originZ() + (mask.depth() - 1) * mask.step() + half >= minZ;
    }

    private static float minimum(float[] values) {
        float minimum = Float.POSITIVE_INFINITY;
        for (float value : values) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    private static float maximum(float[] values) {
        float maximum = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }

    private record Fan(long id, float x, float z, double heading, double angle, double bend, float halfWidth) {
    }
}
