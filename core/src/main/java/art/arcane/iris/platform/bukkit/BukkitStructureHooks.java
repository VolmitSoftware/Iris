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
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit adapter for structure and feature hooks backed by the active NMS binding.
 */
public final class BukkitStructureHooks implements PlatformStructureHooks {
    @Override
    public List<String> structureKeys() {
        return new ArrayList<>(INMS.get().getStructureKeys());
    }

    @Override
    public List<String> jigsawStructureKeys() {
        return new ArrayList<>(INMS.get().getJigsawStructureKeys());
    }

    @Override
    public List<String> templatePoolKeys() {
        return new ArrayList<>(INMS.get().getTemplatePoolKeys());
    }

    @Override
    public JigsawSourceMetadata jigsawSourceMetadata(String structureKey) {
        return INMS.get().getJigsawSourceMetadata(structureKey);
    }

    @Override
    public int templatePoolHorizontalSpan(String templatePoolKey) {
        return INMS.get().getTemplatePoolHorizontalSpan(templatePoolKey);
    }

    @Override
    public int jigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        return INMS.get().getJigsawStartPoolHorizontalSpan(structureKey, templatePoolKey);
    }

    @Override
    public List<String> structureSetKeys() {
        return new ArrayList<>(INMS.get().getStructureSetKeys());
    }

    @Override
    public List<String> structureBiomeKeys(String structureKey) {
        return new ArrayList<>(INMS.get().getStructureBiomeKeys(structureKey));
    }

    @Override
    public List<String> objectFeatureKeys() {
        return new ArrayList<>(INMS.get().getObjectFeatureKeys());
    }

    @Override
    public List<String> reachableStructureKeys(PlatformWorld world) {
        return new ArrayList<>(INMS.get().getReachableStructureKeys(BukkitPlatform.unwrapWorld(world)));
    }

    @Override
    public List<String> possibleBiomeKeys(PlatformWorld world) {
        return new ArrayList<>(INMS.get().getPossibleBiomeKeys(BukkitPlatform.unwrapWorld(world)));
    }

    @Override
    public boolean placeFeature(PlatformWorld world, int x, int y, int z, String featureKey, long seed) {
        return INMS.get().placeFeature(BukkitPlatform.unwrapWorld(world), x, y, z, featureKey, seed);
    }

    @Override
    public int[] placeStructure(PlatformWorld world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
        return INMS.get().placeStructure(BukkitPlatform.unwrapWorld(world), chunkX, chunkZ, structureKey, seed, maxSpan);
    }

    @Override
    public boolean supportsStructurePlacement() {
        Class<?> type = INMS.get().getClass();
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                if ("placeStructure".equals(method.getName())) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
