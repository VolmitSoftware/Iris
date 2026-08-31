package art.arcane.iris.engine.object;


import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;

public final class PlacementContext {
    public record TransformedBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    IrisObjectPlacement config;
    final IObjectPlacer placer;
    final CarveResult caveResult;
    final RNG rng;
    IrisObject self;
    TransformedBounds bounds;
    boolean bail;

    int x;
    int z;
    int y;
    int rty;

    public PlacementContext(
            IrisObjectPlacement config,
            IObjectPlacer placer,
            CarveResult caveResult,
            RNG rng,
            IrisObject self,
            int x,
            int y,
            int z,
            int rty
    ) {
        this.config = config;
        this.placer = placer;
        this.caveResult = caveResult;
        this.rng = rng;
        this.self = self;
        this.x = x;
        this.y = y;
        this.z = z;
    }

        public boolean shouldBailForCarvingAnchor() {
       return shouldBailForCarvingAnchor(x,y,z);
    }

    public boolean shouldBailForCarvingAnchor(int x, int y, int z) {
        CarvingMode carvingMode = config.getCarvingSupport();
        return switch (carvingMode) {
            case SURFACE_ONLY -> placer.isCarved(x, y, z);
            case CARVING_ONLY -> !isCarvedCaveAnchor(x, y, z);
            case ANYWHERE -> false;
        };
    }

    public boolean isCarvedCaveAnchor() {
        return isCarvedCaveAnchor(x, y, z);
    }

    public boolean isCarvedCaveAnchor(int x, int y, int z) {
        return placer.isCarved(x, y, z)
                || placer.isCarved(x, y - 1, z)
                || placer.isCarved(x, y - 2, z)
                || placer.isCarved(x, y - 3, z);
    }
}