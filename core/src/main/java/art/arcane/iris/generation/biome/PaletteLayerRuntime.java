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
import art.arcane.iris.generation.runtime.SeedTable;
import art.arcane.volmlib.util.noise.CNG;

/**
 * What one engine derives from an {@link IrisBiomePaletteLayer}: the block selection and depth generators of the
 * seeds that sample it.
 */
public final class PaletteLayerRuntime {
    private static final int LAYER_SEED_CAPACITY = 32;
    private static final int LAYER_RECENT_SLOTS = 32;
    private static final int HEIGHT_SEED_CAPACITY = 32;

    private final SeedTable<CNG> layerGenerators;
    private final SeedTable<CNG> heightGenerators;

    public PaletteLayerRuntime(IrisBiomePaletteLayer layer, PackRuntime runtime) {
        this.layerGenerators = new SeedTable<>(LAYER_SEED_CAPACITY, LAYER_RECENT_SLOTS,
                seed -> layer.createLayerGenerator(seed, runtime));
        this.heightGenerators = new SeedTable<>(HEIGHT_SEED_CAPACITY, IrisBiomePaletteLayer::createHeightGenerator);
    }

    public CNG layerGenerator(long seed) {
        return layerGenerators.get(seed);
    }

    public CNG heightGenerator(long seed) {
        return heightGenerators.get(seed);
    }
}
