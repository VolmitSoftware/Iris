package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;

record CaveCandidateBounds(
        int entryX,
        int entryY,
        int entryZ,
        int waterHeadY,
        int horizontalRadius,
        int maximumDepth,
        int dryHeadroom
) {
    boolean containsColumn(int x, int z) {
        long deltaX = (long) x - entryX;
        long deltaZ = (long) z - entryZ;
        long radiusSquared = (long) horizontalRadius * horizontalRadius;
        return deltaX * deltaX + deltaZ * deltaZ <= radiusSquared;
    }

    int minimumY() {
        return Math.toIntExact((long) entryY - maximumDepth);
    }

    int maximumY() {
        return Math.max(entryY, Math.addExact(waterHeadY, dryHeadroom + 1));
    }

    boolean contains(CavePosition position) {
        if (!containsColumn(position.x(), position.z())) {
            return false;
        }
        return position.y() >= minimumY() && position.y() <= maximumY();
    }
}
