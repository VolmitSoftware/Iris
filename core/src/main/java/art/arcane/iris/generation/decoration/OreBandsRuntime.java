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
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;

/**
 * What one engine derives from an {@link IrisOreBands} for one seed: the chance field of every banded generator,
 * resolved once so placing ores block by block reads plain fields instead of looking each generator up.
 */
public final class OreBandsRuntime {
    private final IrisOreBands bands;
    private final PackRuntime runtime;
    private final IrisOreGenerator.OreChance[] chances;

    public OreBandsRuntime(IrisOreBands bands, long seed, PackRuntime runtime) {
        this.bands = bands;
        this.runtime = runtime;
        IrisOreGenerator[] generators = bands.generators();
        this.chances = new IrisOreGenerator.OreChance[generators.length];
        for (int index = 0; index < generators.length; index++) {
            chances[index] = runtime.oreChance(generators[index], seed);
        }
    }

    public IrisOreBands bands() {
        return bands;
    }

    public boolean hasOres() {
        return bands.hasOres();
    }

    public int lowestY() {
        return bands.lowestY();
    }

    public int highestY() {
        return bands.highestY();
    }

    public boolean contains(int y) {
        return bands.contains(y);
    }

    /** The ore placed at a Y inside {@link #contains(int)}, or null when no generator places one. */
    public NativeBlockState generate(int x, int y, int z, RNG rng) {
        return bands.generate(x, y, z, rng, chances, runtime);
    }
}
