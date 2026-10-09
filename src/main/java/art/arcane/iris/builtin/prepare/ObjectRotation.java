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

package art.arcane.iris.builtin.prepare;

import com.volmit.config.Desc;
import com.volmit.config.Snippet;

/** How an object is turned around each axis, written as a placement's {@code rotation}. */
@Snippet("object-rotator")
@Desc("Configures rotation for iris")
public final class ObjectRotation {
    private static final double QUARTER_TURN_DEGREES = 90;

    @Desc("If this rotator is enabled or not")
    private boolean enabled = true;

    @Desc("The x axis rotation")
    private AxisRotationClamp xAxis = new AxisRotationClamp();

    @Desc("The y axis rotation")
    private AxisRotationClamp yAxis = AxisRotationClamp.of(true, false, 0, 0, QUARTER_TURN_DEGREES);

    @Desc("The z axis rotation")
    private AxisRotationClamp zAxis = new AxisRotationClamp();

    public ObjectRotation() {
    }

    public boolean enabled() {
        return enabled;
    }

    public AxisRotationClamp xAxis() {
        return xAxis;
    }

    public AxisRotationClamp yAxis() {
        return yAxis;
    }

    public AxisRotationClamp zAxis() {
        return zAxis;
    }
}
