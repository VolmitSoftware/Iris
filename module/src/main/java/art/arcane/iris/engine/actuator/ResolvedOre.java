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
package art.arcane.iris.engine.actuator;

import art.arcane.iris.modules.terrain.OreGenerator;
import art.arcane.volmlib.util.cache.AtomicCache;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSampler;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@link OreGenerator} made ready for one world: its palette resolved to blocks and the noise that
 * decides where it places ore, built from the seed it is warmed with or, when nothing warmed it, from the terrain seed
 * of its first use. The palette picks with the terrain seed. What the generator describes stays in the settings;
 * everything that needs the platform, the pack or the world seed is here.
 */
final class ResolvedOre {
    private final OreGenerator settings;
    private final NoiseFactory noise;
    private final BlockRegistry blockRegistry;
    private final long terrainSeed;
    private final List<BlockState> blocks;
    private final AtomicCache<NoiseSampler> chance = new AtomicCache<>();
    private final AtomicCache<PaletteSampler> palette = new AtomicCache<>();

    /**
     * @param terrainSeed the seed the palette picks with, and the noise is built from when nothing warmed it first
     * @param blocks the palette as the platform's blocks, each repeated by its weight
     */
    ResolvedOre(OreGenerator settings, NoiseFactory noise, BlockRegistry blockRegistry, long terrainSeed, List<BlockState> blocks) {
        this.settings = settings;
        this.noise = noise;
        this.blockRegistry = blockRegistry;
        this.terrainSeed = terrainSeed;
        this.blocks = List.copyOf(blocks);
    }

    /** {@code settings} ready for a world with {@code resources}: its blocks resolved through the pack, its picks seeded with the terrain seed. */
    static ResolvedOre of(OreGenerator settings, PackResources resources, long terrainSeed) {
        BlockRegistry registry = resources.blocks();
        return new ResolvedOre(settings, resources.noise(), registry, terrainSeed, weighted(registry, settings.palette().palette()));
    }

    private static List<BlockState> weighted(BlockRegistry registry, List<BlockSpec> specs) {
        List<BlockState> weighted = new ArrayList<>();
        for (BlockSpec spec : specs) {
            BlockState state = registry.resolve(spec);
            for (int n = 0; n < spec.weight(); n++) {
                weighted.add(state);
            }
        }
        return weighted;
    }

    boolean generatesSurface() {
        return settings.generateSurface();
    }

    double minY() {
        return settings.range().min();
    }

    double maxY() {
        return settings.range().max();
    }

    /** Builds the chance noise from {@code seed}, unless it is built already: the first seed it is given is the one it keeps. */
    void warm(long seed) {
        chance.aquire(() -> noise.sampler(settings.chanceStyle(), seed));
    }

    /** The ore block at the position, or null when the position is out of the band, the chance noise is above the threshold or the palette has no block. */
    BlockState generate(int x, int y, int z) {
        if (!settings.range().contains(y)) {
            return null;
        }
        if (settings.palette().palette().isEmpty()) {
            return null;
        }
        if (chanceNoise().sample(x, y, z) > settings.threshold()) {
            return null;
        }
        return pick(x, y, z);
    }

    /** The chance noise, which this runs per block of the terrain, so a built one is read without creating a supplier. */
    private NoiseSampler chanceNoise() {
        NoiseSampler built = chance.getIfPresent();
        if (built != null) {
            return built;
        }
        return chance.aquire(() -> noise.sampler(settings.chanceStyle(), terrainSeed));
    }

    private BlockState pick(int x, int y, int z) {
        if (blocks.isEmpty()) {
            return null;
        }
        if (blocks.size() == 1) {
            return blocks.getFirst();
        }
        PaletteSampler sampler = palette.aquire(() -> blockRegistry.palette(settings.palette(), terrainSeed));
        return sampler.at(x, y, z);
    }
}
