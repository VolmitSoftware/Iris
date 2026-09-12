package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyPoint;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;
import java.util.Objects;

public record SurfaceCenterline(int[] x, int[] z, double[] tangentX, double[] tangentZ, int[] pathIndex) {
    private static final int TANGENT_WINDOW = 6;

    public static SurfaceCenterline densify(List<HydrologyPoint> path) {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("A surface centerline requires at least one path point.");
        }
        IntArrayList xs = new IntArrayList();
        IntArrayList zs = new IntArrayList();
        IntArrayList indices = new IntArrayList();
        HydrologyPoint first = path.getFirst();
        xs.add(first.x());
        zs.add(first.z());
        indices.add(0);
        for (int pair = 0; pair + 1 < path.size(); pair++) {
            HydrologyPoint start = path.get(pair);
            HydrologyPoint end = path.get(pair + 1);
            int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
            for (int step = 1; step <= steps; step++) {
                double progress = step / (double) steps;
                int cellX = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
                int cellZ = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
                int last = xs.size() - 1;
                if (xs.getInt(last) == cellX && zs.getInt(last) == cellZ) {
                    continue;
                }
                xs.add(cellX);
                zs.add(cellZ);
                indices.add(pair);
            }
        }
        return fromStations(xs.toIntArray(), zs.toIntArray(), indices.toIntArray());
    }

    private static SurfaceCenterline fromStations(int[] xs, int[] zs, int[] indices) {
        int count = xs.length;
        double[] tangentX = new double[count];
        double[] tangentZ = new double[count];
        for (int station = 0; station < count; station++) {
            int from = Math.max(0, station - TANGENT_WINDOW);
            int to = Math.min(count - 1, station + TANGENT_WINDOW);
            double vectorX = xs[to] - xs[from];
            double vectorZ = zs[to] - zs[from];
            double length = StrictMath.hypot(vectorX, vectorZ);
            if (length <= 0D) {
                tangentX[station] = 1D;
                tangentZ[station] = 0D;
            } else {
                tangentX[station] = vectorX / length;
                tangentZ[station] = vectorZ / length;
            }
        }
        return new SurfaceCenterline(xs, zs, tangentX, tangentZ, indices);
    }

    public int size() {
        return x.length;
    }

    public double normalX(int station) {
        return -tangentZ[station];
    }

    public double normalZ(int station) {
        return tangentX[station];
    }

    public double distanceToSegment(int station, double pointX, double pointZ) {
        double startX = x[station];
        double startZ = z[station];
        if (station + 1 >= x.length) {
            return StrictMath.hypot(pointX - startX, pointZ - startZ);
        }
        double endX = x[station + 1];
        double endZ = z[station + 1];
        double segmentX = endX - startX;
        double segmentZ = endZ - startZ;
        double lengthSquared = segmentX * segmentX + segmentZ * segmentZ;
        double progress = lengthSquared <= 0D
                ? 0D
                : ((pointX - startX) * segmentX + (pointZ - startZ) * segmentZ) / lengthSquared;
        progress = Math.max(0D, Math.min(1D, progress));
        double closestX = startX + segmentX * progress;
        double closestZ = startZ + segmentZ * progress;
        return StrictMath.hypot(pointX - closestX, pointZ - closestZ);
    }

    public SurfaceCenterline truncate(int stations) {
        if (stations < 1 || stations > x.length) {
            throw new IllegalArgumentException("Cannot truncate a centerline of " + x.length + " stations to " + stations + ".");
        }
        int[] xs = new int[stations];
        int[] zs = new int[stations];
        int[] indices = new int[stations];
        System.arraycopy(x, 0, xs, 0, stations);
        System.arraycopy(z, 0, zs, 0, stations);
        System.arraycopy(pathIndex, 0, indices, 0, stations);
        return fromStations(xs, zs, indices);
    }
}
