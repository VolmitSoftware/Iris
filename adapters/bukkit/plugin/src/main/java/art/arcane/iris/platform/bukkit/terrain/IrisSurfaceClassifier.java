package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
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
            WaterRole role
    ) {
        if (engineSurfaceHeight <= 0) {
            return IrisSurfaceKind.VOID;
        }
        return switch (role) {
            case NONE -> classify(engineSurfaceHeight, engineFluidHeight, inferredType);
            case SHORE, POND_SHORE -> IrisSurfaceKind.RIVER_SHORE;
            case BANK, DAM -> IrisSurfaceKind.LAND;
            case CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET -> IrisSurfaceKind.RIVER;
        };
    }
}
