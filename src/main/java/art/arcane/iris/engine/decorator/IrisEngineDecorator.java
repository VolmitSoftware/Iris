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
package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.math.RNG;

/** A decorator of one kind of place, placing into the blocks of a {@link DecorationScene}. */
public abstract class IrisEngineDecorator {
    private static final long COLUMN_X_FACTOR = 29356788L;
    private static final long COLUMN_Z_FACTOR = 10439677L;
    private static final int X_PART_OFFSET = 6;
    private static final int Z_PART_OFFSET = 1;

    private final DecorationPart part;
    protected final DecorationScene scene;
    protected final BiomeDecorators decorators;

    protected IrisEngineDecorator(DecorationScene scene, DecorationPart part) {
        this.scene = scene;
        this.part = part;
        this.decorators = scene.decorators();
    }

    public DecorationPart getPart() {
        return part;
    }

    /**
     * Decorates one column. {@code x} and {@code z} are the column in the chunk, {@code realX} and {@code realZ} in the world,
     * with the world columns next to it on either side; {@code height} is the Y to decorate from and {@code max} how much room
     * there is.
     */
    public abstract void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                                  DecoratedBiome biome, int height, int max);

    /** {@link #decorate} for a decorator that looks at no neighbouring column. */
    public void decorate(int x, int z, int realX, int realZ, DecoratedBiome biome, int height, int max) {
        decorate(x, z, realX, realX, realX, realZ, realZ, realZ, biome, height, max);
    }

    protected RNG getRNG(int x, int z) {
        long seed = DecoratorCore.partSeed(scene.seed(), part);
        long modX = COLUMN_X_FACTOR ^ (part.ordinal() + X_PART_OFFSET);
        long modZ = COLUMN_Z_FACTOR ^ (part.ordinal() + Z_PART_OFFSET);
        return new RNG(x * modX + z * modZ + seed);
    }
}
