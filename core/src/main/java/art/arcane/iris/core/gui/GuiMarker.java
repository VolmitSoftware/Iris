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

package art.arcane.iris.core.gui;

/**
 * @param yaw the way it faces, in Minecraft's degrees: 0 is south, 90 is west
 */
public record GuiMarker(String label, double worldX, double worldY, double worldZ, float yaw, double health, double maxHealth) {
    static final float YAW_STEP_DEGREES = 5F;

    /** The yaw is rounded to {@link #YAW_STEP_DEGREES}, so a turning head does not resend the player every poll. */
    public static GuiMarker player(String name, double worldX, double worldZ, float yaw) {
        return new GuiMarker(name, worldX, 0, worldZ, Math.round(yaw / YAW_STEP_DEGREES) * YAW_STEP_DEGREES, 0, 0);
    }

    public static GuiMarker entity(String label, double worldX, double worldY, double worldZ, double health, double maxHealth) {
        return new GuiMarker(label, worldX, worldY, worldZ, 0F, health, maxHealth);
    }
}
