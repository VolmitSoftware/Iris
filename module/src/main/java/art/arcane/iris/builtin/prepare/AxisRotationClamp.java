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
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Objects;

/** One axis of an object rotation, with the angles it may turn by and the steps between them. */
@Snippet("iris/prepare/axis-rotation")
@Desc("Represents a rotation axis with intervals and maxes. The x and z axis values are defaulted to disabled. The Y axis defaults to on, rotating by 90 degree increments.")
public final class AxisRotationClamp {
    private static final double FULL_TURN_DEGREES = 360D;

    @Desc("Should this axis be rotated at all?")
    private boolean enabled = false;

    private transient boolean forceLock = false;

    @Required
    @MinNumber(-360)
    @MaxNumber(360)
    @Desc("The minimum angle (from) or set this and max to zero for any angle degrees. Set both to the same non-zero value to force it to that angle only")
    private double min = 0;

    @Required
    @MinNumber(-360)
    @MaxNumber(360)
    @Desc("The maximum angle (to) or set this and min to zero for any angle degrees. Set both to the same non-zero value to force it to that angle only")
    private double max = 0;

    @Required
    @MinNumber(0)
    @MaxNumber(360)
    @Desc("Iris spins the axis but not freely. For example an interval of 90 would mean 4 possible angles (right angles) degrees. \nSetting this to 0 means totally free rotation.\n\nNote that a lot of structures can have issues with non 90 degree intervals because the minecraft block resolution is so low.")
    private double interval = 0;

    public AxisRotationClamp() {
    }

    /** An axis turned as {@code enabled} says, by angles from {@code min} to {@code max} in steps of {@code interval}. */
    public static AxisRotationClamp of(boolean enabled, boolean forceLock, double min, double max, double interval) {
        AxisRotationClamp clamp = new AxisRotationClamp();
        clamp.enabled = enabled;
        clamp.forceLock = forceLock;
        clamp.min = min;
        clamp.max = max;
        clamp.interval = interval;
        return clamp;
    }

    /** An axis that turns to exactly {@code angle}, in steps of {@code interval}, whatever its min and max say. */
    public static AxisRotationClamp locked(boolean enabled, double angle, double interval) {
        return of(enabled, true, angle, angle, interval);
    }

    public boolean enabled() {
        return enabled;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double interval() {
        return interval;
    }

    public boolean isUnlimited() {
        return min == max && min == 0;
    }

    public boolean isLocked() {
        return min == max && !isUnlimited();
    }

    public double getRadians(int rng) {
        if (forceLock) {
            return Math.toRadians(Math.ceil(Math.abs((max % FULL_TURN_DEGREES))));
        }

        if (isUnlimited()) {
            double resolvedInterval = interval < 1 ? 1 : interval;
            return Math.toRadians((resolvedInterval * (Math.ceil(Math.abs((rng % FULL_TURN_DEGREES) / resolvedInterval)))) % FULL_TURN_DEGREES);
        }

        if (min == max && min != 0) {
            return Math.toRadians(max);
        }

        double angle = (interval * (Math.ceil(Math.abs((rng % FULL_TURN_DEGREES) / interval)))) % FULL_TURN_DEGREES;
        return Math.toRadians(Math.min(Math.max(min, max), Math.max(Math.min(min, max), angle)));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AxisRotationClamp clamp
                && enabled == clamp.enabled
                && Double.compare(min, clamp.min) == 0
                && Double.compare(max, clamp.max) == 0
                && Double.compare(interval, clamp.interval) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, min, max, interval);
    }
}
