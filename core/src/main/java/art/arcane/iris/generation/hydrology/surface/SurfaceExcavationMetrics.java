package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologySurfaceDropRaster;
import art.arcane.iris.generation.hydrology.RiverCourse;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.Objects;

public final class SurfaceExcavationMetrics {
    private static final int REACH_LENGTH = 16;
    private static final long WET_COLUMN = Long.MIN_VALUE;

    private SurfaceExcavationMetrics() {
    }

    public static long accumulate(RiverCourse course, SurfaceCenterline centerline, SurfaceFootprint footprint,
                                  HydrologySurfaceDropRaster drops, SurfaceBounds bounds, int[] stationVolumes) {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(centerline, "centerline");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(drops, "drops");
        Objects.requireNonNull(stationVolumes, "stationVolumes");
        if (stationVolumes.length != centerline.size()) {
            throw new IllegalArgumentException("Excavation volumes must match centerline stations.");
        }
        Long2LongOpenHashMap cuts = new Long2LongOpenHashMap(footprint.columns().size() + drops.columns().size());
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (bounds == null || bounds.contains(column.x(), column.z())) {
                accumulate(cuts, column.x(), column.z(), column.terrain().naturalHeight(), column.layer(), column.station());
            }
        }
        Long2ObjectOpenHashMap<StationRange> ranges = dropStations(course);
        for (HydrologyColumnSample column : drops.columns()) {
            if (bounds != null && !bounds.contains(column.x(), column.z())) {
                continue;
            }
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.oceanApron() || !layer.channel() && !layer.terrainOwned()) {
                    continue;
                }
                StationRange range = ranges.get(layer.feature().segmentId());
                if (range != null) {
                    int station = layer.channel() ? range.first() : nearestStation(centerline, range, column.x(), column.z());
                    accumulate(cuts, column.x(), column.z(), column.naturalHeight(), layer, station);
                }
            }
        }
        long total = 0L;
        LongIterator iterator = cuts.values().iterator();
        while (iterator.hasNext()) {
            long encoded = iterator.nextLong();
            if (encoded == WET_COLUMN) {
                continue;
            }
            int station = (int) (encoded >>> 32);
            int depth = (int) encoded;
            stationVolumes[station] = (int) Math.min(Integer.MAX_VALUE, (long) stationVolumes[station] + depth);
            total += depth;
        }
        return total;
    }

    public static int maximumVolumePerBlock(SurfaceCenterline centerline, int[] stationVolumes) {
        Objects.requireNonNull(centerline, "centerline");
        Objects.requireNonNull(stationVolumes, "stationVolumes");
        if (stationVolumes.length != centerline.size()) {
            throw new IllegalArgumentException("Excavation volumes must match centerline stations.");
        }
        double[] distance = new double[stationVolumes.length];
        long[] volume = new long[stationVolumes.length + 1];
        for (int station = 0; station < stationVolumes.length; station++) {
            if (stationVolumes[station] < 0) {
                throw new IllegalArgumentException("Excavation volumes cannot be negative.");
            }
            double run = station == 0 ? 0D : StrictMath.hypot(
                    (double) centerline.x()[station] - centerline.x()[station - 1],
                    (double) centerline.z()[station] - centerline.z()[station - 1]);
            distance[station] = station == 0 ? 0D : distance[station - 1] + run;
            volume[station + 1] = volume[station] + stationVolumes[station];
        }
        int largestSection = 0;
        int first = 0;
        int last = 0;
        for (int station = 0; station < stationVolumes.length; station++) {
            while (first < station && distance[station] - distance[first] > REACH_LENGTH / 2D) {
                first++;
            }
            while (last + 1 < stationVolumes.length && distance[last + 1] - distance[station] <= REACH_LENGTH / 2D) {
                last++;
            }
            double section = StrictMath.ceil((volume[last + 1] - volume[first]) / (double) REACH_LENGTH);
            largestSection = Math.max(largestSection, (int) Math.min(Integer.MAX_VALUE, section));
        }
        return largestSection;
    }

    private static void accumulate(Long2LongOpenHashMap cuts, int x, int z, int naturalHeight,
                                   HydrologyColumnLayer layer, int station) {
        if (layer.oceanApron()) {
            return;
        }
        long key = RiverFootprint.pack(x, z);
        if (layer.channel() && layer.fluidOwned() && layer.fluidHeadY() > layer.bedY()) {
            cuts.put(key, WET_COLUMN);
            return;
        }
        if (layer.channel() || !layer.terrainOwned()) {
            return;
        }
        int depth = Math.max(0, naturalHeight - layer.bedY());
        long previous = cuts.get(key);
        if (depth > 0 && previous != WET_COLUMN
                && (depth > (int) previous || depth == (int) previous && station < (int) (previous >>> 32))) {
            cuts.put(key, (long) station << 32 | depth & 0xffffffffL);
        }
    }

    private static Long2ObjectOpenHashMap<StationRange> dropStations(RiverCourse course) {
        Long2ObjectOpenHashMap<StationRange> ranges = new Long2ObjectOpenHashMap<>();
        int station = 0;
        HydrologyPoint previous = null;
        for (HydraulicSegment segment : course.segments()) {
            int first = station;
            for (int index = 0; index < segment.centerline().size(); index++) {
                HydrologyPoint point = segment.centerline().get(index);
                if (previous != null) {
                    station += Math.max(Math.abs(point.x() - previous.x()), Math.abs(point.z() - previous.z()));
                }
                if (index == 0) {
                    first = station;
                }
                previous = point;
            }
            if (segment.type().isSurface() && segment.fallingFluid()) {
                ranges.put(segment.id(), new StationRange(first, station));
            }
        }
        return ranges;
    }

    private static int nearestStation(SurfaceCenterline centerline, StationRange range, int x, int z) {
        int nearest = range.first();
        long minimumDistance = Long.MAX_VALUE;
        for (int station = range.first(); station <= range.last(); station++) {
            long deltaX = (long) x - centerline.x()[station];
            long deltaZ = (long) z - centerline.z()[station];
            long distance = deltaX * deltaX + deltaZ * deltaZ;
            if (distance < minimumDistance) {
                minimumDistance = distance;
                nearest = station;
            }
        }
        return nearest;
    }

    private record StationRange(int first, int last) {
    }
}
