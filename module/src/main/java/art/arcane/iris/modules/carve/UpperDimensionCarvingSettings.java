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

package art.arcane.iris.modules.carve;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** A dimension's {@code upperDimensionCarving}, written as the bare boolean. */
@Desc("When true, cave carving will cut through the upper dimension terrain. When false, the upper terrain is a solid untouched mass.")
@Feature(id = "iris:carve/upper-dimension", name = "Upper dimension carving", key = "upperDimensionCarving", root = "enabled", targets = Level.DIMENSION)
public final class UpperDimensionCarvingSettings implements IrisFeature {
    @Desc("When true, cave carving will cut through the upper dimension terrain. When false, the upper terrain is a solid untouched mass.")
    private boolean enabled = false;

    public UpperDimensionCarvingSettings() {
    }

    public UpperDimensionCarvingSettings(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
