package art.arcane.iris.engine.object;


import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;

final class PlacementContext {
    public record TransformedBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    public record WorldBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    IrisObjectPlacement config;
    final IrisData rdata;
    final IObjectPlacer placer;
    final CarveResult caveResult;
    final RNG rng;
    ObjectSpinKernel spin;
    IrisObject self;
    int x;
    int z;
    int rty;
    int yv;

    public PlacementContext(
            IrisObjectPlacement config,
            IrisData rdata,
            IObjectPlacer placer,
            CarveResult caveResult,
            RNG rng,
            ObjectSpinKernel spin,
            IrisObject self,
            int x,
            int z,
            int yv,
            int rty
    ) {
        this.config = config;
        this.rdata = rdata;
        this.placer = placer;
        this.caveResult = caveResult;
        this.rng = rng;
        this.spin = spin;
        this.self = self;
        this.x = x;
        this.z = z;
        this.yv = yv;
        this.rty = rty;
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

    public TransformedBounds transformedBounds(ObjectSpinKernel spin, boolean translating, IrisBlockVector translateOffset,
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

        return new TransformedBounds(minX - margin, maxX + margin, minY, maxY, minZ - margin, maxZ + margin);
    }

    WorldBounds resolveWorldBounds(TransformedBounds bounds, boolean paint, int x, int y, int z) {
        int minX = x + bounds.minX();
        int maxX = x + bounds.maxX();
        int minZ = z + bounds.minZ();
        int maxZ = z + bounds.maxZ();
        if (!paint) {
            return new WorldBounds(minX, maxX, y + bounds.minY(), y + bounds.maxY(), minZ, maxZ);
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
        return new WorldBounds(minX, maxX, minimumSurface + bounds.minY() + paintOffset,
                maximumSurface + bounds.maxY() + paintOffset, minZ, maxZ);
    }
}
