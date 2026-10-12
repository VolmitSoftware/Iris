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

package art.arcane.iris.builtin.objects;

/**
 * A carved void of the mantle, as the cave carver left it.
 *
 * @param air whether the void holds air
 * @param liquid the carver's liquid code for the void; {@link #FORCED_AIR_LIQUID} means air that stays air
 */
public record Cavern(boolean air, byte liquid) {
    public static final byte FORCED_AIR_LIQUID = 3;
}
