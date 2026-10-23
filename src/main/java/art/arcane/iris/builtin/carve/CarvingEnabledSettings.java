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

/** A dimension's {@code carvingEnabled}, written as the bare boolean. */
@Desc("Enable or disable all carving for this dimension")
@Feature(id = "iris:carve/enabled", name = "Carving enabled", key = "carvingEnabled", root = "enabled", targets = Level.DIMENSION)
public final class CarvingEnabledSettings implements IrisFeature {
    @Desc("Enable or disable all carving for this dimension")
    private boolean enabled = true;

    public CarvingEnabledSettings() {
    }

    public CarvingEnabledSettings(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
