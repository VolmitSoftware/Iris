package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;

public abstract class PlacementModule {
    PlacementContext context;
    PlacementState state;

    final PlacementModule load(PlacementContext context, PlacementState state) {
        this.context = context;
        this.state = state;
        return this;
    }

    void install() {

    }

    void setHeight() {}

    void onBlock(VectorMap<PlatformBlockState> blocks, VectorMap<TileData> states, PlatformBlockState block, TileData tile, IrisBlockVector vector) {}

    void postHeight() {}

}
