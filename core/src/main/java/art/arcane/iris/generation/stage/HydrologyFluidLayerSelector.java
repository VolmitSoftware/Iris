package art.arcane.iris.generation.stage;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;

final class HydrologyFluidLayerSelector {
    private HydrologyFluidLayerSelector() {
    }

    static PlatformBlockState select(
            KList<PlatformBlockState> seaLayers,
            int depth,
            PlatformBlockState fluid,
            boolean hydrologyOwned
    ) {
        if (!hydrologyOwned && seaLayers != null && seaLayers.hasIndex(depth)) {
            return seaLayers.get(depth);
        }
        return fluid;
    }
}
