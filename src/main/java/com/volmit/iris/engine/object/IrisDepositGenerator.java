/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

@Snippet("deposit")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Creates ore & other block deposits underground")
@Data
public class IrisDepositGenerator {
    private final transient AtomicCache<KList<IrisObject>> objects = new AtomicCache<>();
    private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
    @Required
    @MinNumber(0)
    @MaxNumber(8192) // TODO: WARNING HEIGHT
    @Desc("The minimum height this deposit can generate at")
    private int minHeight = 1;
    @Required
    @MinNumber(0)
    @MaxNumber(8192) // TODO: WARNING HEIGHT
    @Desc("The maximum height this deposit can generate at")
    private int maxHeight = 75;
    @Required
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("The minimum amount of deposit blocks per clump")
    private int minSize = 0;
    @Required
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("The maximum amount of deposit blocks per clump")
    private int maxSize = 128;
    @Required
    @MinNumber(0)
    @MaxNumber(2048)
    @Desc("The maximum amount of clumps per chunk")
    private int maxPerChunk = 3;
    @Required
    @MinNumber(0)
    @MaxNumber(2048)
    @Desc("The minimum amount of clumps per chunk")
    private int minPerChunk = 0;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The palette of blocks to be used in this deposit generator")
    private KList<IrisBlockData> palette = new KList<>();
    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Ore varience is how many different objects clumps iris will create")
    private int varience = 3;

    public IrisObject getClump(RNG rng, IrisData rdata) {
        KList<IrisObject> objects = this.objects.aquire(() ->
        {
            RNG rngv = rng.nextParallelRNG(3957778);
            KList<IrisObject> objectsf = new KList<>();

            for (int i = 0; i < varience; i++) {
                objectsf.add(generateClumpObject(rngv.nextParallelRNG(2349 * i + 3598), rdata));
            }

            return objectsf;
        });
        return objects.get(rng.i(0, objects.size() - 1));
    }

    public int getMaxDimension() {
        return Math.min(11, (int) Math.round(Math.pow(maxSize, 1D / 3D)));
    }

    private IrisObject generateClumpObject(RNG rngv, IrisData rdata) {
        int s = rngv.i(minSize, maxSize);
        int dim = Math.min(11, (int) Math.round(Math.pow(maxSize, 1D / 3D)));
        int w = dim / 2;
        IrisObject o = new IrisObject(dim, dim, dim);

        if (s == 1) {
            o.getBlocks().put(o.getCenter(), nextBlock(rngv, rdata));
        } else {
            while (s > 0) {
                s--;
                BlockVector ang = new BlockVector(rngv.i(-w, w), rngv.i(-w, w), rngv.i(-w, w));
                BlockVector pos = o.getCenter().clone().add(ang).toBlockVector();
                o.getBlocks().put(pos, nextBlock(rngv, rdata));
            }
        }

        return o;
    }

    private BlockData nextBlock(RNG rngv, IrisData rdata) {
        return getBlockData(rdata).get(rngv.i(0, getBlockData(rdata).size() - 1));
    }

    public KList<BlockData> getBlockData(IrisData rdata) {
        return blockData.aquire(() ->
        {
            KList<BlockData> blockData = new KList<>();

            for (IrisBlockData ix : palette) {
                BlockData bx = ix.getBlockData(rdata);

                if (bx != null) {
                    blockData.add(bx);
                }
            }

            return blockData;
        });
    }
}
