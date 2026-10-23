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

/** Whether the objects of a dimension are also placed in the terrain stacked above it, written as {@code "upperDimensionObjects": true}. */
@Desc("When true, objects from the mantle (structures, trees, etc.) can be placed in the upper dimension terrain zone. When false, the upper terrain is protected from object placement.")
@Feature(id = "iris:objects/upper", name = "Upper dimension objects", key = "upperDimensionObjects", root = "enabled", targets = Level.DIMENSION)
public final class UpperObjectsSettings implements IrisFeature {
    @Desc("When true, objects from the mantle (structures, trees, etc.) can be placed in the upper dimension terrain zone. When false, the upper terrain is protected from object placement.")
    private boolean enabled = false;

    public UpperObjectsSettings() {
    }

    public boolean enabled() {
        return enabled;
    }
}
