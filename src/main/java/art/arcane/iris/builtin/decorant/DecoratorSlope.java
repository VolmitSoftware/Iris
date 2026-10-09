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

package art.arcane.iris.builtin.decorant;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/** The terrain slopes a decorator may be placed on, as packs write it in a decorator's {@code slopeCondition}. */
@Desc("Limits placement to a minimum and maximum terrain slope")
public final class DecoratorSlope {
    private static final double UNLIMITED_MINIMUM = 0;
    private static final double UNLIMITED_MAXIMUM = 10;

    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The minimum slope for placement")
    private double minimumSlope = UNLIMITED_MINIMUM;

    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The maximum slope for placement")
    private double maximumSlope = UNLIMITED_MAXIMUM;

    public DecoratorSlope() {
    }

    public double minimumSlope() {
        return minimumSlope;
    }

    public double maximumSlope() {
        return maximumSlope;
    }

    /** Whether every slope passes: the limits are the defaults, or looser. */
    public boolean isDefault() {
        return minimumSlope <= UNLIMITED_MINIMUM && maximumSlope >= UNLIMITED_MAXIMUM;
    }

    public boolean isValid(double slope) {
        if (isDefault()) {
            return true;
        }

        return !(minimumSlope > slope) && !(maximumSlope < slope);
    }
}
