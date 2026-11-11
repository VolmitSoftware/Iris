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

package art.arcane.iris.core.studio.map;

/** What lies on top of the world's columns, from one {@link BlockSource}. */
@FunctionalInterface
interface ColumnTops {
    ColumnTop at(int worldX, int worldZ);

    /**
     * The top of one column.
     *
     * @param groundY the height of its ground block above the dimension's bottom
     * @param ground  the ground block's key, or null where the column holds no ground
     * @param cover   the key of the block standing on the ground, such as grass or a layer of snow, or null for none
     */
    record ColumnTop(int groundY, String ground, String cover) {
        static final ColumnTop NOTHING = new ColumnTop(0, null, null);
    }
}
