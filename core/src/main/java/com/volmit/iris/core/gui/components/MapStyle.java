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

/**
 * How map tiles are drawn on top of their plain colours: terrain shading, region borders and one highlighted biome or region.
 *
 * @param highlight the biome or region to keep bright while everything else dims, or null to show all
 */
public record MapStyle(boolean shading, boolean regionBorders, Highlight highlight) {
    public static final MapStyle PLAIN = new MapStyle(false, false, null);

    public MapStyle withShading(boolean nextShading) {
        return new MapStyle(nextShading, regionBorders, highlight);
    }

    public MapStyle withRegionBorders(boolean nextRegionBorders) {
        return new MapStyle(shading, nextRegionBorders, highlight);
    }

    public MapStyle withHighlight(Highlight nextHighlight) {
        return new MapStyle(shading, regionBorders, nextHighlight);
    }

    public record Highlight(MapLabels.Kind kind, String key, String name) {
    }
}
