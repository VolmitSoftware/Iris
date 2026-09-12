/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import art.arcane.volmlib.util.interpolation.NoiseBoundsProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Configures rotation for iris")
@Data
public class IrisInterpolator {
    public static final IrisInterpolator DEFAULT = new IrisInterpolator();

    @Required
    @Description("The interpolation method when two biomes use different heights but this same generator")
    private InterpolationMethod function = InterpolationMethod.BILINEAR_STARCAST_6;

    @Required
    @MinNumber(1)
    @MaxNumber(8192)
    @Description("The range checked horizontally. Smaller ranges yeild more detail but are not as smooth.")
    private double horizontalScale = 7;

    @Override
    public int hashCode() {
        // Bit-identical to Objects.hash(horizontalScale, function) without the Object[] or Double boxing.
        int result = 31 + Double.hashCode(horizontalScale);
        return (31 * result) + (function == null ? 0 : function.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IrisInterpolator i) {
            return i.getFunction().equals(function) && i.getHorizontalScale() == horizontalScale;
        }

        return false;
    }

    public double interpolate(double x, double z, NoiseProvider provider) {
        return interpolate((int) Math.round(x), (int) Math.round(z), provider);
    }

    public double interpolate(int x, int z, NoiseProvider provider) {
        return IrisInterpolation.getNoise(getFunction(), x, z, getHorizontalScale(), provider);
    }

    public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
        return interpolateBounds((int) Math.round(x), (int) Math.round(z), provider);
    }

    public NoiseBounds interpolateBounds(int x, int z, NoiseBoundsProvider provider) {
        return IrisInterpolation.getNoiseBounds(getFunction(), x, z, getHorizontalScale(), provider);
    }
}
