/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.object;

import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A palette of materials")
@Data
public class IrisMaterialPalette {

    @Desc("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();

    @MinNumber(0.0001)
    @Desc("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;

    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("STONE"));

    private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
    private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();

    public BlockData get(RNG rng, double x, double y, double z, IrisDataManager rdata) {
        if (getBlockData(rdata).isEmpty()) {
            return null;
        }

        if (getBlockData(rdata).size() == 1) {
            return getBlockData(rdata).get(0);
        }

        return getLayerGenerator(rng, rdata).fit(getBlockData(rdata), x / zoom, y / zoom, z / zoom);
    }

    public CNG getLayerGenerator(RNG rng, IrisDataManager rdata) {
        return layerGenerator.aquire(() ->
        {
            RNG rngx = rng.nextParallelRNG(-23498896 + getBlockData(rdata).size());
            return style.create(rngx);
        });
    }

    public IrisMaterialPalette qclear() {
        palette.clear();
        return this;
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));

        return palette;
    }

    public IrisMaterialPalette qadd(String b) {
        palette.add(new IrisBlockData(b));

        return this;
    }

    public KList<BlockData> getBlockData(IrisDataManager rdata) {
        return blockData.aquire(() ->
        {
            KList<BlockData> blockData = new KList<>();
            for (IrisBlockData ix : palette) {
                BlockData bx = ix.getBlockData(rdata);
                if (bx != null) {
                    for (int i = 0; i < ix.getWeight(); i++) {
                        blockData.add(bx);
                    }
                }
            }

            return blockData;
        });
    }

    public IrisMaterialPalette zero() {
        palette.clear();
        return this;
    }
}
