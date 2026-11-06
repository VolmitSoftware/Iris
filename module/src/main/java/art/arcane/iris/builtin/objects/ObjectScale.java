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

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

/** How an object placement scales its objects, written as the placement's {@code scale}. */
@Snippet("iris/objects/object-scale")
@Desc("Scale Objects")
public final class ObjectScale {
    private static final double NO_SCALE = 1;

    @MinNumber(0.01)
    @MaxNumber(50)
    @Desc("Fixed scale multiplier for this object. 0.5 shrinks to half size, 2.0 doubles the size. When set to anything other than 1, this overrides minimumScale and maximumScale. Leave at 1 to use the minimumScale/maximumScale range.")
    private double size = NO_SCALE;

    @MinNumber(1)
    @MaxNumber(32)
    @Desc("Iris Objects are scaled and cached to speed up placements. Because of this extra memory is used, so we evenly distribute variations across the defined scale range, then pick one randomly. If the differences is small, use a lower number. For more possibilities on the scale spectrum, increase this at the cost of memory.")
    private int variations = 7;

    @MinNumber(0.01)
    @MaxNumber(50)
    @Desc("The minimum scale. Used when size is 1 to pick a random scale per placement.")
    private double minimumScale = NO_SCALE;

    @MinNumber(0.01)
    @MaxNumber(50)
    @Desc("The maximum scale. Used when size is 1 to pick a random scale per placement.")
    private double maximumScale = NO_SCALE;

    @Desc("If this object is scaled up beyond its origin size, specify a 3D interpolator. NONE keeps blocky scaled output, TRILINEAR (LERP) smooths with linear interpolation, TRICUBIC and TRIHERMITE produce smoother but slower output.")
    private ScaleInterpolation interpolation = ScaleInterpolation.NONE;

    public ObjectScale() {
    }

    public double size() {
        return size;
    }

    public int variations() {
        return variations;
    }

    public double minimumScale() {
        return minimumScale;
    }

    public double maximumScale() {
        return maximumScale;
    }

    public ScaleInterpolation interpolation() {
        return interpolation == null ? ScaleInterpolation.NONE : interpolation;
    }

    /** The largest factor this scale makes an object, which a placement's reach has to cover. */
    public double maxScale() {
        if (size != NO_SCALE) {
            return size;
        }
        return Math.max(minimumScale, maximumScale);
    }
}
