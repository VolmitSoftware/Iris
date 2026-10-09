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

package art.arcane.iris.engine.platform;

import com.volmit.service.EntitySpawns;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

/** Whether a block is the kind of ground or fluid an entity spawns on. */
public final class BukkitSurfaces {
    private BukkitSurfaces() {
    }

    /** Whether {@code block} suits an entity that wants {@code surface} below it. */
    public static boolean matches(EntitySpawns.Surface surface, Block block) {
        Material type = block.getType();
        if (type.isSolid()) {
            return surface == EntitySpawns.Surface.LAND || surface == EntitySpawns.Surface.OVERWORLD
                    || (surface == EntitySpawns.Surface.ANIMAL && isAnimalGround(type));
        }
        if (type == Material.LAVA) {
            return surface == EntitySpawns.Surface.LAVA;
        }
        if (isWater(type, block)) {
            return surface == EntitySpawns.Surface.WATER || surface == EntitySpawns.Surface.OVERWORLD;
        }
        return false;
    }

    private static boolean isAnimalGround(Material type) {
        return type == Material.GRASS_BLOCK || type == Material.DIRT
                || type == Material.DIRT_PATH || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT || type == Material.PODZOL
                || type == Material.MYCELIUM || type == Material.SNOW_BLOCK;
    }

    private static boolean isWater(Material type, Block block) {
        return type == Material.WATER || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS || type == Material.KELP_PLANT
                || type == Material.KELP
                || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged());
    }
}
