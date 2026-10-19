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

import java.awt.Color;

/**
 * A colour with a light and a dark version that always answers with the one of the current theme, like IntelliJ's
 * JBColor. Components keep the same colour object across a theme switch and simply paint in the other version.
 */
final class ThemeColor extends Color {
    private static final long serialVersionUID = 2286435620386470207L;
    private final Color light;
    private final Color dark;

    ThemeColor(int lightRgb, int darkRgb) {
        this(new Color(lightRgb), new Color(darkRgb));
    }

    ThemeColor(Color light, Color dark) {
        super(dark.getRGB(), true);
        this.light = light;
        this.dark = dark;
    }

    // Every other colour query of Color, and Java2D's painting, go through this one.
    @Override
    public int getRGB() {
        return current().getRGB();
    }

    String toHtml() {
        return String.format("#%06X", getRGB() & 0xFFFFFF);
    }

    private Color current() {
        return StudioTheme.isDark() ? dark : light;
    }
}
