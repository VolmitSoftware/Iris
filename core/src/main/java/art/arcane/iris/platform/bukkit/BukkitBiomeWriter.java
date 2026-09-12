/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit adapter for biome resolution backed by the active NMS binding.
 */
public final class BukkitBiomeWriter implements PlatformBiomeWriter {
    @Override
    public int biomeIdFor(String key) {
        return INMS.get().getBiomeBaseIdForKey(key);
    }

    @Override
    public List<PlatformBiome> allBiomes() {
        List<?> natives = INMS.get().getBiomes();
        List<PlatformBiome> biomes = new ArrayList<>(natives.size());
        for (Object biome : natives) {
            biomes.add(BukkitPlatform.wrapBiome(biome));
        }
        return biomes;
    }
}
