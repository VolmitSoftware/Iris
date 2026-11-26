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

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.SeedTable;
import art.arcane.volmlib.util.noise.CNG;

/** What one engine derives from an {@link IrisDecorator}: the stack, placement and variance generators of each seed. */
public final class DecoratorRuntime {
    private static final int SEED_CAPACITY = 32;

    private final SeedTable<CNG> heightGenerators;
    private final SeedTable<CNG> layerGenerators;
    private final SeedTable<CNG> varianceGenerators;

    public DecoratorRuntime(IrisDecorator decorator, PackRuntime runtime) {
        this.heightGenerators = new SeedTable<>(SEED_CAPACITY, seed -> decorator.createHeightGenerator(seed, runtime));
        this.layerGenerators = new SeedTable<>(SEED_CAPACITY, seed -> decorator.createLayerGenerator(seed, runtime));
        this.varianceGenerators = new SeedTable<>(SEED_CAPACITY, seed -> decorator.createVarianceGenerator(seed, runtime));
    }

    public CNG heightGenerator(long seed) {
        return heightGenerators.get(seed);
    }

    public CNG layerGenerator(long seed) {
        return layerGenerators.get(seed);
    }

    public CNG varianceGenerator(long seed) {
        return varianceGenerators.get(seed);
    }
}
