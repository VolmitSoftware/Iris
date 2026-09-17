package art.arcane.iris.core.service.terrain;

import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.engine.object.world.biome.InferredType;

public final class IrisSurfaceClassifier {
    private IrisSurfaceClassifier() {
    }

    public static boolean requiresSurfaceBiome(int engineSurfaceHeight, int engineFluidHeight) {
        return engineSurfaceHeight > 0 && engineSurfaceHeight > engineFluidHeight;
    }

    public static IrisSurfaceKind classify(int engineSurfaceHeight, int engineFluidHeight, InferredType inferredType) {
        if (engineSurfaceHeight <= 0) {
            return IrisSurfaceKind.VOID;
        }

        if (engineSurfaceHeight <= engineFluidHeight) {
            return IrisSurfaceKind.OCEAN;
        }

        return inferredType == InferredType.SHORE ? IrisSurfaceKind.SHORE : IrisSurfaceKind.LAND;
    }
}
