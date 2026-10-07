package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.generation.hydrology.surface.SurfaceLayerColumn;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.List;

final class HydrologyRegionalConnectivity {
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final Long2LongOpenHashMap water = new Long2LongOpenHashMap();
    private final Long2ObjectOpenHashMap<LongArrayList> disjoint = new Long2ObjectOpenHashMap<>();

    void add(List<SurfaceLayerColumn> columns) {
        for (SurfaceLayerColumn column : columns) {
            HydrologyColumnLayer layer = column.layer();
            if (layer.fluidOwned() && layer.bedY() < layer.fluidHeadY()) {
                addFluidColumn(column.x(), column.z(), layer.bedY(), layer.fluidHeadY());
            }
        }
    }

    void add(HydrologyColumnSample column) {
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.fluidOwned() && layer.bedY() < layer.fluidHeadY()) {
                addFluidColumn(column.x(), column.z(), layer.bedY(), layer.fluidHeadY());
            }
        }
    }

    void addFluidColumn(int x, int z, int bed, int head) {
        long key = RiverFootprint.pack(x, z);
        long interval = levels(bed, head);
        if (!water.containsKey(key)) {
            water.put(key, interval);
            return;
        }
        long previous = water.get(key);
        LongArrayList gaps = disjoint.get(key);
        if (gaps == null && bed <= (int) previous && (int) (previous >> 32) <= head) {
            water.put(key, levels(Math.min(bed, (int) (previous >> 32)), Math.max(head, (int) previous)));
            return;
        }
        if (gaps == null) {
            gaps = new LongArrayList(3);
            gaps.add(previous);
            disjoint.put(key, gaps);
        }
        gaps.add(interval);
        gaps.sort(null);
        int retained = 1;
        for (int index = 1; index < gaps.size(); index++) {
            long first = gaps.getLong(retained - 1);
            long next = gaps.getLong(index);
            if ((int) (next >> 32) <= (int) first) {
                gaps.set(retained - 1, levels((int) (first >> 32), Math.max((int) first, (int) next)));
            } else {
                gaps.set(retained++, next);
            }
        }
        gaps.size(retained);
        water.put(key, gaps.getLong(0));
        if (retained == 1) {
            disjoint.remove(key);
        }
    }

    boolean connected(RiverCourse course, HydrologyTerrainSampler sampler, HydrologyPlannerSettings settings) {
        if (water.isEmpty()) {
            return false;
        }
        LongOpenHashSet visited = new LongOpenHashSet(water.size());
        if (!disjoint.isEmpty()) {
            if (!connectedIntervals(visited)) {
                return false;
            }
            return reachesTerminal(course, sampler, settings, visited);
        }
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long start = water.keySet().iterator().nextLong();
        queue.enqueue(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            long currentLevels = water.get(current);
            int currentBed = (int) (currentLevels >> 32);
            int currentHead = (int) currentLevels;
            int x = RiverFootprint.unpackX(current);
            int z = RiverFootprint.unpackZ(current);
            for (int[] offset : NEIGHBORS) {
                long next = RiverFootprint.pack(x + offset[0], z + offset[1]);
                if (!water.containsKey(next) || visited.contains(next)) {
                    continue;
                }
                long levels = water.get(next);
                if (Math.max(currentBed, (int) (levels >> 32)) >= Math.min(currentHead, (int) levels)) {
                    continue;
                }
                visited.add(next);
                queue.enqueue(next);
            }
        }
        if (visited.size() != water.size()) {
            return false;
        }
        return reachesTerminal(course, sampler, settings, visited);
    }

    private boolean connectedIntervals(LongOpenHashSet visited) {
        int count = water.size();
        for (LongArrayList intervals : disjoint.values()) {
            count += intervals.size() - 1;
        }
        long[] columns = new long[count];
        long[] intervals = new long[count];
        Long2IntOpenHashMap starts = new Long2IntOpenHashMap(water.size());
        starts.defaultReturnValue(-1);
        int index = 0;
        for (long column : water.keySet()) {
            starts.put(column, index);
            LongArrayList layers = disjoint.get(column);
            int size = layers == null ? 1 : layers.size();
            for (int layer = 0; layer < size; layer++) {
                columns[index] = column;
                intervals[index++] = layers == null ? water.get(column) : layers.getLong(layer);
            }
        }
        boolean[] reached = new boolean[count];
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        queue.enqueue(0);
        reached[0] = true;
        visited.add(columns[0]);
        int reachedCount = 1;
        while (!queue.isEmpty()) {
            int current = queue.dequeueInt();
            int currentBed = (int) (intervals[current] >> 32);
            int currentHead = (int) intervals[current];
            int x = RiverFootprint.unpackX(columns[current]);
            int z = RiverFootprint.unpackZ(columns[current]);
            for (int[] offset : NEIGHBORS) {
                long nextColumn = RiverFootprint.pack(x + offset[0], z + offset[1]);
                int start = starts.get(nextColumn);
                if (start < 0) {
                    continue;
                }
                LongArrayList layers = disjoint.get(nextColumn);
                int end = start + (layers == null ? 1 : layers.size());
                for (int next = start; next < end; next++) {
                    if (reached[next] || Math.max(currentBed, (int) (intervals[next] >> 32))
                            >= Math.min(currentHead, (int) intervals[next])) {
                        continue;
                    }
                    reached[next] = true;
                    reachedCount++;
                    visited.add(nextColumn);
                    queue.enqueue(next);
                }
            }
        }
        return reachedCount == count;
    }

    private boolean reachesTerminal(RiverCourse course, HydrologyTerrainSampler sampler,
                                    HydrologyPlannerSettings settings, LongOpenHashSet visited) {
        HydraulicSegment first = course.segments().getFirst();
        HydraulicSegment last = course.segments().getLast();
        return reaches(last, false, sampler, settings, visited)
                && (first.type() != HydrologyFeatureType.MOUTH || reaches(first, true, sampler, settings, visited));
    }

    private boolean reaches(HydraulicSegment mouth, boolean upstream, HydrologyTerrainSampler sampler,
                            HydrologyPlannerSettings settings, LongOpenHashSet visited) {
        int seaLevel = settings.seaLevel();
        if (mouth.type() != HydrologyFeatureType.MOUTH || mouth.upstreamHeadY() != seaLevel
                || mouth.downstreamHeadY() != seaLevel) {
            return false;
        }
        HydrologyPoint endpoint = upstream ? mouth.start() : mouth.end();
        HydrologyTerrainSample terrain = sampler.sample(endpoint.x(), endpoint.z());
        if (terrain == null || !terrain.ocean() || terrain.naturalHeight() >= seaLevel) {
            return false;
        }
        HydrologyTerrainSampler receiver = HydrologyOceanReceiver.forMouth(settings, sampler, mouth);
        SurfaceCenterline centerline = SurfaceCenterline.densify(mouth.centerline());
        int step = upstream ? 1 : -1;
        int previousX = endpoint.x();
        int previousZ = endpoint.z();
        for (int station = upstream ? 0 : centerline.size() - 1; station >= 0 && station < centerline.size(); station += step) {
            int x = centerline.x()[station];
            int z = centerline.z()[station];
            if (x != previousX && z != previousZ) {
                boolean firstBridge = receiver.receivingWater(previousX, z, seaLevel);
                boolean secondBridge = receiver.receivingWater(x, previousZ, seaLevel);
                if (!firstBridge && !secondBridge) {
                    return false;
                }
                if (firstBridge && touchesOwnedWater(previousX, z, seaLevel, visited)
                        || secondBridge && touchesOwnedWater(x, previousZ, seaLevel, visited)) {
                    return true;
                }
            }
            if (!receiver.receivingWater(x, z, seaLevel)) {
                return false;
            }
            if (touchesOwnedWater(x, z, seaLevel, visited)) {
                return true;
            }
            previousX = x;
            previousZ = z;
        }
        return false;
    }

    private boolean touchesOwnedWater(int x, int z, int seaLevel, LongOpenHashSet visited) {
        for (int[] offset : NEIGHBORS) {
            long key = RiverFootprint.pack(x + offset[0], z + offset[1]);
            if (!visited.contains(key)) {
                continue;
            }
            LongArrayList layers = disjoint.get(key);
            if (layers == null) {
                if ((int) water.get(key) == seaLevel) {
                    return true;
                }
                continue;
            }
            for (int index = 0; index < layers.size(); index++) {
                if ((int) layers.getLong(index) == seaLevel) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long levels(int bed, int head) {
        return ((long) bed << 32) | (head & 0xffffffffL);
    }
}
