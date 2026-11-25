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

package art.arcane.iris.generation.block;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

/**
 * Where a plant can stand. A platform that can ask the server's own placement rules binds them here; without one,
 * each block state answers from Iris's block table.
 */
public final class PlantPlacement {
    private static final String DEAD_BUSH = "minecraft:dead_bush";
    private static final String GRASS_BLOCK = "minecraft:grass_block";
    private static volatile ServerRule serverRule;

    private PlantPlacement() {
    }

    public interface ServerRule {
        boolean canPlaceOnto(NativeBlockState plant, NativeBlockState ground);
    }

    public static synchronized ServerRule bindServerRule(ServerRule rule) {
        ServerRule previous = serverRule;
        serverRule = rule;
        return previous;
    }

    public static synchronized void restoreServerRule(ServerRule previous) {
        serverRule = previous;
    }

    static boolean canPlaceOnto(NativeBlockState plant, NativeBlockState ground) {
        ServerRule rule = serverRule;
        if (rule == null) {
            return plant.canPlaceOnto(ground);
        }
        if (ground.isAir() || isDeadBushOnGrass(plant, ground)) {
            return false;
        }
        return rule.canPlaceOnto(plant, ground);
    }

    // Vanilla allows this, but packs mix dead bushes with other plants for their sandy patches, and blacklisting grass
    // on such a decorator would take the other plants off the grass too.
    private static boolean isDeadBushOnGrass(NativeBlockState plant, NativeBlockState ground) {
        return DEAD_BUSH.equals(plant.materialKey()) && GRASS_BLOCK.equals(ground.materialKey());
    }
}
