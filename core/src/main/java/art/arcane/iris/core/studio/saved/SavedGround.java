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

package art.arcane.iris.core.studio.saved;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which saved blocks are the ground. The view shows ground without objects, so only whole opaque blocks count: plants,
 * fences, slabs and tree trunks are looked past, and water is looked through.
 */
@FunctionalInterface
interface SavedGround {
    Set<String> AIR_BLOCKS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    // Surfaces that aren't whole opaque blocks but read as the ground they lie on, when they lie on ground: snow on a
    // treetop or ice on a lake is looked past.
    Set<String> THIN_GROUND = Set.of("minecraft:ice", "minecraft:frosted_ice", "minecraft:snow", "minecraft:lava",
            "minecraft:dirt_path", "minecraft:farmland");

    boolean isGround(String block);

    static boolean isAir(String block) {
        return AIR_BLOCKS.contains(block);
    }

    static boolean isThin(String block) {
        return THIN_GROUND.contains(block);
    }

    static boolean isWater(String block) {
        return block.equals("minecraft:water") || block.equals("minecraft:bubble_column") || block.equals("minecraft:sea_pickle")
                || block.endsWith("_fan") || block.contains("seagrass") || block.contains("kelp")
                || (block.contains("coral") && !block.endsWith("_block"));
    }

    /** Asks the server's block registry; a block it doesn't know is ground, so a column never runs down to the floor. */
    static SavedGround platform() {
        return new PlatformGround();
    }

    final class PlatformGround implements SavedGround {
        private static final List<String> TRUNK_SUFFIXES = List.of("_log", "_wood", "_stem", "_hyphae", "_mushroom_block");

        private final Map<String, Boolean> groundByBlock = new ConcurrentHashMap<>();

        @Override
        public boolean isGround(String block) {
            return groundByBlock.computeIfAbsent(block, PlatformGround::looksLikeGround);
        }

        private static boolean looksLikeGround(String block) {
            if (isThin(block)) {
                return true;
            }
            if (TRUNK_SUFFIXES.stream().anyMatch(block::endsWith)) {
                return false;
            }
            PlatformBlockState state = B.getStateOrNull(block, false);
            return state == null || state.isOccluding();
        }
    }
}
