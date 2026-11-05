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
package art.arcane.iris.engine.procedural.part;

import com.volmit.config.Desc;
import art.arcane.volmlib.util.math.RNG;

@Desc("A shift in blocks along each axis. Each axis takes a number or a [min, max] range.")
public final class PartOffset {
    @Desc("Blocks east (negative is west).")
    private ProceduralValue x;

    @Desc("Blocks up (negative is down).")
    private ProceduralValue y;

    @Desc("Blocks south (negative is north).")
    private ProceduralValue z;

    public double[] roll(RNG rng) {
        return new double[]{roll(x, rng), roll(y, rng), roll(z, rng)};
    }

    private static double roll(ProceduralValue value, RNG rng) {
        return value == null ? 0 : value.sample(rng, 0);
    }
}
