/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * One {@link Decorator} made ready for one world: its palettes resolved to the platform's blocks, and the noise that
 * decides where it places, which block of the palette it picks and how tall it stacks, built when first asked for. What
 * the decorator describes stays in the settings; everything that needs the platform, the pack or the world seed is here.
 *
 * <p>The noise seeds are the decorator's own seed plus the same offsets Iris's decorator always used, so a decorator
 * noise is a pure function of its seed.
 */
final class ResolvedDecorator {
    private final Decorator settings;
    private final long seed;
    private final NoiseFactory noise;
    private final List<PlatformBlockState> blocks;
    private final List<PlatformBlockState> topBlocks;
    private final PlatformBlockState[] blockArray;
    private final PlatformBlockState[] topBlockArray;
    private final PlatformBlockState forceBlock;
    private final PlatformBlockState[] whitelist;
    private final PlatformBlockState[] blacklist;
    private final AtomicCache<NoiseSampler> chanceNoise = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> varianceNoise = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> heightNoise = new AtomicCache<>();
    private final ModuleLog log;

    /**
     * @param blocks the palette as the platform's blocks, each repeated by its weight
     * @param topBlocks the top palette the same way
     * @param forceBlock the block it forces onto the surface, or null
     * @param whitelist the blocks it may only be placed onto; ignored unless the decorator has a whitelist
     * @param blacklist the blocks it is never placed onto; ignored unless the decorator has a blacklist
     * @param log the log of the module that decorates
     */
    ResolvedDecorator(Decorator settings, long seed, NoiseFactory noise, List<PlatformBlockState> blocks, List<PlatformBlockState> topBlocks,
                      PlatformBlockState forceBlock, List<PlatformBlockState> whitelist, List<PlatformBlockState> blacklist,
                      ModuleLog log) {
        this.settings = settings;
        this.seed = seed;
        this.noise = noise;
        this.blocks = List.copyOf(blocks);
        this.topBlocks = List.copyOf(topBlocks);
        this.blockArray = blocks.toArray(new PlatformBlockState[0]);
        this.topBlockArray = topBlocks.toArray(new PlatformBlockState[0]);
        this.forceBlock = forceBlock;
        this.whitelist = whitelist.toArray(new PlatformBlockState[0]);
        this.blacklist = blacklist.toArray(new PlatformBlockState[0]);
        this.log = log;
    }

    /** {@code settings} ready for {@code engine}: its blocks resolved through the pack, its noise seeded from {@code seed}. */
    static ResolvedDecorator of(Decorator settings, Engine engine, long seed, ModuleLog log) {
        var resources = engine.getData().getPackResources();
        BlockRegistry registry = resources.blocks();
        PlatformBlockState forced = settings.forceBlock().map(spec -> platformState(registry, spec)).orElse(null);
        return new ResolvedDecorator(settings, seed, resources.noise(), weighted(registry, settings.palette()),
                weighted(registry, settings.topPalette()), forced, each(registry, settings.whitelist()), each(registry, settings.blacklist()), log);
    }

    private static PlatformBlockState platformState(BlockRegistry registry, BlockSpec spec) {
        return B.getState(registry.resolve(spec).key());
    }

    private static List<PlatformBlockState> weighted(BlockRegistry registry, List<BlockSpec> palette) {
        List<PlatformBlockState> weighted = new ArrayList<>();
        for (BlockSpec spec : palette) {
            PlatformBlockState state = platformState(registry, spec);
            if (state == null) {
                continue;
            }
            for (int n = 0; n < spec.weight(); n++) {
                weighted.add(state);
            }
        }
        return weighted;
    }

    private static List<PlatformBlockState> each(BlockRegistry registry, List<BlockSpec> palette) {
        return palette.stream().map(spec -> platformState(registry, spec)).toList();
    }

    Decorator settings() {
        return settings;
    }

    /** The seed the decorator's noise is built from. */
    long seed() {
        return seed;
    }

    DecorationPart part() {
        return settings.partOf();
    }

    boolean isStacking() {
        return settings.isStacking();
    }

    boolean isForcePlace() {
        return settings.forcePlace();
    }

    boolean isScaleStack() {
        return settings.scaleStack();
    }

    int absoluteMaxStack() {
        return settings.absoluteMaxStack();
    }

    double topThreshold() {
        return settings.topThreshold();
    }

    /** The block it forces onto the surface instead of the one found there, or null. */
    PlatformBlockState forceBlock() {
        return forceBlock;
    }

    /** Whether the terrain's slope keeps the decorator off; the slope is only worked out when the decorator limits it. */
    boolean slopeBlocks(DoubleSupplier slope) {
        if (settings.forcePlace() || settings.slopeCondition().isDefault()) {
            return false;
        }
        return !settings.slopeCondition().isValid(slope.getAsDouble());
    }

    /** Whether the white and black lists keep the decorator off {@code surface}. */
    boolean rejectsSurface(PlatformBlockState surface) {
        if (settings.hasWhitelist() && !matches(whitelist, surface)) {
            return true;
        }
        return settings.hasBlacklist() && matches(blacklist, surface);
    }

    private static boolean matches(PlatformBlockState[] palette, PlatformBlockState surface) {
        for (PlatformBlockState state : palette) {
            if (state.equals(surface)) {
                return true;
            }
        }
        return false;
    }

    /** How many blocks the decorator stacks at the column: the minimum, or a height between the limits drawn from its height noise. */
    int stackHeight(double x, double z) {
        if (settings.stackMin() == settings.stackMax()) {
            return settings.stackMin();
        }

        double zoom = settings.heightVariance().zoom();
        return fit(settings.stackMin(), settings.stackMax(), heightNoise().sample(x / zoom, z / zoom));
    }

    /** Whether the chance noise lets the decorator place at the column. */
    boolean passesChanceGate(double x, double z) {
        if (blocks.isEmpty()) {
            return false;
        }
        double zoom = settings.style().zoom();
        return chanceNoise().sample(x / zoom, z / zoom) <= settings.chance();
    }

    /** The block of the palette at the position, for decorations that fill a column block by block; null when the palette has no block. */
    PlatformBlockState blockAt(IrisBiome biome, double x, double y, double z) {
        if (blocks.isEmpty()) {
            log.warnOnce("decorator-empty:" + biome.getName(), "Empty Block Data for " + biome.getName());
            return null;
        }

        if (blocks.size() == 1) {
            return blocks.get(0);
        }

        return blocks.get(fit(0, blocks.size() - 1, varianceNoise().sample(z, y, x))); // X and Z are switched
    }

    /** The top palette's block at the position, or the palette's when there is no top palette; null where the chance noise declines. */
    PlatformBlockState topBlockAt(IrisBiome biome, double x, double y, double z) {
        if (topBlocks.isEmpty()) {
            return blockAt(biome, x, y, z);
        }

        double zoom = settings.style().zoom();
        if (!(chanceNoise().sample(x / zoom, z / zoom) <= settings.chance())) {
            return null;
        }

        if (blocks.size() == 1) {
            return topBlocks.get(0);
        }

        return topBlocks.get(fit(0, topBlocks.size() - 1, varianceNoise().sample(z, y, x)));
    }

    /** The palette's block for the column, or null when the palette has no block. */
    PlatformBlockState pickBlock(double x, double z) {
        return pick(blockArray, x, z);
    }

    /** The top palette's block for the column; the palette's when there is no top palette. */
    PlatformBlockState pickTopBlock(double x, double z) {
        if (topBlockArray.length == 0) {
            return pickBlock(x, z);
        }
        return pick(topBlockArray, x, z);
    }

    private PlatformBlockState pick(PlatformBlockState[] palette, double x, double z) {
        if (palette.length == 0) {
            return null;
        }
        if (palette.length == 1) {
            return palette[0];
        }
        return palette[Math.abs(fit(0, palette.length - 1, varianceNoise().sample(z, x)))];
    }

    /** Builds the noise now instead of when first asked for. */
    void warm() {
        heightNoise();
        chanceNoise();
        varianceNoise();
    }

    private NoiseSampler heightNoise() {
        return heightNoise.aquireOrThrow(() -> noise.sampler(settings.heightVariance(),
                seed + blocks.size() + settings.stackMax() + settings.stackMin()));
    }

    private NoiseSampler chanceNoise() {
        return chanceNoise.aquireOrThrow(() -> noise.sampler(settings.style(), seed + blocks.size()));
    }

    private NoiseSampler varianceNoise() {
        return varianceNoise.aquireOrThrow(() -> noise.sampler(settings.variance(), seed + blocks.size()));
    }

    /** The whole number between {@code min} and {@code max} that {@code fraction}, from 0 to 1, is the way along. */
    private static int fit(int min, int max, double fraction) {
        return (int) Math.round(min + (fraction * (max - min)));
    }
}
