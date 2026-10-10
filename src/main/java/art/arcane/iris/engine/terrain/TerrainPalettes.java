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

package art.arcane.iris.engine.terrain;

import art.arcane.iris.builtin.terrain.FluidPaletteSettings;
import art.arcane.iris.builtin.terrain.RockPaletteSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.PaletteSpec;
import com.volmit.noise.NoiseSampler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the terrain module keeps for one world about the palettes that fill its terrain: the rock below the biomes' layers
 * and the fluid of its oceans and aquifers, as a block for every column, and the rock of an upper dimension. They are the
 * dimension's {@link RockPaletteSettings} and {@link FluidPaletteSettings}.
 *
 * <p>The noise of a palette is seeded from the world's complex seed and a signature of its own, as the complex always did, so
 * a column's rock and fluid do not depend on who asks first.
 */
public final class TerrainPalettes {
    private static final int ROCK_SIGNATURE = 45;
    private static final int FLUID_SIGNATURE = 78;
    /** The salt Iris's material palettes always added to the seed of the noise that picks their blocks. */
    private static final int PALETTE_NOISE_SALT = -23_498_896;

    /** A block for every column. */
    @FunctionalInterface
    public interface ColumnBlocks {
        /** The block at the column, or null when the palette has no block. */
        PlatformBlockState at(double x, double z);
    }

    private final Engine engine;
    private final AtomicCache<ColumnBlocks> rock = new AtomicCache<>();
    private final AtomicCache<ColumnBlocks> fluid = new AtomicCache<>();
    private final Map<UpperDimensionContext, ColumnBlocks> upperRocks = new ConcurrentHashMap<>();

    TerrainPalettes(Engine engine) {
        this.engine = engine;
    }

    /** The palettes of the world {@code engine} generates now. */
    public static TerrainPalettes of(Engine engine) {
        return engine.getGeneration().state(TerrainPalettes.class, TerrainPalettes::new);
    }

    /** Builds the rock and fluid noise now instead of when first asked for. */
    public void warm() {
        rock();
        fluid();
    }

    /** The rock at the column, below the biome's layers. */
    public PlatformBlockState rockAt(double x, double z) {
        return rock().at(x, z);
    }

    /** The fluid at the column, above the terrain up to the fluid height. */
    public PlatformBlockState fluidAt(double x, double z) {
        return fluid().at(x, z);
    }

    /** The rock at the column of the upper dimension: the world's own when the upper dimension is the dimension itself. */
    public PlatformBlockState upperRockAt(UpperDimensionContext upper, double x, double z) {
        return upperRocks.computeIfAbsent(upper, this::upperRock).at(x, z);
    }

    private ColumnBlocks rock() {
        return rock.aquireOrThrow(() -> columns(rockPalette(engine.getDimension()), complexRng().nextParallelRNG(ROCK_SIGNATURE), engine.getData()));
    }

    private ColumnBlocks fluid() {
        return fluid.aquireOrThrow(() -> columns(fluidPalette(engine.getDimension()), complexRng().nextParallelRNG(FLUID_SIGNATURE), engine.getData()));
    }

    private RNG complexRng() {
        return new RNG(engine.getSeedManager().getComplex());
    }

    private ColumnBlocks upperRock(UpperDimensionContext upper) {
        if (upper.isSelfReferencing()) {
            return rock();
        }

        long seedOffset = upper.getDimension().getLoadKey().hashCode();
        RNG rng = new RNG(engine.getSeedManager().getComplex() ^ seedOffset);
        return columns(rockPalette(upper.getDimension()), rng.nextParallelRNG(ROCK_SIGNATURE), upper.getData());
    }

    private static PaletteSpec rockPalette(IrisDimension dimension) {
        return dimension.getModuleSettings().find(RockPaletteSettings.class).orElseGet(RockPaletteSettings::new).palette();
    }

    private static PaletteSpec fluidPalette(IrisDimension dimension) {
        return dimension.getModuleSettings().find(FluidPaletteSettings.class).orElseGet(FluidPaletteSettings::new).palette();
    }

    /** The palette's blocks over the columns, picked by its noise: the noise at a column, from 0 to 1, is how far down the weighted list the block is. */
    private static ColumnBlocks columns(PaletteSpec palette, RNG rng, IrisData data) {
        IrisPackResources resources = data.getPackResources();
        List<PlatformBlockState> blocks = PaletteBlocks.weighted(resources.blocks(), palette.palette());
        if (blocks.isEmpty()) {
            return (x, z) -> null;
        }

        long seed = rng.getSeed() + (PALETTE_NOISE_SALT + blocks.size());
        NoiseSampler noise = resources.noise().sampler(palette.style(), seed);
        int last = blocks.size() - 1;
        return (x, z) -> blocks.get((int) Math.round(noise.sample(x, z) * last));
    }
}
