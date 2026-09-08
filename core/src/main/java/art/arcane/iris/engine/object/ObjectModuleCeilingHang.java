package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;

public class ObjectModuleCeilingHang extends PlacementModule {

    @Override
    public void onBlock(VectorMap<PlatformBlockState> blocks, VectorMap<TileData> states, PlatformBlockState block, TileData tile, IrisBlockVector vector) {
        vector.setY(-vector.getBlockY());
    }
}
