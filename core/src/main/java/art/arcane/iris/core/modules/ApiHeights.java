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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.Engine;
import com.volmit.geometry.BlockBox;

/**
 * Where IrisApi's heights meet Iris's. IrisApi measures every Y from the dimension's bottom (0 is the bottom
 * block), and so do Iris's engine, mantle and object placers: the platform adds the world's minimum height
 * only where it touches the real world. IrisApi heights and engine heights are therefore the same numbers,
 * and this class is the one place that says so and builds the bounds in them. Code that needs the real world
 * Y of an IrisApi height adds {@link Engine#getMinHeight()} itself, outside the bridge.
 */
final class ApiHeights {
    /** The height of the dimension's lowest block. */
    static final int BOTTOM = 0;

    private ApiHeights() {
    }

    /** The columns from the minima, inclusive, to the maxima, exclusive, over the dimension's whole height. */
    static BlockBox columns(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return new BlockBox(minX, BOTTOM, minZ, maxX, BOTTOM + engine.getHeight(), maxZ);
    }
}
