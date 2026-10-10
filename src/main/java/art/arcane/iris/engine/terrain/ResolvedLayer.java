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

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;

import java.util.List;

/**
 * One {@link PaletteLayer} made ready for one pack: its palette resolved to the platform's blocks, and the noise that
 * decides how thick it is here and which block of the palette it picks, built when first asked for. What the layer
 * describes stays in the settings; everything that needs the platform, the pack or a seed is here.
 *
 * <p>Both noises are built once, from the random of whoever asks first, as the layers of a biome always were: a layer's
 * noise is a function of that random, the signature it was first asked with and the layer's own size.
 */
public final class ResolvedLayer {
    private final PaletteLayer settings;
    private final IrisPackResources resources;
    private final AtomicCache<List<PlatformBlockState>> blocks = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> blockNoise = new AtomicCache<>();
    private final AtomicCache<CNG> heightNoise = new AtomicCache<>();

    private ResolvedLayer(PaletteLayer settings, IrisPackResources resources) {
        this.settings = settings;
        this.resources = resources;
    }

    /** {@code settings} ready for the blocks and noise of the pack of {@code data}. */
    static ResolvedLayer of(PaletteLayer settings, IrisData data) {
        return new ResolvedLayer(settings, data.getPackResources());
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
    public CNG heightNoise(RNG rng) {
        CNG cached = heightNoise.getIfPresent();
        if (cached != null) {
            return cached;
        }

        return heightNoise.aquire(() -> CNG.signature(rng.nextParallelRNG(settings.minHeight() * settings.maxHeight() + blocks().size())));
    }

    /** The layer's block at the position, picked with the noise seeded from {@code parent} and {@code signature}, or null when the palette has no block. */
    public PlatformBlockState get(RNG parent, int signature, double x, double y, double z) {
        List<PlatformBlockState> palette = blocks();
        if (palette.isEmpty()) {
            return null;
        }
        if (palette.size() == 1) {
            return palette.get(0);
        }

        double zoom = settings.zoom();
        double noise = blockNoise(parent, signature, palette.size()).sample(x / zoom, y / zoom, z / zoom);
        try {
            return palette.get((int) Math.round(noise * (palette.size() - 1)));
        } catch (IndexOutOfBoundsException outOfRange) {
            IrisLogging.reportError(outOfRange);
            return palette.get(0);
        }
    }

    /** {@link #get(RNG, int, double, double, double)} for a layer asked once per column, such as a slab or a wall. */
    public PlatformBlockState get(RNG rng, double x, double y, double z) {
        return get(rng, 0, x, y, z);
    }

    private List<PlatformBlockState> blocks() {
        List<PlatformBlockState> cached = blocks.getIfPresent();
        if (cached != null) {
            return cached;
        }

        return blocks.aquire(() -> PaletteBlocks.weighted(resources.blocks(), settings.palette()));
    }

    private NoiseSampler blockNoise(RNG parent, int signature, int paletteSize) {
        NoiseSampler cached = blockNoise.getIfPresent();
        if (cached != null) {
            return cached;
        }

        NoiseFactory noise = resources.noise();
        long seed = parent.nextParallelRNG(signature).nextParallelRNG(settings.minHeight() + settings.maxHeight() + paletteSize).getSeed();
        return blockNoise.aquireOrThrow(() -> noise.sampler(settings.style(), seed));
    }
}
