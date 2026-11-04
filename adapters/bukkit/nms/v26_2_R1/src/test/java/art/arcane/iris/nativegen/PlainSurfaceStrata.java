package art.arcane.iris.nativegen;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureSurfaceFitter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class PlainSurfaceStrata {
    static final NativeStructureSurfaceFitter.SurfaceStrata STRATA = (x, z, surfaceY, submerged) ->
            new NativeStructureSurfaceFitter.SurfaceStrata.Column() {
                @Override
                public BlockState block(int y) {
                    int depth = surfaceY - y;
                    if (depth == 0) {
                        return submerged ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    }
                    return depth <= 2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                }

                @Override
                public int soilDepth() {
                    return 3;
                }
            };

    static BlockState at(int surfaceY, int y) {
        return STRATA.column(0, 0, surfaceY, false).block(y);
    }

    private PlainSurfaceStrata() {
    }
}
