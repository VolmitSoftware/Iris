package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;

/** Source data and a placement-owned vector; changing the vector does not change the cached object. */
public record PlacementBlock(PlatformBlockState data, TileData tile, IrisBlockVector vector) {
}
