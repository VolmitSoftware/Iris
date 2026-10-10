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

package art.arcane.iris.builtin.terrain;

import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the blocks of the biomes' palette layers ({@link PaletteLayer}) are in one world, and which of them a layer picks
 * where. Terrain, caves and post processing all pick from the same layers (a biome's wall is painted in caves and on
 * cliffs), and a layer's block noise is built once per world, from the random of whichever of them asks first, as layers
 * always were. So the picks live here, in the world's state under this class, which the compiled-in built-ins and the
 * built-ins jar both see: one place, one noise per layer, whichever module asks.
 */
public final class LayerBlocks {
    /** A layer without blocks picks none. */
    public static final int NONE = -1;

    private final PackResources resources;
    private final Map<PaletteLayer, Picks> picks = new ConcurrentHashMap<>();

    private LayerBlocks(PackResources resources) {
        this.resources = resources;
    }

    /** The layer blocks of {@code world}. */
    public static LayerBlocks of(PackWorld world) {
        return world.state(LayerBlocks.class, created -> new LayerBlocks(created.resources()));
    }

    /** The layer's blocks resolved for the world's pack, each repeated by its weight: what an index from {@link #pick} names. */
    public List<BlockState> blocks(PaletteLayer layer) {
        return picksOf(layer).blocks();
    }

    /**
     * The index into {@link #blocks(PaletteLayer)} the layer picks at the position, or {@link #NONE} when it has no block.
     * The noise is seeded from {@code parent} and {@code signature} the first time anyone asks about this layer; a pick
     * outside the palette falls back to its first block.
     */
    public int pick(PaletteLayer layer, RNG parent, int signature, double x, double y, double z) {
        Picks layerPicks = picksOf(layer);
        int size = layerPicks.blocks().size();
        if (size <= 1) {
            return size - 1;
        }
        double zoom = layer.zoom();
        double noise = layerPicks.noise(parent, signature).sample(x / zoom, y / zoom, z / zoom);
        int index = (int) Math.round(noise * (size - 1));
        return index < 0 || index >= size ? 0 : index;
    }

    private Picks picksOf(PaletteLayer layer) {
        Picks known = picks.get(layer);
        if (known != null) {
            return known;
        }
        return picks.computeIfAbsent(layer, declared -> new Picks(declared, weighted(resources.blocks(), declared.palette()), resources));
    }

    private static List<BlockState> weighted(BlockRegistry registry, List<BlockSpec> palette) {
        List<BlockState> weighted = new ArrayList<>();
        for (BlockSpec spec : palette) {
            BlockState block = registry.resolve(spec);
            for (int n = 0; n < spec.weight(); n++) {
                weighted.add(block);
            }
        }
        return List.copyOf(weighted);
    }

    /** One layer's blocks and its block noise, built by the first caller. */
    private static final class Picks {
        private final PaletteLayer layer;
        private final List<BlockState> blocks;
        private final PackResources resources;
        private volatile NoiseSampler noise;

        Picks(PaletteLayer layer, List<BlockState> blocks, PackResources resources) {
            this.layer = layer;
            this.blocks = blocks;
            this.resources = resources;
        }

        List<BlockState> blocks() {
            return blocks;
        }

        NoiseSampler noise(RNG parent, int signature) {
            NoiseSampler built = noise;
            if (built != null) {
                return built;
            }
            synchronized (this) {
                if (noise == null) {
                    long seed = parent.nextParallelRNG(signature).nextParallelRNG(layer.minHeight() + layer.maxHeight() + blocks.size()).getSeed();
                    noise = resources.noise().sampler(layer.style(), seed);
                }
                return noise;
            }
        }
    }
}
