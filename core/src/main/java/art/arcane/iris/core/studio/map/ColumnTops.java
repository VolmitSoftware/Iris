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

import java.util.Arrays;

/** What lies on top of the world's columns, from one {@link BlockSource}. */
@FunctionalInterface
interface ColumnTops {
    ColumnTop at(int worldX, int worldZ);

    /**
     * The tops of the grid's columns found at once, so asking for them one by one after is quick; this source itself
     * where its tops are quick to find.
     *
     * @throws java.util.concurrent.CancellationException as soon as the grid's request is cancelled
     */
    default ColumnTops prepared(TileGrid grid) {
        return this;
    }

    /**
     * The top of one column.
     *
     * @param groundY the height of its ground block above the dimension's bottom: the terrain's top, under what stands
     *                on it
     * @param ground  the ground block's key, or null where the column holds no ground
     * @param cover   the key of the block standing on the ground, such as grass or a layer of snow, or null for none
     * @param raised  the solid blocks above the ground, such as a tree's wood and leaves
     */
    record ColumnTop(int groundY, String ground, String cover, Raised raised) {
        static final ColumnTop NOTHING = new ColumnTop(0, null, null, Raised.NONE);

        ColumnTop(int groundY, String ground, String cover) {
            this(groundY, ground, cover, Raised.NONE);
        }
    }

    /**
     * The solid blocks above a column's ground, highest first, with the gaps between them left out. Kept as two arrays,
     * as a forest's chunk holds over a thousand of them.
     *
     * @param ys     each block's height above the dimension's bottom
     * @param blocks each block's key
     */
    record Raised(short[] ys, String[] blocks) {
        static final Raised NONE = new Raised(new short[0], new String[0]);

        int count() {
            return ys.length;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Raised raised && Arrays.equals(ys, raised.ys) && Arrays.equals(blocks, raised.blocks);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(ys) + Arrays.hashCode(blocks);
        }

        @Override
        public String toString() {
            return "Raised" + Arrays.toString(ys) + Arrays.toString(blocks);
        }
    }
}
