package art.arcane.iris.core.gui;

import java.util.Objects;

public record PregenRenderSnapshot(
        Bounds bounds,
        PregeneratorJob.PregenProgress progress,
        Phase phase,
        boolean cached,
        long usedMemoryBytes,
        double memoryUsage,
        long allocationBytesPerSecond,
        String failure
) {
    public PregenRenderSnapshot {
        Objects.requireNonNull(bounds, "Map bounds");
        Objects.requireNonNull(progress, "Generation progress");
        Objects.requireNonNull(phase, "Generation phase");
    }

    public enum Phase {
        INITIALIZING,
        GENERATING,
        PAUSED,
        SAVING,
        STOPPING,
        COMPLETED,
        ERROR
    }

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public Bounds {
            if (maxX < minX || maxZ < minZ) {
                throw new IllegalArgumentException("Map bounds must contain at least one chunk");
            }
        }

        public long width() {
            return (long) maxX - minX + 1;
        }

        public long height() {
            return (long) maxZ - minZ + 1;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }
}
