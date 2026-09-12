package art.arcane.iris.generation.hydrology.runtime;

final class HydrologyFeatureSearchBounds {
    private HydrologyFeatureSearchBounds() {
    }

    static int maximumDistance(
            int x,
            int z,
            int requestedDistance,
            int tileSize,
            int publicationRadius,
            int maximumTiles
    ) {
        if (requestedDistance < 0 || tileSize <= 0 || publicationRadius < 0 || maximumTiles < 1) {
            throw new IllegalArgumentException("Hydrology feature search bounds are invalid.");
        }
        if (tileCount(x, z, 0, tileSize, publicationRadius) > maximumTiles) {
            throw new IllegalArgumentException("Hydrology publication radius exceeds the bounded tile limit.");
        }
        int minimum = 0;
        int maximum = requestedDistance;
        int accepted = 0;
        while (minimum <= maximum) {
            int candidate = minimum + (maximum - minimum) / 2;
            if (tileCount(x, z, candidate, tileSize, publicationRadius) <= maximumTiles) {
                accepted = candidate;
                minimum = candidate + 1;
            } else {
                maximum = candidate - 1;
            }
        }
        return accepted;
    }

    static long tileCount(int x, int z, int distance, int tileSize, int publicationRadius) {
        int minimumTileX = tileCoordinate((long) x - distance - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) x + distance + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) z - distance - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) z + distance + publicationRadius, tileSize);
        return (long) (maximumTileX - minimumTileX + 1) * (maximumTileZ - minimumTileZ + 1);
    }

    private static int tileCoordinate(long blockCoordinate, int tileSize) {
        return Math.toIntExact(Math.floorDiv(blockCoordinate, tileSize));
    }
}
