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
package art.arcane.iris.engine.procedural.part;

/** How far a point is from the center of a cross-section, in the section's own measure, so 1 is on its edge at radius 1. */
final class Sections {
    private static final double HEX_SLANT = Math.sqrt(3) / 2;

    private Sections() {
    }

    static double norm(PartSection section, double u, double w) {
        double au = Math.abs(u);
        double aw = Math.abs(w);
        return switch (section) {
            case ROUND -> Math.sqrt(u * u + w * w);
            case SQUARE -> Math.max(au, aw);
            case DIAMOND -> au + aw;
            case HEX -> Math.max(aw, au * HEX_SLANT + aw * 0.5);
        };
    }
}
