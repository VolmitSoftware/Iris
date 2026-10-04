package art.arcane.iris.generation.subterrain;

public record SubterrainBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(int lowX, int lowZ, int highX, int highZ) {
        return minX <= highX && maxX >= lowX && minZ <= highZ && maxZ >= lowZ;
    }
}
