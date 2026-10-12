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

import com.volmit.config.Desc;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

/** The size of a sapling area, in saplings, that a tree override applies to. */
@Snippet("tree-size")
@Desc("Sapling override object picking options")
public final class TreeSize {
    @Required
    @Desc("The width of the sapling area")
    private int width = 1;

    @Required
    @Desc("The depth of the sapling area")
    private int depth = 1;

    public TreeSize() {
    }

    public TreeSize(int width, int depth) {
        this.width = width;
        this.depth = depth;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    /** Whether an area of {@code otherWidth} by {@code otherDepth} saplings is this size, turned either way. */
    public boolean matches(int otherWidth, int otherDepth) {
        return (width == otherWidth && depth == otherDepth) || (depth == otherWidth && width == otherDepth);
    }
}
