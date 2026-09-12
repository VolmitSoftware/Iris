package art.arcane.iris.world.history;

import java.util.Objects;
import java.util.function.IntBinaryOperator;

public final class FloatingBiomeOverlay {
    private final int height;
    private final int quartHeight;
    private final Identity[] volume;
    private final Surface[] surfaces = new Surface[256];

    public FloatingBiomeOverlay(int height) {
        if (height <= 0 || height > SavedBiomeChunk.MAXIMUM_HEIGHT) {
            throw new IllegalArgumentException("Invalid floating biome height: " + height);
        }
        this.height = height;
        quartHeight = (height + 3) / 4;
        volume = new Identity[16 * quartHeight];
    }

    public void record(int localX, int y, int localZ, Identity identity) {
        requirePosition(localX, y, localZ);
        Objects.requireNonNull(identity, "identity");
        if ((localX & 3) == 0 && (localZ & 3) == 0 && (y & 3) == 0) {
            volume[index(localX, y, localZ)] = identity;
        }
        int column = localX * 16 + localZ;
        Surface previous = surfaces[column];
        if (previous == null || y >= previous.y()) {
            surfaces[column] = new Surface(y, identity);
        }
    }

    public Identity volumeAt(int localX, int y, int localZ) {
        requirePosition(localX, y, localZ);
        return volume[index(localX, y, localZ)];
    }

    public void retainHighestSurfaces(IntBinaryOperator highestSolid) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int column = localX * 16 + localZ;
                Surface surface = surfaces[column];
                if (surface != null && highestSolid.applyAsInt(localX, localZ) != surface.y()) {
                    surfaces[column] = null;
                }
            }
        }
    }

    public Identity surfaceAt(int localX, int localZ) {
        requirePosition(localX, 0, localZ);
        Surface surface = surfaces[localX * 16 + localZ];
        return surface == null ? null : surface.identity();
    }

    public int surfaceYAt(int localX, int localZ) {
        requirePosition(localX, 0, localZ);
        Surface surface = surfaces[localX * 16 + localZ];
        return surface == null ? -1 : surface.y();
    }

    public int height() {
        return height;
    }

    private int index(int localX, int y, int localZ) {
        return ((localX >> 2) * 4 + (localZ >> 2)) * quartHeight + (y >> 2);
    }

    private void requirePosition(int localX, int y, int localZ) {
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16 || y < 0 || y >= height) {
            throw new IllegalArgumentException("Floating biome position is outside its generation chunk.");
        }
    }

    public record Identity(String biomeKey, String regionKey) {
        public Identity {
            Objects.requireNonNull(biomeKey, "biomeKey");
            Objects.requireNonNull(regionKey, "regionKey");
        }
    }

    private record Surface(int y, Identity identity) {
    }
}
