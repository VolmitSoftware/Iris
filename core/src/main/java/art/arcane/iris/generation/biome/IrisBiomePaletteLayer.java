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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisSlopeClip;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.DependsOn;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import lombok.experimental.Accessors;


@Snippet("biome-palette")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer {
    private final transient AtomicCache<KList<NativeBlockState>> blockData = new AtomicCache<>();
    @Description("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(0)
    @MaxNumber(2032)

    @Description("The min thickness of this layer")
    private int minHeight = 1;
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(1)
    @MaxNumber(2032)

    @Description("The max thickness of this layer")
    private int maxHeight = 1;
    @Description("If set, this layer only shows where the terrain slope is within these bounds; outside them the layer is skipped entirely. Thickness is not scaled by slope.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @MinNumber(0.0001)
    @Description("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("GRASS_BLOCK"));

    public CNG getHeightGenerator(RNG rng, PackRuntime runtime) {
        long generatorSeed = rng.getSeed() + minHeight * maxHeight + getBlockData(runtime.data()).size();
        return runtime.paletteLayer(this).heightGenerator(generatorSeed);
    }

    static CNG createHeightGenerator(long generatorSeed) {
        return CNG.signature(new RNG(generatorSeed));
    }

    public NativeBlockState get(RNG rng, double x, double y, double z, PackRuntime runtime) {
        return get(rng, 0, x, y, z, runtime);
    }

    /**
     * Resolves a block for this layer without allocating a child RNG per call. The child RNG is
     * only built inside the lazy layer generator initializer, using the exact same seed derivation
     * as {@code parent.nextParallelRNG(signature)} followed by the generator signature.
     */
    public NativeBlockState get(RNG parent, int signature, double x, double y, double z, PackRuntime runtime) {
        KList<NativeBlockState> localBlockData = getBlockData(runtime.data());
        if (localBlockData.size() <= 1) {
            return localBlockData.isEmpty() ? null : localBlockData.get(0);
        }
        return fit(localBlockData, runtime.paletteLayer(this), parent, signature, x, y, z);
    }

    /** {@link #get(RNG, int, double, double, double, PackRuntime)} for a caller that already holds this layer's runtime. */
    public NativeBlockState get(RNG parent, int signature, double x, double y, double z, PaletteLayerRuntime layerRuntime) {
        KList<NativeBlockState> localBlockData = getBlockData(layerRuntime.packRuntime().data());
        if (localBlockData.size() <= 1) {
            return localBlockData.isEmpty() ? null : localBlockData.get(0);
        }
        return fit(localBlockData, layerRuntime, parent, signature, x, y, z);
    }

    private NativeBlockState fit(KList<NativeBlockState> localBlockData, PaletteLayerRuntime layerRuntime,
                                 RNG parent, int signature, double x, double y, double z) {
        double localZoom = zoom;
        CNG generator = layerRuntime.layerGenerator(layerGeneratorSeed(parent, signature, localBlockData.size()));
        return generator.fit(localBlockData, x / localZoom, y / localZoom, z / localZoom);
    }

    public CNG getLayerGenerator(RNG rng, PackRuntime runtime) {
        return getLayerGenerator(rng, 0, runtime);
    }

    public CNG getLayerGenerator(RNG parent, int signature, PackRuntime runtime) {
        long generatorSeed = layerGeneratorSeed(parent, signature, getBlockData(runtime.data()).size());
        return runtime.paletteLayer(this).layerGenerator(generatorSeed);
    }

    private long layerGeneratorSeed(RNG parent, int signature, int blockCount) {
        return parent.getSeed() + signature + minHeight + maxHeight + blockCount;
    }

    CNG createLayerGenerator(long generatorSeed, PackRuntime runtime) {
        return style.createNoCache(new RNG(generatorSeed), runtime);
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));

        return palette;
    }

    public KList<NativeBlockState> getBlockData(IrisData data) {
        KList<NativeBlockState> cached = blockData.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockData.aquire(() ->
        {
            KList<NativeBlockState> blockData = new KList<>();
            for (IrisBlockData ix : palette) {
                NativeBlockState bx = ix.getBlockData(data);
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
