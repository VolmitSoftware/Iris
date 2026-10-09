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

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Range;
import com.volmit.noise.NoiseStyle;

/** One more layer of noise a cave profile blends into its density, an entry of the profile's {@code modules}. */
@Desc("Represents a modular cave-density layer.")
public final class CaveFieldModule {
    @Desc("Density style used by this module.")
    private NoiseStyle style = NoiseStyle.of("CELLULAR_IRIS_DOUBLE");
    @MinNumber(0)
    @Desc("Layer contribution multiplier.")
    private double weight = 1;
    @MinNumber(-1)
    @MaxNumber(1)
    @Desc("Threshold offset applied to this layer before blending.")
    private double threshold = 0;
    @Desc("Vertical bounds where this module can contribute, in engine-local Y where 0 is the bottom of the dimension, not world Y.")
    private Range verticalRange = Range.of(0, 384);
    @Desc("Invert this module before weighting.")
    private boolean invert = false;

    public CaveFieldModule() {
    }

    public NoiseStyle style() {
        return style;
    }

    public double weight() {
        return weight;
    }

    public double threshold() {
        return threshold;
    }

    public Range verticalRange() {
        return verticalRange;
    }

    public boolean invert() {
        return invert;
    }
}
