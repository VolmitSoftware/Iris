package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.math.IrisBlockVector;
import lombok.Getter;

@Getter
class ObjectPlacementConditions {

    record WorldBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    private IrisObjectPlacement config;
    private IObjectPlacer placer;
    private IrisObject self;

    ObjectPlacementConditions(IrisObjectPlacement config, IObjectPlacer placer, IrisObject self) {
        this.config = config;
        this.placer = placer;
        this.self = self;
    }


    public boolean shouldBailForCarvingAnchor(int x, int y, int z) {
        CarvingMode carvingMode = config.getCarvingSupport();
        return switch (carvingMode) {
            case SURFACE_ONLY -> placer.isCarved(x, y, z);
            case CARVING_ONLY -> !isCarvedCaveAnchor(x, y, z);
            case ANYWHERE -> false;
        };
    }

    public boolean isCarvedCaveAnchor(int x, int y, int z) {
        return placer.isCarved(x, y, z)
                || placer.isCarved(x, y - 1, z)
                || placer.isCarved(x, y - 2, z)
                || placer.isCarved(x, y - 3, z);
    }

    public PlacementContext.TransformedBounds transformedBounds(ObjectSpinKernel spin, boolean translating, IrisBlockVector translateOffset,
                                                                 boolean ceilingHang, int margin) {
        int sourceMinX = -self.getCenter().getBlockX();
        int sourceMaxX = self.getW() - self.getCenter().getBlockX() - 1;
        int sourceMinY = -self.getCenter().getBlockY();
        int sourceMaxY = self.getH() - self.getCenter().getBlockY() - 1;
        int sourceMinZ = -self.getCenter().getBlockZ();
        int sourceMaxZ = self.getD() - self.getCenter().getBlockZ() - 1;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int xCorner = 0; xCorner < 2; xCorner++) {
            int sourceX = xCorner == 0 ? sourceMinX : sourceMaxX;
            for (int yCorner = 0; yCorner < 2; yCorner++) {
                int sourceY = yCorner == 0 ? sourceMinY : sourceMaxY;
                for (int zCorner = 0; zCorner < 2; zCorner++) {
                    int sourceZ = zCorner == 0 ? sourceMinZ : sourceMaxZ;
                    IrisBlockVector corner = new IrisBlockVector(sourceX, sourceY, sourceZ);
                    spin.rotate(corner);
                    if (ceilingHang) {
                        corner.setY(-corner.getBlockY());
                    }
                    if (translating) {
                        corner.add(translateOffset);
                    }
                    int transformedX = (int) Math.round(corner.getX());
                    int transformedY = (int) Math.round(corner.getY());
                    int transformedZ = (int) Math.round(corner.getZ());
                    minX = Math.min(minX, transformedX);
                    maxX = Math.max(maxX, transformedX);
                    minY = Math.min(minY, transformedY);
                    maxY = Math.max(maxY, transformedY);
                    minZ = Math.min(minZ, transformedZ);
                    maxZ = Math.max(maxZ, transformedZ);
                }
            }
        }

        return new PlacementContext.TransformedBounds(minX - margin, maxX + margin, minY, maxY, minZ - margin, maxZ + margin);
    }

    ObjectPlacementConditions.WorldBounds resolveWorldBounds(PlacementContext.TransformedBounds bounds,
                                                             boolean paint, int x, int y, int z) {
        int minX = x + bounds.minX();
        int maxX = x + bounds.maxX();
        int minZ = z + bounds.minZ();
        int maxZ = z + bounds.maxZ();
        if (!paint) {
            return new ObjectPlacementConditions.WorldBounds(minX, maxX, y + bounds.minY(), y + bounds.maxY(), minZ, maxZ);
        }

        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        for (int worldX = minX; worldX <= maxX; worldX++) {
            for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
                int surface = placer.getHighest(worldX, worldZ, self.getLoader(), config.isUnderwater());
                minimumSurface = Math.min(minimumSurface, surface);
                maximumSurface = Math.max(maximumSurface, surface);
            }
        }
        int paintOffset = Math.floorDiv(self.h, 2);
        return new ObjectPlacementConditions.WorldBounds(minX, maxX, minimumSurface + bounds.minY() + paintOffset,
                maximumSurface + bounds.maxY() + paintOffset, minZ, maxZ);
    }

}
