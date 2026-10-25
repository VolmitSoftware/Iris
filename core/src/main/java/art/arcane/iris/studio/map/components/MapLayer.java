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

package art.arcane.iris.studio.map.components;

import java.util.Objects;

/**
 * What a map tile shows: one of the tile types, for a region preview which region every spot is treated as, and in
 * the region and biome views whether the noise that picks is drawn too.
 *
 * @param previewRegionKey the region a {@link TileType#REGION_PREVIEW} tile shows everywhere, null for every other type
 * @param display          how the picking noise is drawn; only the region and biome views draw anything but the picks
 */
public record MapLayer(TileType type, String previewRegionKey, PickDisplay display, boolean waterOverlay) {
    public MapLayer {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(display, "display");
        if ((type == TileType.REGION_PREVIEW) != (previewRegionKey != null)) {
            throw new IllegalArgumentException("Only a region preview names a region");
        }
        if (display != PickDisplay.PICKS && !type.drawsPickNoise()) {
            throw new IllegalArgumentException(type + " has no picking noise to draw");
        }
        waterOverlay = waterOverlay && type == TileType.BIOME && display == PickDisplay.PICKS;
    }

    public static MapLayer of(TileType type) {
        return new MapLayer(type, null, PickDisplay.PICKS, true);
    }

    public static MapLayer biomes(PickDisplay display, boolean waterOverlay) {
        return new MapLayer(TileType.BIOME, null, display, waterOverlay);
    }

    public static MapLayer regions(PickDisplay display) {
        return new MapLayer(TileType.REGION, null, display, false);
    }

    public static MapLayer regionPreview(String regionKey, PickDisplay display) {
        return new MapLayer(TileType.REGION_PREVIEW, Objects.requireNonNull(regionKey, "regionKey"), display, false);
    }
}
