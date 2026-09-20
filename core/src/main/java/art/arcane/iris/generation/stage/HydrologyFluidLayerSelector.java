package art.arcane.iris.generation.stage;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;

final class HydrologyFluidLayerSelector {
    private HydrologyFluidLayerSelector() {
    }

    static NativeBlockState select(
            KList<NativeBlockState> seaLayers,
            int depth,
            NativeBlockState fluid,
            boolean hydrologyOwned
    ) {
        if (!hydrologyOwned && seaLayers != null && seaLayers.hasIndex(depth)) {
            return seaLayers.get(depth);
        }
        return fluid;
    }
}
