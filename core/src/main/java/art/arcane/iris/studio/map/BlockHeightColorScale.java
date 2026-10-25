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

package art.arcane.iris.studio.map;

import java.awt.Color;

/**
 * Colours noise that is already a height in blocks, spreading a palette over the heights it can reach.
 */
record BlockHeightColorScale(NoisePalette palette, double minimum, double maximum) implements NoiseColorScale {
    @Override
    public int colorFinite(double value) {
        double span = maximum - minimum;
        return palette.colorNormalized(span <= 0D ? 0.5D : (value - minimum) / span);
    }

    @Override
    public int colorInvalid() {
        return palette.colorInvalid();
    }

    @Override
    public Color displayColorNormalized(double normalized) {
        return palette.displayColorNormalized(normalized);
    }

    @Override
    public String label() {
        return "Blocks";
    }
}
