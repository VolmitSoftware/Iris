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

package art.arcane.iris.engine.object.terrain;

import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.placement.IrisSlopeClip;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.DependsOn;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;

@Snippet("biome-palette")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer {
    private final transient AtomicCache<KList<PlatformBlockState>> blockData = new AtomicCache<>();
    private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
    @Desc("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(0)
    @MaxNumber(2032)

    @Desc("The min thickness of this layer")
    private int minHeight = 1;
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(1)
    @MaxNumber(2032)

    @Desc("The max thickness of this layer")
    private int maxHeight = 1;
    @Desc("If set, this layer only shows where the terrain slope is within these bounds; outside them the layer is skipped entirely. Thickness is not scaled by slope.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @MinNumber(0.0001)
    @Desc("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("GRASS_BLOCK"));

    public CNG getHeightGenerator(RNG rng, IrisData data) {
        CNG cached = heightGenerator.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return heightGenerator.aquire(() -> CNG.signature(rng.nextParallelRNG(minHeight * maxHeight + getBlockData(data).size())));
    }

    public PlatformBlockState get(RNG rng, double x, double y, double z, IrisData data) {
        return get(rng, 0, x, y, z, data);
    }

    /**
     * Resolves a block for this layer without allocating a child RNG per call. The child RNG is
     * only built inside the lazy layer generator initializer, using the exact same seed derivation
     * as {@code parent.nextParallelRNG(signature)} followed by the generator signature.
     */
    public PlatformBlockState get(RNG parent, int signature, double x, double y, double z, IrisData data) {
        KList<PlatformBlockState> localBlockData = getBlockData(data);

        if (localBlockData.isEmpty()) {
            return null;
        }

        if (localBlockData.size() == 1) {
            return localBlockData.get(0);
        }

        double localZoom = zoom;
        double scaledX = x / localZoom;
        double scaledY = y / localZoom;
        double scaledZ = z / localZoom;
        return getLayerGenerator(parent, signature, data).fit(localBlockData, scaledX, scaledY, scaledZ);
    }

    public CNG getLayerGenerator(RNG rng, IrisData data) {
        return getLayerGenerator(rng, 0, data);
    }

    public CNG getLayerGenerator(RNG parent, int signature, IrisData data) {
        CNG cached = layerGenerator.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return layerGenerator.aquire(() ->
        {
            RNG rngx = parent.nextParallelRNG(signature).nextParallelRNG(minHeight + maxHeight + getBlockData(data).size());
            return style.create(rngx, data);
        });
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));

        return palette;
    }

    public KList<PlatformBlockState> getBlockData(IrisData data) {
        KList<PlatformBlockState> cached = blockData.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockData.aquire(() ->
        {
            KList<PlatformBlockState> blockData = new KList<>();
            for (IrisBlockData ix : palette) {
                PlatformBlockState bx = ix.getBlockData(data);
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
