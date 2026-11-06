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
package art.arcane.iris.modules.terrain;

import com.volmit.noise.NoiseSampler;

/** The noise that sets how thick a layer is: a number between a layer's limits at every column. */
public final class LayerNoise {
    private final NoiseSampler noise;

    LayerNoise(NoiseSampler noise) {
        this.noise = noise;
    }

    /** The whole number between {@code min} and {@code max} the noise gives at the column. */
    public int fit(int min, int max, double x, double z) {
        if (min == max) {
            return min;
        }
        return (int) Math.round(min + noise.sample(x, z) * (max - min));
    }
}
