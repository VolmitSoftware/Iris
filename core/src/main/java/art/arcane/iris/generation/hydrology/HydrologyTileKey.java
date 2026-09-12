package art.arcane.iris.generation.hydrology;

public record HydrologyTileKey(int tileX, int tileZ) implements Comparable<HydrologyTileKey> {
    public static HydrologyTileKey fromBlock(int blockX, int blockZ, int tileSize) {
        if (tileSize <= 0) {
            throw new IllegalArgumentException("Tile size must be positive.");
        }
        return new HydrologyTileKey(Math.floorDiv(blockX, tileSize), Math.floorDiv(blockZ, tileSize));
    }

    public int minimumBlockX(int tileSize) {
        return Math.multiplyExact(tileX, tileSize);
    }

    public int minimumBlockZ(int tileSize) {
        return Math.multiplyExact(tileZ, tileSize);
    }

    public boolean contains(int blockX, int blockZ, int tileSize) {
        int minimumX = minimumBlockX(tileSize);
        int minimumZ = minimumBlockZ(tileSize);
        return blockX >= minimumX && blockX < minimumX + tileSize
                && blockZ >= minimumZ && blockZ < minimumZ + tileSize;
    }

    @Override
    public int compareTo(HydrologyTileKey other) {
        int xComparison = Integer.compare(tileX, other.tileX);
        return xComparison != 0 ? xComparison : Integer.compare(tileZ, other.tileZ);
    }
}
