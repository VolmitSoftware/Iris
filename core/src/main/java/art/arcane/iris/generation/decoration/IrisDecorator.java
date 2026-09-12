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

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisSlopeClip;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
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
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;

@Snippet("decorator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A biome decorator is used for placing flowers, grass, cacti and so on")
@Data
public class IrisDecorator {
    private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> varianceGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
    private final transient AtomicCache<KList<PlatformBlockState>> blockData = new AtomicCache<>();
    private final transient AtomicCache<KList<PlatformBlockState>> blockDataTops = new AtomicCache<>();
    private final transient AtomicCache<PlatformBlockState[]> blockDataArray = new AtomicCache<>();
    private final transient AtomicCache<PlatformBlockState[]> blockDataTopsArray = new AtomicCache<>();
    private final transient AtomicCache<PlatformBlockState[]> whitelistArray = new AtomicCache<>();
    private final transient AtomicCache<PlatformBlockState[]> blacklistArray = new AtomicCache<>();
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

    public int getHeight(RNG rng, double x, double z, IrisData data) {
        if (stackMin == stackMax) {
            return stackMin;
        }

        return getHeightGenerator(rng, data)
                .fit(stackMin, stackMax,
                        x / heightVariance.getZoom(),
                        z / heightVariance.getZoom());
    }

    public CNG getHeightGenerator(RNG rng, IrisData data) {
        CNG cached = heightGenerator.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return heightGenerator.aquire(() ->
                heightVariance.create(rng.nextParallelRNG(getBlockData(data).size() + stackMax + stackMin), data));
    }

    public CNG getGenerator(RNG rng, IrisData data) {
        CNG cached = layerGenerator.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return layerGenerator.aquire(() -> style.create(rng.nextParallelRNG(getBlockData(data).size()), data));
    }

    public CNG getVarianceGenerator(RNG rng, IrisData data) {
        CNG cached = varianceGenerator.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return varianceGenerator.aquire(() ->
                variance.create(
                                rng.nextParallelRNG(getBlockData(data).size()), data)
                        .scale(1D / variance.getZoom()));
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));
        return palette;
    }

    public boolean passesChanceGate(RNG rng, double x, double z, IrisData data) {
        if (getBlockData(data).isEmpty()) {
            return false;
        }
        double xx = x / style.getZoom();
        double zz = z / style.getZoom();
        return getGenerator(rng, data).fitDouble(0D, 1D, xx, zz) <= chance;
    }

    public PlatformBlockState getBlockData(IrisBiome b, RNG rng, double x, double z, IrisData data) {
        if (!passesChanceGate(rng, x, z, data)) {
            return null;
        }
        if (getBlockData(data).size() == 1) {
            return getBlockData(data).get(0);
        }
        return getVarianceGenerator(rng, data).fit(getBlockData(data), z, x);
    }

    public PlatformBlockState getBlockData100(IrisBiome b, RNG rng, double x, double y, double z, IrisData data) {
        if (getBlockData(data).isEmpty()) {
            IrisLogging.warnOnce("decorator-empty:" + b.getName(), "Empty Block Data for " + b.getName());
            return null;
        }

        if (getBlockData(data).size() == 1) {
            return getBlockData(data).get(0);
        }

        return getVarianceGenerator(rng, data).fit(getBlockData(data), z, y, x); //X and Z must be switched
    }

    public PlatformBlockState getBlockDataForTop(IrisBiome b, RNG rng, double x, double y, double z, IrisData data) {
        if (getBlockDataTops(data).isEmpty()) {
            return getBlockData100(b, rng, x, y, z, data);
        }

        double xx = x / style.getZoom();
        double zz = z / style.getZoom();

        if (getGenerator(rng, data).fitDouble(0D, 1D, xx, zz) <= chance) { //Exclude y from here
            if (getBlockData(data).size() == 1) {
                return getBlockDataTops(data).get(0);
            }

            return getVarianceGenerator(rng, data).fit(getBlockDataTops(data), z, y, x); //X and Z must be switched
        }

        return null;
    }

    public KList<PlatformBlockState> getBlockData(IrisData data) {
        KList<PlatformBlockState> cached = blockData.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockData.aquire(() ->
        {
            KList<PlatformBlockState> blockData = new KList<>();
            for (IrisBlockData i : palette) {
                PlatformBlockState bx = i.getBlockData(data);
                if (bx != null) {
                    for (int n = 0; n < i.getWeight(); n++) {
                        blockData.add(bx);
                    }
                }
            }

            return blockData;
        });
    }

    public KList<PlatformBlockState> getBlockDataTops(IrisData data) {
        KList<PlatformBlockState> cached = blockDataTops.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockDataTops.aquire(() ->
        {
            KList<PlatformBlockState> blockDataTops = new KList<>();
            for (IrisBlockData i : topPalette) {
                PlatformBlockState bx = i.getBlockData(data);
                if (bx != null) {
                    for (int n = 0; n < i.getWeight(); n++) {
                        blockDataTops.add(bx);
                    }
                }
            }

            return blockDataTops;
        });
    }

    public PlatformBlockState[] getBlockDataArray(IrisData data) {
        PlatformBlockState[] cached = blockDataArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockDataArray.aquire(() -> {
            KList<PlatformBlockState> list = getBlockData(data);
            return list.toArray(new PlatformBlockState[0]);
        });
    }

    public PlatformBlockState[] getBlockDataTopsArray(IrisData data) {
        PlatformBlockState[] cached = blockDataTopsArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockDataTopsArray.aquire(() -> {
            KList<PlatformBlockState> list = getBlockDataTops(data);
            return list.toArray(new PlatformBlockState[0]);
        });
    }

    /**
     * The resolved whitelist palette. Empty when no whitelist is configured; callers must still gate on
     * {@link #getWhitelist()} being non null, because an explicitly empty whitelist blocks all placement.
     */
    public PlatformBlockState[] getWhitelistArray(IrisData data) {
        PlatformBlockState[] cached = whitelistArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return whitelistArray.aquire(() -> resolvePalette(whitelist, data));
    }

    public PlatformBlockState[] getBlacklistArray(IrisData data) {
        PlatformBlockState[] cached = blacklistArray.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blacklistArray.aquire(() -> resolvePalette(blacklist, data));
    }

    private static PlatformBlockState[] resolvePalette(KList<IrisBlockData> list, IrisData data) {
        if (list == null) {
            return new PlatformBlockState[0];
        }

        PlatformBlockState[] resolved = new PlatformBlockState[list.size()];

        for (int i = 0; i < resolved.length; i++) {
            resolved[i] = list.get(i).getBlockData(data);
        }

        return resolved;
    }

    public PlatformBlockState pickBlockData(RNG rng, IrisData data, double x, double z) {
        PlatformBlockState[] arr = getBlockDataArray(data);
        if (arr.length == 0) {
            return null;
        }
        if (arr.length == 1) {
            return arr[0];
        }
        return arr[Math.abs((int) getVarianceGenerator(rng, data).fit(0, arr.length - 1, z, x))];
    }

    public PlatformBlockState pickBlockDataTop(RNG rng, IrisData data, double x, double z) {
        PlatformBlockState[] arr = getBlockDataTopsArray(data);
        if (arr.length == 0) {
            return pickBlockData(rng, data, x, z);
        }
        if (arr.length == 1) {
            return arr[0];
        }
        return arr[Math.abs((int) getVarianceGenerator(rng, data).fit(0, arr.length - 1, z, x))];
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isStacking() {
        return getStackMax() > 1;
    }
}
