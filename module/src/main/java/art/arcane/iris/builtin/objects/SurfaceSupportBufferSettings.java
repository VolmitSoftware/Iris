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

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/** The least surface-support buffer a dimension gives its surface objects, written as {@code "objectSurfaceSupportBuffer": 2}. */
@Desc("Minimum surface-support buffer, in blocks, applied to every surface object placement in this dimension. A placement may ask for more but never less.")
@Feature(id = "iris:objects/surface-support-buffer", name = "Surface support buffer", key = "objectSurfaceSupportBuffer", root = "buffer", targets = Level.DIMENSION)
public final class SurfaceSupportBufferSettings implements IrisFeature {
    private static final int DEFAULT_BUFFER_BLOCKS = 2;

    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Minimum surface-support buffer, in blocks, applied to every surface object placement in this dimension. A placement may ask for more but never less.")
    private int buffer = DEFAULT_BUFFER_BLOCKS;

    public SurfaceSupportBufferSettings() {
    }

    public int buffer() {
        return buffer;
    }
}
