package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;

public abstract class PlacementBehavior {
    PlacementContext context;
    ObjectPlacementConditions util;

    final PlacementBehavior load(PlacementContext context, ObjectPlacementConditions util) {
        this.context = context;
        this.util = util;
        return this;
    }

    void setHeight() {}

    void onBlock(VectorMap<PlatformBlockState> blocks, VectorMap<TileData> states, PlatformBlockState block, TileData tile, IrisBlockVector vector) {}

    void postHeight() {}

}
