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
import java.util.Optional;

/** Where a tile's blocks come from. */
public enum BlockSource {
    /**
     * The chunks generated in memory: what the world would hold, at any scale. A tile generates every chunk its pixels
     * fall in, so one coarser than a chunk a pixel costs a chunk for each pixel, up to about a minute's work.
     */
    GENERATED("generated", 4_096D);

    private final String id;
    private final double largestBlocksPerPixel;

    BlockSource(String id, double largestBlocksPerPixel) {
        this.id = id;
        this.largestBlocksPerPixel = largestBlocksPerPixel;
    }

    public String id() {
        return id;
    }

    /** The coarsest scale the source is drawn at; a coarser tile would ask for more columns than it shows. */
    public double largestBlocksPerPixel() {
        return largestBlocksPerPixel;
    }

    public static Optional<BlockSource> byId(String id) {
        return Arrays.stream(values()).filter(source -> source.id.equals(id)).findFirst();
    }
}
