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

package com.volmit.iris.core.gui.components;

import java.util.Objects;

/**
 * What a map tile shows: one of the render types, and for a region preview which region every spot is treated as.
 *
 * @param previewRegionKey the region a {@link RenderType#REGION_PREVIEW} tile shows everywhere, null for every other type
 */
public record MapLayer(RenderType type, String previewRegionKey) {
    public MapLayer {
        Objects.requireNonNull(type, "type");
        if ((type == RenderType.REGION_PREVIEW) != (previewRegionKey != null)) {
            throw new IllegalArgumentException("Only a region preview names a region");
        }
    }

    public static MapLayer of(RenderType type) {
        return new MapLayer(type, null);
    }

    public static MapLayer regionPreview(String regionKey) {
        return new MapLayer(RenderType.REGION_PREVIEW, Objects.requireNonNull(regionKey, "regionKey"));
    }
}
