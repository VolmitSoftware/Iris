package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

@AllArgsConstructor
@NoArgsConstructor
@Desc("Dimension Merging only supports 1 for now.")
@Data
public class IrisMerger {
    @Desc("Selected Generator")
    private String generator;

    @Desc("Uses the generator as a datapack key")
    private boolean datapackMode;

    @Desc("Merging strategy")
    private IrisMergeStrategies mode;

    /**
     * Merges caves from a selected chunk into the corresponding chunk in the outcome world.
     * This is calling 1/16th of a chunk x/z slice. It is a plane from sky to bedrock 1 thick in the x direction.
     *
     * @param x  the chunk x in blocks
     * @param z  the chunk z in blocks
     * @param xf the current x slice
     * @param h  the blockdata
     */
    @BlockCoordinates
    private void terrainMergeSliver(int x, int z, int xf, Hunk<BlockData> h, Engine engine) {
        int zf, realX, realZ, hf, he;

        for (zf = 0; zf < h.getDepth(); zf++) {
            realX = xf + x;
            realZ = zf + z;

            for (int i = h.getHeight(); i >= 0; i--) {




            }


        }
    }
}
