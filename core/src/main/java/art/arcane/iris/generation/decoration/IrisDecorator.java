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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
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
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import lombok.experimental.Accessors;

@Snippet("decorator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A biome decorator is used for placing flowers, grass, cacti and so on")
@Data
public class IrisDecorator {
    private final transient AtomicCache<KList<NativeBlockState>> blockData = new AtomicCache<>();
    private final transient AtomicCache<KList<NativeBlockState>> blockDataTops = new AtomicCache<>();
    private final transient AtomicCache<NativeBlockState[]> blockDataArray = new AtomicCache<>();
    private final transient AtomicCache<NativeBlockState[]> blockDataTopsArray = new AtomicCache<>();
    private final transient AtomicCache<NativeBlockState[]> whitelistArray = new AtomicCache<>();
    private final transient AtomicCache<NativeBlockState[]> blacklistArray = new AtomicCache<>();
    @Description("The varience dispersion is used when multiple blocks are put in the palette. Scatter scrambles them, Wispy shows streak-looking varience")
    private IrisGeneratorStyle variance = NoiseStyle.STATIC.style();
    @Description("Forcefully place this decorant anywhere it is supposed to go even if it should not go on a specific surface block. For example, you could force tallgrass to place on top of stone by using this.")
    private boolean forcePlace = false;
    @Description("Forced the surface block of this decorant to be the specified block. Assumes forcePlace.")
    private IrisBlockData forceBlock;
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("When set, the decorator can only place onto any of these blocks.")
    private KList<IrisBlockData> whitelist;
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("When set, the decorator will never place onto any of these blocks.")
    private KList<IrisBlockData> blacklist;
    @Description("The slope at which this decorator can be placed. Range from 0 to 10 by default. Calculated from a 3-block radius from the center of the decorator placement.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @DependsOn({"scaleStack", "stackMin", "stackMax"})
    @Description("If stackMax is set to true, use this to limit its max height for large caverns")
    private int absoluteMaxStack = 30;
    @Description("Dispersion is used to pick places to spawn. Scatter randomly places them (vanilla) or Wispy for a streak like patch system.")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @DependsOn({"stackMin", "stackMax"})
    @Description("If this decorator has a height more than 1 this changes how it picks the height between your maxes. Scatter = random, Wispy = wavy heights")
    private IrisGeneratorStyle heightVariance = NoiseStyle.STATIC.style();
    @Description("Tells iris where this decoration is a part of. I.e. SHORE_LINE or SEA_SURFACE")
    private IrisDecorationPart partOf = IrisDecorationPart.NONE;
    @DependsOn({"stackMin", "stackMax"})
    @MinNumber(1)
    @MaxNumber(2032)
    @Description("The minimum repeat stack height (setting to 3 would stack 3 of <block> on top of each other")
    private int stackMin = 1;
    @DependsOn({"stackMin", "stackMax"})
    @MinNumber(1)
    @MaxNumber(2032)
    @Description("The maximum repeat stack height")
    private int stackMax = 1;
    @DependsOn({"stackMin", "stackMax"})
    @Description("""
            Changes stackMin and stackMin from being absolute block heights and instead uses them as a percentage to scale the stack based on the cave height

            Within a cave, setting them stackMin/max to 50 would make the stack 50% of the cave height""")
    private boolean scaleStack = false;
    @Required
    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance for this decorator to decorate at a given X,Y coordinate. This is hit 256 times per chunk (per surface block)")
    private double chance = 0.1;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The palette of blocks to pick from when this decorator needs to place.")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("grass"));
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The palette of blocks used at the very top of a 'stackMax' of higher than 1. For example, bamboo tops.")
    private KList<IrisBlockData> topPalette = new KList<>();
    @DependsOn("topPalette")
    @MinNumber(0.01)
    @MaxNumber(1.0)
    @Description("When the stack passes the top threshold, the top palette will start being used instead of the normal palette.")
    private double topThreshold = 1.0;

    public int getHeight(RNG rng, double x, double z, PackRuntime runtime) {
        if (stackMin == stackMax) {
            return stackMin;
        }

        return getHeightGenerator(rng, runtime)
                .fit(stackMin, stackMax,
                        x / heightVariance.getZoom(),
                        z / heightVariance.getZoom());
    }

    public CNG getHeightGenerator(RNG rng, PackRuntime runtime) {
        return runtime.decorator(this).heightGenerator(rng.getSeed());
    }

    public CNG getGenerator(RNG rng, PackRuntime runtime) {
        return runtime.decorator(this).layerGenerator(rng.getSeed());
    }

    public CNG getVarianceGenerator(RNG rng, PackRuntime runtime) {
        return runtime.decorator(this).varianceGenerator(rng.getSeed());
    }

    public CNG createHeightGenerator(long seed, PackRuntime runtime) {
        return heightVariance.create(new RNG(seed).nextParallelRNG(getBlockData(runtime.data()).size() + stackMax + stackMin), runtime);
    }

    public CNG createLayerGenerator(long seed, PackRuntime runtime) {
        return style.create(new RNG(seed).nextParallelRNG(getBlockData(runtime.data()).size()), runtime);
    }

    public CNG createVarianceGenerator(long seed, PackRuntime runtime) {
        return variance.createNoCache(deriveVarianceSeed(new RNG(seed), runtime.data()), runtime).scale(1D / variance.getZoom());
    }

    RNG deriveVarianceSeed(RNG rng, IrisData data) {
        return rng.nextParallelRNG(getBlockData(data).size());
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));
        return palette;
    }

    public boolean passesChanceGate(RNG rng, double x, double z, PackRuntime runtime) {
        return passesChanceGate(rng, x, z, runtime.decorator(this));
    }

    /** {@link #passesChanceGate(RNG, double, double, PackRuntime)} for a caller that already holds this decorator's runtime. */
    public boolean passesChanceGate(RNG rng, double x, double z, DecoratorRuntime decoratorRuntime) {
        if (getBlockData(decoratorRuntime.packRuntime().data()).isEmpty()) {
            return false;
        }
        double blocksPerSample = chanceBlocksPerSample();
        double xx = x / blocksPerSample;
        double zz = z / blocksPerSample;
        return decoratorRuntime.layerGenerator(rng.getSeed()).fitDouble(0D, 1D, xx, zz) <= chance;
    }

    /** A style without a seed divides by its zoom here on top of its own scale; a seeded one is read in plain blocks. */
    private double chanceBlocksPerSample() {
        return style.isSeeded() ? 1 : style.getZoom();
    }

    public NativeBlockState getBlockData(IrisBiome b, RNG rng, double x, double z, PackRuntime runtime) {
        if (!passesChanceGate(rng, x, z, runtime)) {
            return null;
        }
        if (getBlockData(runtime.data()).size() == 1) {
            return getBlockData(runtime.data()).get(0);
        }
        return getVarianceGenerator(rng, runtime).fit(getBlockData(runtime.data()), z, x);
    }

    public NativeBlockState getBlockData100(IrisBiome b, RNG rng, double x, double y, double z, PackRuntime runtime) {
        KList<NativeBlockState> palette = getBlockData(runtime.data());
        if (palette.isEmpty()) {
            IrisLogging.warnOnce("decorator-empty:" + b.getName(), "Empty Block Data for " + b.getName());
            return null;
        }

        if (palette.size() == 1) {
            return palette.get(0);
        }

        return getVarianceGenerator(rng, runtime).fit(palette, z, y, x); //X and Z must be switched
    }

    public NativeBlockState getBlockDataForTop(IrisBiome b, RNG rng, double x, double y, double z, PackRuntime runtime) {
        KList<NativeBlockState> topPalette = getBlockDataTops(runtime.data());
        if (topPalette.isEmpty()) {
            return getBlockData100(b, rng, x, y, z, runtime);
        }

        double blocksPerSample = chanceBlocksPerSample();
        double xx = x / blocksPerSample;
        double zz = z / blocksPerSample;

        if (getGenerator(rng, runtime).fitDouble(0D, 1D, xx, zz) <= chance) { //Exclude y from here
            if (topPalette.size() == 1) {
                return topPalette.get(0);
            }

            return getVarianceGenerator(rng, runtime).fit(topPalette, z, y, x); //X and Z must be switched
        }

        return null;
    }

    public KList<NativeBlockState> getBlockData(IrisData data) {
        KList<NativeBlockState> cached = blockData.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockData.aquire(() ->
        {
            KList<NativeBlockState> blockData = new KList<>();
            for (IrisBlockData i : palette) {
                NativeBlockState bx = i.getBlockData(data);
                if (bx != null) {
                    for (int n = 0; n < i.getWeight(); n++) {
                        blockData.add(bx);
                    }
                }
            }

            return blockData;
        });
    }

    public KList<NativeBlockState> getBlockDataTops(IrisData data) {
        KList<NativeBlockState> cached = blockDataTops.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockDataTops.aquire(() ->
        {
            KList<NativeBlockState> blockDataTops = new KList<>();
            for (IrisBlockData i : topPalette) {
                NativeBlockState bx = i.getBlockData(data);
                if (bx != null) {
                    for (int n = 0; n < i.getWeight(); n++) {
                        blockDataTops.add(bx);
                    }
                }
            }

            return blockDataTops;
        });
    }

    public NativeBlockState[] getBlockDataArray(IrisData data) {
        NativeBlockState[] cached = blockDataArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockDataArray.aquire(() -> {
            KList<NativeBlockState> list = getBlockData(data);
            return list.toArray(new NativeBlockState[0]);
        });
    }

    public NativeBlockState[] getBlockDataTopsArray(IrisData data) {
        NativeBlockState[] cached = blockDataTopsArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockDataTopsArray.aquire(() -> {
            KList<NativeBlockState> list = getBlockDataTops(data);
            return list.toArray(new NativeBlockState[0]);
        });
    }

    /**
     * The resolved whitelist palette. Empty when no whitelist is configured; callers must still gate on
     * {@link #getWhitelist()} being non null, because an explicitly empty whitelist blocks all placement.
     */
    public NativeBlockState[] getWhitelistArray(IrisData data) {
        NativeBlockState[] cached = whitelistArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return whitelistArray.aquire(() -> resolvePalette(whitelist, data));
    }

    public NativeBlockState[] getBlacklistArray(IrisData data) {
        NativeBlockState[] cached = blacklistArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blacklistArray.aquire(() -> resolvePalette(blacklist, data));
    }

    private static NativeBlockState[] resolvePalette(KList<IrisBlockData> list, IrisData data) {
        if (list == null) {
            return new NativeBlockState[0];
        }

        NativeBlockState[] resolved = new NativeBlockState[list.size()];

        for (int i = 0; i < resolved.length; i++) {
            resolved[i] = list.get(i).getBlockData(data);
        }

        return resolved;
    }

    public NativeBlockState pickBlockData(RNG rng, PackRuntime runtime, double x, double z) {
        NativeBlockState[] arr = getBlockDataArray(runtime.data());
        if (arr.length == 0) {
            return null;
        }
        if (arr.length == 1) {
            return arr[0];
        }
        return arr[Math.abs((int) getVarianceGenerator(rng, runtime).fit(0, arr.length - 1, z, x))];
    }

    public NativeBlockState pickBlockDataTop(RNG rng, PackRuntime runtime, double x, double z) {
        NativeBlockState[] arr = getBlockDataTopsArray(runtime.data());
        if (arr.length == 0) {
            return pickBlockData(rng, runtime, x, z);
        }
        if (arr.length == 1) {
            return arr[0];
        }
        return arr[Math.abs((int) getVarianceGenerator(rng, runtime).fit(0, arr.length - 1, z, x))];
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isStacking() {
        return getStackMax() > 1;
    }
}
