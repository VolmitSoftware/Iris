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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.PendingSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.terrain.IrisBiomePaletteLayer;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;

import java.util.Optional;

/**
 * Transitional: what {@link PendingSettings} reads from Iris's dimension and biomes for one world, until those settings
 * are module settings. A palette layer picks with the world's post-processing random, as the post-processing worker did.
 */
final class IrisPendingSettings implements PendingSettings {
    private final Engine engine;
    private volatile RNG postRandom;

    IrisPendingSettings(Engine engine) {
        this.engine = engine;
    }

    @Override
    public boolean hidesOres() {
        return engine.getDimension().isHideOresForHiddenOre();
    }

    @Override
    public boolean postProcessingWalls() {
        return engine.getDimension().isPostProcessingWalls();
    }

    @Override
    public boolean postProcessingSlabs() {
        return engine.getDimension().isPostProcessingSlabs();
    }

    @Override
    public Optional<BiomeLayer> wall(int x, int z) {
        IrisBiomePaletteLayer wall = engine.getSurfaceBiome(x, z).getWall();
        return wall.getPalette().isEmpty() ? Optional.empty() : Optional.of(new PaletteLayer(wall));
    }

    @Override
    public BiomeLayer slab(int x, int z) {
        return new PaletteLayer(engine.getSurfaceBiome(x, z).getSlab());
    }

    /** Made on first use, so a world that never picks from a layer never asks for the seed. */
    private RNG postRandom() {
        RNG random = postRandom;
        if (random != null) {
            return random;
        }
        synchronized (this) {
            if (postRandom == null) {
                postRandom = new RNG(engine.getSeedManager().getPost());
            }
            return postRandom;
        }
    }

    private final class PaletteLayer implements BiomeLayer {
        private final IrisBiomePaletteLayer layer;

        PaletteLayer(IrisBiomePaletteLayer layer) {
            this.layer = layer;
        }

        @Override
        public Optional<BlockState> at(double x, double y, double z) {
            PlatformBlockState picked = layer.get(postRandom(), x, y, z, engine.getData());
            return picked == null ? Optional.empty() : Optional.of(BlockState.parse(PlatformBlockStates.namespaced(picked.key())));
        }

        @Override
        public boolean allowsSlope(double slope) {
            return layer.getSlopeCondition().isValid(slope);
        }
    }
}
