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

import java.util.Optional;

@Snippet("palette")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A palette of materials")
@Data
public class IrisMaterialPalette {
    private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
    private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
    @Desc("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @MinNumber(0.0001)
    @Desc("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("STONE"));

    public BlockData get(RNG rng, double x, double y, double z, IrisData rdata) {
        if (getBlockData(rdata).isEmpty()) {
            return null;
        }

        if (getBlockData(rdata).size() == 1) {
            return getBlockData(rdata).get(0);
        }

        return getLayerGenerator(rng, rdata).fit(getBlockData(rdata), x / zoom, y / zoom, z / zoom);
    }

    public Optional<TileData<?>> getTile(RNG rng, double x, double y, double z, IrisData rdata) {
        if (getBlockData(rdata).isEmpty())
            return Optional.empty();

        TileData<?> tile = getBlockData(rdata).size() == 1 ? palette.get(0).tryGetTile() : palette.getRandom(rng).tryGetTile();
        return tile != null ? Optional.of(tile) : Optional.empty();
    }

    public CNG getLayerGenerator(RNG rng, IrisData rdata) {
        return layerGenerator.aquire(() ->
        {
            RNG rngx = rng.nextParallelRNG(-23498896 + getBlockData(rdata).size());
            return style.create(rngx, rdata);
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

    public KList<BlockData> getBlockData(IrisData rdata) {
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
