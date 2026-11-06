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
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

/** The terrain slopes an object placement, a decorator or a terrain layer may sit on, written as its {@code slopeCondition}. */
@Snippet("iris/prepare/slope-clip")
@Desc("Limits placement to a minimum and maximum terrain slope")
public final class SlopeClip {
    private static final double DEFAULT_MINIMUM_SLOPE = 0;
    private static final double DEFAULT_MAXIMUM_SLOPE = 10;

    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The minimum slope for placement")
    private double minimumSlope = DEFAULT_MINIMUM_SLOPE;

    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The maximum slope for placement")
    private double maximumSlope = DEFAULT_MAXIMUM_SLOPE;

    public SlopeClip() {
    }

    public double minimumSlope() {
        return minimumSlope;
    }

    public double maximumSlope() {
        return maximumSlope;
    }

    /** Whether every slope passes. */
    public boolean isDefault() {
        return minimumSlope <= 0 && maximumSlope >= DEFAULT_MAXIMUM_SLOPE;
    }

    public boolean isValid(double slope) {
        if (isDefault()) {
            return true;
        }

        return !(minimumSlope > slope) && !(maximumSlope < slope);
    }
}
