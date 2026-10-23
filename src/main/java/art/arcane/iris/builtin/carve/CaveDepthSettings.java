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

package art.arcane.iris.builtin.carve;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/** A biome's {@code caveMinDepthBelowSurface}, written as the bare number of blocks. */
@Desc("Minimum depth below terrain surface required before this cave biome can be selected.")
@Feature(id = "iris:carve/min-depth", name = "Cave minimum depth", key = "caveMinDepthBelowSurface", root = "depth", targets = Level.BIOME)
public final class CaveDepthSettings implements IrisFeature {
    @MinNumber(0)
    @MaxNumber(256)
    @Desc("Minimum depth below terrain surface required before this cave biome can be selected.")
    private int depth = 0;

    public CaveDepthSettings() {
    }

    public CaveDepthSettings(int depth) {
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }
}
