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

package art.arcane.iris.builtin.vacuum;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

/** How a VACUUM place mode bends terrain to meet an object's base, written as the placement's {@code vacuumSettings}. */
@Snippet("vacuum-settings")
@Desc("Defines how a VACUUM place mode bends terrain to meet an object's base.")
public final class VacuumSettings {
    @MinNumber(0)
    @MaxNumber(128)
    @Desc("The horizontal radius (in blocks) the terrain deformation extends beyond the object footprint before it blends back to the natural surface. 0 = derive automatically from the place mode (VACUUM, VACUUM_HIGH larger, VACUUM_FAST smaller).")
    private int radius = 0;
    @MinNumber(0.25)
    @MaxNumber(8)
    @Desc("The falloff exponent controlling how the deformation eases out across the radius. 1 = linear (cone), 2 = parabolic (default, gentle bowl), higher = flatter near the object with a sharper drop at the edge.")
    private double falloff = 2.0;
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("For VACUUM_ORGANIC: the maximum number of blocks the effective radius is randomly perturbed per column, giving the meeting edge an irregular organic outline instead of a clean circle.")
    private int organicJitter = 4;
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("For VACUUM_WAVY: the vertical amplitude (in blocks) of the rolling simplex wave applied across the terrain bend. The wave fades to zero under the object (so it stays flush) and at the outer radius (so it blends back to the surface), peaking across the mid-slope. 0 disables the wave.")
    private int waveAmplitude = 3;
    @MinNumber(0.1)
    @MaxNumber(64)
    @Desc("For VACUUM_WAVY: the frequency of the simplex wave. Higher values make tighter, choppier ripples; lower values make broad, gentle swells. The wavelength in blocks is roughly 100 / waveScale.")
    private double waveScale = 5.0;

    public VacuumSettings() {
    }

    public int radius() {
        return radius;
    }

    public double falloff() {
        return falloff;
    }

    public int organicJitter() {
        return organicJitter;
    }

    public int waveAmplitude() {
        return waveAmplitude;
    }

    public double waveScale() {
        return waveScale;
    }
}
