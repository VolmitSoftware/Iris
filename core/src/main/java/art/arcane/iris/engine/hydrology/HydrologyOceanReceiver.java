package art.arcane.iris.engine.hydrology;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HydrologyOceanReceiver implements HydrologyTerrainSampler {
    private static final int MAXIMUM_SAMPLES = 65_536;
    private static final int[][] CARDINALS = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}};

    private final HydrologyTerrainSampler source;
    private final LongOpenHashSet receiving;
    private final int seaLevel;

    private HydrologyOceanReceiver(HydrologyTerrainSampler source, LongOpenHashSet receiving, int seaLevel) {
        this.source = source;
        this.receiving = receiving;
        this.seaLevel = seaLevel;
    }

    public static HydrologyTerrainSampler forOutlet(HydrologyPlannerSettings settings,
                                                     HydrologyTerrainSampler source, RiverOutlet outlet) {
        if (!outlet.directOcean() || outlet.type() != HydrologyFeatureType.MOUTH) {
            return source;
        }
        return forConnection(settings, source, outlet.landwardPoint(), outlet.connectionPoint());
    }

    public static HydrologyTerrainSampler forConnection(HydrologyPlannerSettings settings,
                                                         HydrologyTerrainSampler source,
                                                         HydrologyPoint landward, HydrologyPoint receiving) {
        HydrologyTerrainSample shore = source.sample(landward.x(), landward.z());
        if (shore == null || shore.ocean() || shore.naturalHeight() < settings.seaLevel()) {
            return source;
        }
        double width = settings.surface().maximumWidth() * shore.widthMultiplier()
                * settings.surface().banks().mouthFlareRatio();
        return create(settings, source, List.of(new Mouth(
                List.of(landward, receiving), width)));
    }

    public static HydrologyTerrainSampler forCourse(HydrologyPlannerSettings settings,
                                                     HydrologyTerrainSampler source, RiverCourse course) {
        if (course.type() != RiverCourseType.SURFACE || course.segments().isEmpty()) {
            return source;
        }
        ArrayList<Mouth> mouths = new ArrayList<>(2);
        for (int index = 0; index < course.segments().size(); index++) {
            if (index != 0 && index != course.segments().size() - 1) {
                continue;
            }
            Mouth mouth = mouth(settings, source, course.segments().get(index));
            if (mouth != null) {
                mouths.add(mouth);
            }
        }
        return create(settings, source, mouths);
    }

    public static HydrologyTerrainSampler forMouth(HydrologyPlannerSettings settings,
                                                    HydrologyTerrainSampler source, HydraulicSegment segment) {
        Mouth mouth = mouth(settings, source, segment);
        return mouth == null ? source : create(settings, source, List.of(mouth));
    }

    @Override
    public HydrologyTerrainSample sample(int blockX, int blockZ) {
        return source.sample(blockX, blockZ);
    }

    @Override
    public boolean receivingWater(int blockX, int blockZ, int queriedSeaLevel) {
        return queriedSeaLevel == seaLevel && receiving.contains(RiverFootprint.pack(blockX, blockZ))
                || source.receivingWater(blockX, blockZ, queriedSeaLevel);
    }

    private static Mouth mouth(HydrologyPlannerSettings settings, HydrologyTerrainSampler source,
                                HydraulicSegment segment) {
        if (segment.type() != HydrologyFeatureType.MOUTH || segment.upstreamHeadY() != settings.seaLevel()
                || segment.downstreamHeadY() != settings.seaLevel()) {
            return null;
        }
        ArrayList<HydrologyPoint> points = new ArrayList<>(segment.centerline());
        HydrologyPoint first = points.getFirst();
        HydrologyTerrainSample terrain = source.sample(first.x(), first.z());
        if (terrain != null && terrain.ocean() && terrain.naturalHeight() < settings.seaLevel()) {
            Collections.reverse(points);
        }
        double width = segment.width();
        for (int station = 0; station < segment.centerline().size(); station++) {
            width = Math.max(width, segment.channelProfile().widthAt(station));
        }
        return new Mouth(List.copyOf(points), width);
    }

    private static HydrologyTerrainSampler create(HydrologyPlannerSettings settings,
                                                   HydrologyTerrainSampler source, List<Mouth> mouths) {
        if (mouths.isEmpty()) {
            return source;
        }
        Samples samples = new Samples(source);
        LongOpenHashSet receiving = new LongOpenHashSet();
        for (Mouth mouth : mouths) {
            LongArrayList run = submergedRun(mouth.points(), samples, settings.seaLevel());
            if (run.isEmpty()) {
                continue;
            }
            receiving.addAll(run);
            double radius = mouth.width() * settings.surface().banks().channel().outlineMaximumRatio() / 2D
                    + settings.outlets().maximumOceanApron() + 2D;
            extendHalo(receiving, run, samples, settings.seaLevel(), radius);
        }
        return receiving.isEmpty() ? source : new HydrologyOceanReceiver(source, receiving, settings.seaLevel());
    }

    private static LongArrayList submergedRun(List<HydrologyPoint> points, Samples samples, int seaLevel) {
        LongArrayList run = new LongArrayList();
        if (points.size() < 2) {
            return run;
        }
        HydrologyPoint endpoint = points.getLast();
        HydrologyTerrainSample ocean = samples.sample(endpoint.x(), endpoint.z());
        if (ocean == null || !ocean.ocean() || ocean.naturalHeight() >= seaLevel) {
            return run;
        }
        long previous = RiverFootprint.pack(points.getFirst().x(), points.getFirst().z());
        for (int index = 0; index + 1 < points.size(); index++) {
            HydrologyPoint first = points.get(index);
            HydrologyPoint second = points.get(index + 1);
            long steps = Math.max(Math.abs((long) second.x() - first.x()), Math.abs((long) second.z() - first.z()));
            if (steps > MAXIMUM_SAMPLES) {
                return new LongArrayList();
            }
            for (int step = index == 0 ? 0 : 1; step <= steps; step++) {
                double progress = steps == 0L ? 0D : step / (double) steps;
                int x = (int) StrictMath.round(first.x() + ((long) second.x() - first.x()) * progress);
                int z = (int) StrictMath.round(first.z() + ((long) second.z() - first.z()) * progress);
                HydrologyTerrainSample terrain = samples.sample(x, z);
                if (terrain == null) {
                    return new LongArrayList();
                }
                long key = RiverFootprint.pack(x, z);
                if (terrain.naturalHeight() >= seaLevel) {
                    if (!run.isEmpty()) {
                        return new LongArrayList();
                    }
                } else {
                    if (!run.isEmpty() && !cardinalBridge(run, previous, key, samples, seaLevel)) {
                        return new LongArrayList();
                    }
                    if (run.isEmpty() || run.getLong(run.size() - 1) != key) {
                        run.add(key);
                    }
                }
                previous = key;
            }
        }
        return run;
    }

    private static boolean cardinalBridge(LongArrayList run, long previous, long next,
                                          Samples samples, int seaLevel) {
        int previousX = RiverFootprint.unpackX(previous);
        int previousZ = RiverFootprint.unpackZ(previous);
        int nextX = RiverFootprint.unpackX(next);
        int nextZ = RiverFootprint.unpackZ(next);
        if (previousX == nextX || previousZ == nextZ) {
            return true;
        }
        long first = RiverFootprint.pack(previousX, nextZ);
        long second = RiverFootprint.pack(nextX, previousZ);
        for (long bridge : new long[] {Math.min(first, second), Math.max(first, second)}) {
            HydrologyTerrainSample terrain = samples.sample(RiverFootprint.unpackX(bridge), RiverFootprint.unpackZ(bridge));
            if (terrain != null && terrain.naturalHeight() < seaLevel) {
                run.add(bridge);
                return true;
            }
        }
        return false;
    }

    private static void extendHalo(LongOpenHashSet receiving, LongArrayList run, Samples samples,
                                    int seaLevel, double radius) {
        long first = run.getLong(0);
        int anchorX = RiverFootprint.unpackX(first);
        int anchorZ = RiverFootprint.unpackZ(first);
        double squaredRadius = radius * radius;
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int index = 0; index < run.size(); index++) {
            long key = run.getLong(index);
            double dx = (double) RiverFootprint.unpackX(key) - anchorX;
            double dz = (double) RiverFootprint.unpackZ(key) - anchorZ;
            if (dx * dx + dz * dz <= squaredRadius && visited.add(key)) {
                queue.enqueue(key);
            }
        }
        while (!queue.isEmpty() && samples.available()) {
            long key = queue.dequeueLong();
            int x = RiverFootprint.unpackX(key);
            int z = RiverFootprint.unpackZ(key);
            for (int[] offset : CARDINALS) {
                long nextX = (long) x + offset[0];
                long nextZ = (long) z + offset[1];
                double dx = nextX - anchorX;
                double dz = nextZ - anchorZ;
                if (nextX < Integer.MIN_VALUE || nextX > Integer.MAX_VALUE
                        || nextZ < Integer.MIN_VALUE || nextZ > Integer.MAX_VALUE
                        || dx * dx + dz * dz > squaredRadius) {
                    continue;
                }
                long next = RiverFootprint.pack((int) nextX, (int) nextZ);
                if (!visited.add(next)) {
                    continue;
                }
                HydrologyTerrainSample terrain = samples.sample((int) nextX, (int) nextZ);
                if (terrain != null && terrain.naturalHeight() < seaLevel) {
                    receiving.add(next);
                    queue.enqueue(next);
                }
            }
        }
    }

    private record Mouth(List<HydrologyPoint> points, double width) {
    }

    private static final class Samples {
        private final HydrologyTerrainSampler source;
        private final Long2ObjectOpenHashMap<HydrologyTerrainSample> samples = new Long2ObjectOpenHashMap<>();

        private Samples(HydrologyTerrainSampler source) {
            this.source = source;
        }

        private HydrologyTerrainSample sample(int x, int z) {
            long key = RiverFootprint.pack(x, z);
            if (samples.containsKey(key)) {
                return samples.get(key);
            }
            if (!available()) {
                return null;
            }
            HydrologyTerrainSample terrain = source.sample(x, z);
            samples.put(key, terrain);
            return terrain;
        }

        private boolean available() {
            return samples.size() < MAXIMUM_SAMPLES;
        }
    }
}
