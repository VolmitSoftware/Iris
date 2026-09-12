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

package art.arcane.iris.studio.view;

public record GuiMarker(String label, double worldX, double worldY, double worldZ, double health, double maxHealth) {
    public static GuiMarker player(String name, double worldX, double worldZ) {
        return new GuiMarker(name, worldX, 0, worldZ, 0, 0);
    }

    public static GuiMarker entity(String label, double worldX, double worldY, double worldZ, double health, double maxHealth) {
        return new GuiMarker(label, worldX, worldY, worldZ, health, maxHealth);
    }
}
