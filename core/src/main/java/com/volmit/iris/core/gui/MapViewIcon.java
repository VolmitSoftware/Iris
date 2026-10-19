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

package com.volmit.iris.core.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The icon of each map view, taken from IntelliJ's new UI icon set (see the NOTICE next to the files).
 */
final class MapViewIcon {
    private static final String ICON_FOLDER = "iris/icons/views/";
    private static final int SIZE = 16;
    private static final Map<MapView, Icon> LOADED = new EnumMap<>(MapView.class);

    private MapViewIcon() {
    }

    static Icon of(MapView view) {
        return LOADED.computeIfAbsent(view, MapViewIcon::load);
    }

    private static Icon load(MapView view) {
        String fileName = view.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".svg";
        return new FlatSVGIcon(ICON_FOLDER + fileName, SIZE, SIZE, MapViewIcon.class.getClassLoader());
    }
}
