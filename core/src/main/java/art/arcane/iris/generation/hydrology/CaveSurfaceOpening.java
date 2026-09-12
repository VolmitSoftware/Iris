package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;

import java.util.List;

record CaveSurfaceOpening(
        long segmentId,
        HydrologyPoint point,
        double interiorX,
        double interiorZ,
        long radiusSquared,
        int minimumY,
        boolean includeNeighborhood,
        boolean surfaceFall
) {
    static CaveSurfaceOpening create(
            HydraulicSegment segment,
            boolean start,
            long radiusSquared,
            int minimumY,
            boolean includeNeighborhood
    ) {
        List<HydrologyPoint> centerline = segment.centerline();
        int boundaryIndex = start ? 0 : centerline.size() - 1;
        int interiorIndex = start
                ? Math.min(1, centerline.size() - 1)
                : Math.max(0, centerline.size() - 2);
        HydrologyPoint point = centerline.get(boundaryIndex);
        HydrologyPoint interior = centerline.get(interiorIndex);
        double deltaX = interior.x() - point.x();
        double deltaZ = interior.z() - point.z();
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        double scale = 0D;
        if (includeNeighborhood) {
            scale = lengthSquared <= radiusSquared
                    ? 1D
                    : StrictMath.sqrt(radiusSquared / lengthSquared);
        }
        return new CaveSurfaceOpening(
                segment.id(),
                point,
                point.x() + deltaX * scale,
                point.z() + deltaZ * scale,
                radiusSquared,
                minimumY,
                includeNeighborhood,
                segment.type().isSurface() && segment.fallingFluid()
        );
    }

    boolean matches(HydrologyColumnLayer layer, CavePosition position) {
        return matchesColumn(layer, position.x(), position.z())
                && position.y() >= minimumY;
    }

    boolean matchesColumn(HydrologyColumnLayer layer, int x, int z) {
        if (surfaceFall) {
            return layer.fallingFluid() && layer.feature().segmentId() == segmentId;
        }
        return (includeNeighborhood || layer.feature().segmentId() == segmentId)
                && distanceSquared(x, z) <= radiusSquared;
    }

    private double distanceSquared(int x, int z) {
        double pathX = interiorX - point.x();
        double pathZ = interiorZ - point.z();
        double pathLengthSquared = pathX * pathX + pathZ * pathZ;
        double positionX = x - point.x();
        double positionZ = z - point.z();
        double progress = pathLengthSquared == 0D
                ? 0D
                : (positionX * pathX + positionZ * pathZ) / pathLengthSquared;
        double clampedProgress = Math.max(0D, Math.min(1D, progress));
        double deltaX = positionX - pathX * clampedProgress;
        double deltaZ = positionZ - pathZ * clampedProgress;
        return deltaX * deltaX + deltaZ * deltaZ;
    }
}
