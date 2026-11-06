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
package art.arcane.iris.builtin.structure;

import com.volmit.config.Desc;

@Desc("Controls the shape of the expanded terrain carve around an overbored structure.")
public enum StructureCarveShape {
    @Desc("Carves the full expanded rectangular volume with straight walls, ceiling, and floor.")
    BOX,

    @Desc("Carves a smooth rounded volume around the structure without erosion noise.")
    ROUNDED,

    @Desc("Carves a rounded volume whose outer boundary is eroded by deterministic noise.")
    ERODED;

    private static final double ERODED_CEILING_GROWTH = 0.8D;

    public double maximumCeilingScale() {
        return maximumCeilingScale(1D);
    }

    /** How far above its configured head room a carve of this shape may reach, as a multiple of it. */
    public double maximumCeilingScale(double erosionStrength) {
        double strength = Double.isFinite(erosionStrength)
                ? Math.max(0D, Math.min(1D, erosionStrength)) : 1D;
        return switch (this) {
            case BOX, ROUNDED -> 1D;
            case ERODED -> 1D + ERODED_CEILING_GROWTH * strength;
        };
    }

    public int maximumCeilingExtension(int configuredHeight) {
        return maximumCeilingExtension(configuredHeight, 1D);
    }

    public int maximumCeilingExtension(int configuredHeight, double erosionStrength) {
        return (int) Math.ceil(Math.max(0, configuredHeight) * maximumCeilingScale(erosionStrength));
    }
}
