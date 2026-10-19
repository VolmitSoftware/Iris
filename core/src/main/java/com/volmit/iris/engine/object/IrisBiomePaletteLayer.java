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
import com.volmit.iris.util.interpolation.IrisInterpolation;
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
        KList<BlockData> blocks = getBlockData(data);
        if (blocks.isEmpty()) {
            return null;
        }

        if (blocks.size() == 1) {
            return blocks.get(0);
        }

        return blocks.get(slotForNoise(getLayerGenerator(rng, data).noise(x / zoom, y / zoom, z / zoom), blocks.size()));
    }

    public BlockData getAtDepth(RNG rng, double wx, double wz, int depth, IrisData data) {
        return get(rng, depthShiftedX(wx, depth), depth, depthShiftedZ(wz, depth), data);
    }

    public double getNoiseAtDepth(RNG rng, double wx, double wz, int depth, IrisData data) {
        return getLayerGenerator(rng, data).noise(depthShiftedX(wx, depth) / zoom, depth / zoom, depthShiftedZ(wz, depth) / zoom);
    }

    public static int slotForNoise(double noise, int slotCount) {
        int slot = (int) Math.round(IrisInterpolation.lerp(0, slotCount - 1, noise));
        return slot >= 0 && slot < slotCount ? slot : 0;
    }

    private double depthShiftedX(double wx, int depth) {
        return (wx + depth) / zoom;
    }

    private double depthShiftedZ(double wz, int depth) {
        return (wz - depth) / zoom;
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
            for (int entry : getPaletteEntryPerSlot(data)) {
                blockData.add(palette.get(entry).getBlockData(data));
            }

            return blockData;
        });
    }

    public KList<Integer> getPaletteEntryPerSlot(IrisData data) {
        return paletteEntryPerSlot(palette, data);
    }

    /**
     * Each block fills as many slots of the noise range as its weight; blocks that can't be found get none.
     */
    public static KList<Integer> paletteEntryPerSlot(KList<IrisBlockData> palette, IrisData data) {
        KList<Integer> entries = new KList<>();
        for (int entry = 0; entry < palette.size(); entry++) {
            IrisBlockData block = palette.get(entry);
            if (block.getBlockData(data) == null) {
                continue;
            }

            for (int i = 0; i < block.getWeight(); i++) {
                entries.add(entry);
            }
        }

        return entries;
    }

    public IrisBiomePaletteLayer zero() {
        palette.clear();
        return this;
    }
}
