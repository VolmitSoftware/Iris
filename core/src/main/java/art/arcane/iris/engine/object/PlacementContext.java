package art.arcane.iris.engine.object;


import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;

final class PlacementContext {
    public record TransformedBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    IrisObjectPlacement config;
    final IrisData rdata;
    final IObjectPlacer placer;
    final CarveResult caveResult;
    final RNG rng;
    ObjectSpinKernel spin;
    IrisObject self;
    TransformedBounds bounds;
    boolean bail;

    int x;
    int z;
    int y;
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
            TransformedBounds bounds,
            int x,
            int y,
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
        this.bounds = bounds;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yv = yv;
        this.rty = rty;
    }
}