package art.arcane.iris.generation.terrain;

import java.util.Arrays;

public final class Terrain3DColumn {
    private final double baseHeight;
    private final int minY;
    private final boolean shaped;
    private final int[] boundaries;

    Terrain3DColumn(double baseHeight, int minY, boolean shaped, int[] boundaries) {
        this.baseHeight = baseHeight;
        this.minY = minY;
        this.shaped = shaped;
        this.boundaries = boundaries.clone();
    }

    public static Terrain3DColumn unshaped(double baseHeight, int height) {
        int top = Math.clamp(Math.round(baseHeight), 0, height - 1);
        return new Terrain3DColumn(baseHeight, top + 1, false, new int[]{0, top});
    }

    public double baseHeight() {
        return baseHeight;
    }

    public int topY() {
        return boundaries[boundaries.length - 1];
    }

    public int minY() {
        return minY;
    }

    public boolean shaped() {
        return shaped;
    }

    public int spanCount() {
        return boundaries.length / 2;
    }

    public int floor(int span) {
        return boundaries[span * 2 + 1];
    }

    public int ceiling(int span) {
        return boundaries[span * 2];
    }

    public boolean isSolid(int y) {
        return surfaceY(y) >= 0;
    }

    public int highestSolidY(int maximumY) {
        for (int index = boundaries.length - 2; index >= 0; index -= 2) {
            if (maximumY >= boundaries[index]) {
                return Math.min(maximumY, boundaries[index + 1]);
            }
        }
        return -1;
    }

    public int surfaceY(int y) {
        for (int index = 0; index < boundaries.length; index += 2) {
            if (y < boundaries[index]) {
                return -1;
            }
            if (y <= boundaries[index + 1]) {
                return boundaries[index + 1];
            }
        }
        return -1;
    }

    public int nearestSurfaceY(int y) {
        int nearest = boundaries[1];
        long distance = Math.abs((long) nearest - y);
        for (int index = 3; index < boundaries.length; index += 2) {
            long candidateDistance = Math.abs((long) boundaries[index] - y);
            if (candidateDistance < distance) {
                nearest = boundaries[index];
                distance = candidateDistance;
            }
        }
        return nearest;
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof Terrain3DColumn other
                && Double.compare(baseHeight, other.baseHeight) == 0
                && minY == other.minY && shaped == other.shaped
                && Arrays.equals(boundaries, other.boundaries);
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(baseHeight);
        result = 31 * result + minY;
        result = 31 * result + Boolean.hashCode(shaped);
        return 31 * result + Arrays.hashCode(boundaries);
    }
}
