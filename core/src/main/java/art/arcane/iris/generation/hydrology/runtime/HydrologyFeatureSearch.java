package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.generation.hydrology.HydrologyTileKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring order for feature searches: tiles are visited outward from the origin tile so a search can
 * stop as soon as no unvisited tile could hold a nearer feature, instead of planning the whole box.
 */
final class HydrologyFeatureSearch {
    private HydrologyFeatureSearch() {
    }

    /** Tiles at exactly Chebyshev distance {@code radius} from the origin tile, in row-major order. */
    static List<HydrologyTileKey> ring(int originTileX, int originTileZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative.");
        }
        if (radius == 0) {
            return List.of(new HydrologyTileKey(originTileX, originTileZ));
        }
        ArrayList<HydrologyTileKey> keys = new ArrayList<>(radius * 8);
        for (int tileZ = originTileZ - radius; tileZ <= originTileZ + radius; tileZ++) {
            boolean edgeRow = tileZ == originTileZ - radius || tileZ == originTileZ + radius;
            for (int tileX = originTileX - radius; tileX <= originTileX + radius; tileX++) {
                boolean edgeColumn = tileX == originTileX - radius || tileX == originTileX + radius;
                if (edgeRow || edgeColumn) {
                    keys.add(new HydrologyTileKey(tileX, tileZ));
                }
            }
        }
        return List.copyOf(keys);
    }

    /** Number of rings needed so every tile that can publish within {@code maximumDistance} is visited. */
    static int ringLimit(int maximumDistance, int tileSize, int publicationRadius) {
        if (maximumDistance < 0 || tileSize <= 0 || publicationRadius < 0) {
            throw new IllegalArgumentException("Hydrology feature search bounds are invalid.");
        }
        long reach = (long) maximumDistance + publicationRadius;
        return Math.toIntExact((reach + tileSize - 1) / tileSize);
    }

    /**
     * Smallest block distance a feature published by a ring-{@code radius} tile can have from the
     * origin: the ring starts {@code radius - 1} tiles away and a tile's footprint reaches
     * {@code publicationRadius} beyond its bounds.
     */
    static long lowerBound(int radius, int tileSize, int publicationRadius) {
        if (radius <= 0) {
            return 0L;
        }
        return Math.max(0L, (long) (radius - 1) * tileSize - publicationRadius);
    }
}
