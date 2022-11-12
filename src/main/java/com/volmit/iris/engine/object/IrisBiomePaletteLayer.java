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
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Snippet("biome-palette")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer {
    private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
    private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
    @Desc("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(0)
    @MaxNumber(2032) // TODO: WARNING HEIGHT

    @Desc("The min thickness of this layer")
    private int minHeight = 1;
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(1)
    @MaxNumber(2032) // TODO: WARNING HEIGHT

    @Desc("The max thickness of this layer")
    private int maxHeight = 1;
    @Desc("If set, this layer will change size depending on the slope. If in bounds, the layer will get larger (taller) the closer to the center of this slope clip it is. If outside of the slipe's bounds, this layer will not show.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @MinNumber(0.0001)
    @Desc("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("GRASS_BLOCK"));

    public CNG getHeightGenerator(RNG rng, IrisData data) {
        return heightGenerator.aquire(() -> CNG.signature(rng.nextParallelRNG(minHeight * maxHeight + getBlockData(data).size())));
    }

    public BlockData get(RNG rng, double x, double y, double z, IrisData data) {
        if (getBlockData(data).isEmpty()) {
            return null;
        }

        if (getBlockData(data).size() == 1) {
            return getBlockData(data).get(0);
        }

        return getLayerGenerator(rng, data).fit(getBlockData(data), x / zoom, y / zoom, z / zoom);
    }

    public CNG getLayerGenerator(RNG rng, IrisData data) {
        return layerGenerator.aquire(() ->
        {
            RNG rngx = rng.nextParallelRNG(minHeight + maxHeight + getBlockData(data).size());
            return style.create(rngx, data);
        });
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));

        return palette;
    }

    public KList<BlockData> getBlockData(IrisData data) {
        return blockData.aquire(() ->
        {
            KList<BlockData> blockData = new KList<>();
            for (IrisBlockData ix : palette) {
                BlockData bx = ix.getBlockData(data);
                if (bx != null) {
                    for (int i = 0; i < ix.getWeight(); i++) {
                        blockData.add(bx);
                    }
                }
            }

            return blockData;
        });
    }

    public IrisBiomePaletteLayer zero() {
        palette.clear();
        return this;
    }
}
