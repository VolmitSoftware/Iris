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

/** Whether a dimension refuses surface objects over carved openings, written as {@code "requireObjectSurfaceSupport": true}. */
@Desc("Refuse to place surface objects and trees over carved surface openings.")
@Feature(id = "iris:objects/surface-support", name = "Require surface support", key = "requireObjectSurfaceSupport", root = "require", targets = Level.DIMENSION)
public final class RequireSurfaceSupportSettings implements IrisFeature {
    private static final boolean DEFAULT_REQUIRED = true;

    @Desc("Refuse to place surface objects and trees over carved surface openings.")
    private boolean require = DEFAULT_REQUIRED;

    public RequireSurfaceSupportSettings() {
    }

    public boolean require() {
        return require;
    }
}
