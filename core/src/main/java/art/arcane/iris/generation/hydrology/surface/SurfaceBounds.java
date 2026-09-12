package art.arcane.iris.generation.hydrology.surface;

public record SurfaceBounds(int minimumX, int minimumZ, int maximumX, int maximumZ) {
    public SurfaceBounds {
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Surface bounds must be ordered.");
        }
    }

    public boolean contains(int x, int z) {
        return x >= minimumX && x <= maximumX && z >= minimumZ && z <= maximumZ;
    }

    public boolean intersects(int x, int z, int radius) {
        return (long) x + radius >= minimumX && (long) x - radius <= maximumX
                && (long) z + radius >= minimumZ && (long) z - radius <= maximumZ;
    }

    public SurfaceBounds expand(int distance) {
        return new SurfaceBounds(Math.subtractExact(minimumX, distance), Math.subtractExact(minimumZ, distance),
                Math.addExact(maximumX, distance), Math.addExact(maximumZ, distance));
    }
}
