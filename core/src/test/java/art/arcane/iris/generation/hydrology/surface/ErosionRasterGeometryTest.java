package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ErosionRasterGeometryTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    public void rasterMatchesTheReferenceOwnersDistancesAndIterationOrder() {
        SplittableRandom random = new SplittableRandom(0x5EED_E05EL);
        int compared = 0;
        for (int trial = 0; trial < 160; trial++) {
            SurfaceCenterline centerline = randomCenterline(random);
            int count = Math.max(1, centerline.size() - random.nextInt(Math.min(4, centerline.size())));
            int[] radii = new int[count];
            for (int station = 0; station < count; station++) {
                radii[station] = 1 + random.nextInt(trial % 3 == 0 ? 48 : 12);
            }
            SurfaceBounds bounds = trial % 4 == 0 ? null : randomBounds(random, centerline);
            Long2IntOpenHashMap expectedIndices = new Long2IntOpenHashMap();
            expectedIndices.defaultReturnValue(-1);
            IntArrayList expectedStations = new IntArrayList();
            DoubleArrayList expectedDistances = new DoubleArrayList();
            reference(centerline, radii, count, bounds, expectedIndices, expectedStations, expectedDistances);
            Long2IntOpenHashMap actualIndices = new Long2IntOpenHashMap();
            actualIndices.defaultReturnValue(-1);
            IntArrayList actualStations = new IntArrayList();
            DoubleArrayList actualDistances = new DoubleArrayList();
            ErosionFieldCompiler.rasterize(centerline, radii, count, bounds, actualIndices, actualStations, actualDistances);

            assertEquals(expectedIndices.size(), actualIndices.size());
            LongIterator expectedKeys = expectedIndices.keySet().iterator();
            LongIterator actualKeys = actualIndices.keySet().iterator();
            while (expectedKeys.hasNext()) {
                long expectedKey = expectedKeys.nextLong();
                long actualKey = actualKeys.nextLong();
                assertEquals(expectedKey, actualKey);
                int expectedIndex = expectedIndices.get(expectedKey);
                int actualIndex = actualIndices.get(actualKey);
                assertEquals(expectedIndex, actualIndex);
                assertEquals(expectedStations.getInt(expectedIndex), actualStations.getInt(actualIndex));
                assertEquals(Double.doubleToLongBits(expectedDistances.getDouble(expectedIndex)),
                        Double.doubleToLongBits(actualDistances.getDouble(actualIndex)));
                compared++;
            }
            assertEquals(expectedStations.size(), actualStations.size());
        }
        assertTrue(compared > 100_000);
    }

    private static SurfaceCenterline randomCenterline(SplittableRandom random) {
        int originX = random.nextInt(-40_000, 40_000);
        int originZ = random.nextInt(-40_000, 40_000);
        int points = 2 + random.nextInt(6);
        List<HydrologyPoint> path = new ArrayList<>(points);
        int x = originX;
        int z = originZ;
        for (int point = 0; point < points; point++) {
            path.add(new HydrologyPoint(x, 64, z));
            x += random.nextInt(-90, 91);
            z += random.nextInt(-90, 91);
        }
        return SurfaceCenterline.densify(path);
    }

    private static SurfaceBounds randomBounds(SplittableRandom random, SurfaceCenterline centerline) {
        int station = random.nextInt(centerline.size());
        int centerX = centerline.x()[station];
        int centerZ = centerline.z()[station];
        int halfWidth = 4 + random.nextInt(60);
        int halfDepth = 4 + random.nextInt(60);
        return new SurfaceBounds(centerX - halfWidth, centerZ - halfDepth, centerX + halfWidth, centerZ + halfDepth)
                .expand(2);
    }

    private static void reference(SurfaceCenterline centerline, int[] radii, int count, SurfaceBounds samplingBounds,
                                  Long2IntOpenHashMap indices, IntArrayList stations, DoubleArrayList distance) {
        for (int station = 0; station < count; station++) {
            int radius = radii[station];
            int stationX = centerline.x()[station];
            int stationZ = centerline.z()[station];
            if (samplingBounds != null && !samplingBounds.intersects(stationX, stationZ, radius)) {
                continue;
            }
            int minimumZ = samplingBounds == null ? -radius : Math.max(-radius, samplingBounds.minimumZ() - stationZ);
            int maximumZ = samplingBounds == null ? radius : Math.min(radius, samplingBounds.maximumZ() - stationZ);
            int minimumX = samplingBounds == null ? -radius : Math.max(-radius, samplingBounds.minimumX() - stationX);
            int maximumX = samplingBounds == null ? radius : Math.min(radius, samplingBounds.maximumX() - stationX);
            for (int deltaZ = minimumZ; deltaZ <= maximumZ; deltaZ++) {
                for (int deltaX = minimumX; deltaX <= maximumX; deltaX++) {
                    int cellX = stationX + deltaX;
                    int cellZ = stationZ + deltaZ;
                    double cellDistance = centerline.distanceToSegment(station, cellX, cellZ, SurfaceDistanceTable.SHARED);
                    if (cellDistance > radius) {
                        continue;
                    }
                    long key = RiverFootprint.pack(cellX, cellZ);
                    int index = indices.putIfAbsent(key, stations.size());
                    if (index < 0) {
                        stations.add(station);
                        distance.add(cellDistance);
                        continue;
                    }
                    int existing = stations.getInt(index);
                    double existingDistance = distance.getDouble(index);
                    if (cellDistance < existingDistance - EPSILON
                            || Math.abs(cellDistance - existingDistance) <= EPSILON && station > existing) {
                        stations.set(index, station);
                        distance.set(index, cellDistance);
                    }
                }
            }
        }
    }
}
