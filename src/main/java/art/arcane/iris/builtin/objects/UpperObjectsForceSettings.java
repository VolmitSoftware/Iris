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

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** Whether the objects placed in the terrain stacked above ignore placement restrictions, written as {@code "upperObjectsForcePlace": true}. */
@Desc("When true, upper-dimension objects force-place regardless of placement restrictions (slope, underwater, clamp, collisions, carving). Normal dimension objects always place first; upper objects place second and may clip or occlude lower-dimension placements when this is enabled.")
@Feature(id = "iris:objects/upper-force", name = "Force upper dimension objects", key = "upperObjectsForcePlace", root = "force", targets = Target.DIMENSION)
public final class UpperObjectsForceSettings implements IrisFeature {
    @Desc("When true, upper-dimension objects force-place regardless of placement restrictions (slope, underwater, clamp, collisions, carving). Normal dimension objects always place first; upper objects place second and may clip or occlude lower-dimension placements when this is enabled.")
    private boolean force = false;

    public UpperObjectsForceSettings() {
    }

    public boolean force() {
        return force;
    }
}
