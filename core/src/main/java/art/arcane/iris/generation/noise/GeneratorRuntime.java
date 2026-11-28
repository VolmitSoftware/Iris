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

package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.SeedTable;
import art.arcane.volmlib.util.noise.CellGenerator;

/**
 * What one engine derives from an {@link IrisGenerator}: the lattice of smoothed surface heights it has sampled and
 * the cell generators of its crack seeds.
 */
public final class GeneratorRuntime {
    private static final int CELL_SEED_CAPACITY = 8;

    private final IrisGenerator.SurfaceCacheStripe[] surfaceStripes = IrisGenerator.createSurfaceStripes();
    private final SeedTable<CellGenerator> cellGenerators;

    public GeneratorRuntime(IrisGenerator generator) {
        this.cellGenerators = new SeedTable<>(CELL_SEED_CAPACITY, generator::buildCellGenerator);
    }

    IrisGenerator.SurfaceCacheStripe[] surfaceStripes() {
        return surfaceStripes;
    }

    public CellGenerator cellGenerator(long seed) {
        return cellGenerators.get(seed);
    }
}
