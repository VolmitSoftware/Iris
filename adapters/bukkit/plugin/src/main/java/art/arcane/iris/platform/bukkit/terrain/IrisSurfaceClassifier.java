package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.terrain.InferredType;

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

    public static IrisSurfaceKind classify(
            int engineSurfaceHeight,
            int engineFluidHeight,
            InferredType inferredType,
            HydrologyColumnSample hydrology
    ) {
        if (engineSurfaceHeight <= 0) {
            return IrisSurfaceKind.VOID;
        }
        HydrologyColumnLayer layer = hydrology == null
                ? null
                : hydrology.primarySurfaceLayer().orElse(null);
        if (layer != null) {
            if (layer.shore()) {
                return IrisSurfaceKind.RIVER_SHORE;
            }
            if (layer.channel()) {
                return layer.connectedFluid() ? IrisSurfaceKind.RIVER : IrisSurfaceKind.DRY_CHANNEL;
            }
            if (layer.grading()) {
                return IrisSurfaceKind.LAND;
            }
        }
        return classify(engineSurfaceHeight, engineFluidHeight, inferredType);
    }
}
