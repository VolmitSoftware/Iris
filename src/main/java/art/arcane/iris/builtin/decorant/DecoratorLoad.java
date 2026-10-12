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
package art.arcane.iris.builtin.decorant;

/** How loaded with decorating a biome is, which studio's decorator load view colours it by. */
public final class DecoratorLoad {
    private static final int COLUMNS_PER_CHUNK = 256;

    private DecoratorLoad() {
    }

    /** The sum of the decorators' chances times their stack heights, at least one block each; 0 for a biome without decorators. */
    public static double of(DecorantSettings settings) {
        if (settings == null) {
            return 0D;
        }

        double load = 0D;
        for (Decorator decorator : settings.decorators()) {
            load += Math.max(decorator.stackMax(), 1) * decorator.chance();
        }
        return load;
    }

    /** The sum of the decorators' chances over a chunk's columns, which is how strongly the view colours the biome. */
    public static double colour(DecorantSettings settings) {
        if (settings == null) {
            return 0D;
        }

        double load = 0D;
        for (Decorator decorator : settings.decorators()) {
            load += decorator.chance() * Math.min(1, decorator.stackMax()) * COLUMNS_PER_CHUNK;
        }
        return load;
    }
}
