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

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseStyle;

import java.util.List;
import java.util.function.Supplier;

/**
 * One {@link PaletteLayer} made ready for one world: its blocks, and the noise that decides how thick it is here, built when first
 * asked for. Which block it picks where is the world's {@link LayerBlocks}, shared with every other module that picks from the layer.
 *
 * <p>The thickness noise is built once, from the random of whoever asks first, as the layers of a biome always were.
 */
public final class ResolvedLayer {
    /** The style of the noise that sets a layer's thickness: Iris's signature noise. */
    private static final NoiseStyle THICKNESS_STYLE = NoiseStyle.of("IRIS");

    private final PaletteLayer settings;
    private final Supplier<LayerBlocks> layerBlocks;
    private final Supplier<NoiseFactory> noise;
    private final AtomicCache<List<BlockState>> blocks = new AtomicCache<>();
    private final AtomicCache<LayerNoise> heightNoise = new AtomicCache<>();

    private ResolvedLayer(PaletteLayer settings, Supplier<LayerBlocks> layerBlocks, Supplier<NoiseFactory> noise) {
        this.settings = settings;
        this.layerBlocks = layerBlocks;
        this.noise = noise;
    }

    /** {@code settings} ready for the world whose layer blocks {@code layerBlocks} gives, asked for when first needed. */
    static ResolvedLayer of(PaletteLayer settings, Supplier<LayerBlocks> layerBlocks, Supplier<NoiseFactory> noise) {
        return new ResolvedLayer(settings, layerBlocks, noise);
    }

    public PaletteLayer settings() {
        return settings;
    }

    public int minHeight() {
        return settings.minHeight();
    }

    public int maxHeight() {
        return settings.maxHeight();
    }

    public double zoom() {
        return settings.zoom();
    }

    public SlopeClip slopeCondition() {
        return settings.slopeCondition();
    }

    /** Whether the layer lists any block, which is not the same as picking one: a listed block may have no weight. */
    public boolean hasPalette() {
        return !settings.palette().isEmpty();
    }

    /** The noise that sets the layer's thickness, built from {@code rng} unless an earlier caller's is already kept. */
    public LayerNoise heightNoise(RNG rng) {
        LayerNoise cached = heightNoise.getIfPresent();
        if (cached != null) {
            return cached;
        }

        return heightNoise.aquire(() -> new LayerNoise(noise.get().sampler(THICKNESS_STYLE,
                rng.nextParallelRNG(settings.minHeight() * settings.maxHeight() + blocks().size()).getSeed())));
    }

    /** The layer's block at the position, picked as {@link LayerBlocks#pick} says, or null when the palette has no block. */
    public BlockState get(RNG parent, int signature, double x, double y, double z) {
        int index = layerBlocks.get().pick(settings, parent, signature, x, y, z);
        return index == LayerBlocks.NONE ? null : blocks().get(index);
    }

    /** {@link #get(RNG, int, double, double, double)} for a layer asked once per column, such as a slab or a wall. */
    public BlockState get(RNG rng, double x, double y, double z) {
        return get(rng, 0, x, y, z);
    }

    private List<BlockState> blocks() {
        List<BlockState> cached = blocks.getIfPresent();
        if (cached != null) {
            return cached;
        }

        return blocks.aquire(() -> layerBlocks.get().blocks(settings));
    }
}
