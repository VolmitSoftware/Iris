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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.protocol.IrisMessage;

public final class IrisCursorResolver {
    private IrisCursorResolver() {
    }

    public static IrisMessage.CursorInfo resolve(Engine engine, int blockX, int blockZ) {
        IrisBiome surfaceBiome = engine.getSurfaceBiome(blockX, blockZ);
        IrisRegion region = engine.getRegion(blockX, blockZ);
        IrisBiome caveBiome = engine.getCaveBiome(blockX, blockZ);
        int height = engine.getMinHeight() + engine.getHeight(blockX, blockZ);
        String biomeKey = keyOf(surfaceBiome == null ? null : surfaceBiome.getLoadKey());
        String regionKey = keyOf(region == null ? null : region.getLoadKey());
        String caveBiomeKey = keyOf(caveBiome == null ? null : caveBiome.getLoadKey());
        String dimensionKey = keyOf(engine.getDimension().getLoadKey());
        return new IrisMessage.CursorInfo(blockX, blockZ, biomeKey, regionKey, caveBiomeKey, height, dimensionKey);
    }

    private static String keyOf(String value) {
        return value == null ? "" : value;
    }
}
