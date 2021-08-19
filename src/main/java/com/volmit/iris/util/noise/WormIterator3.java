/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.noise;

import com.volmit.iris.util.function.NoiseProvider;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class WormIterator3 {
    private transient Worm3 worm;
    private int x;
    private int y;
    private int z;
    private int maxDistance;
    private int maxIterations;

    public boolean hasNext()
    {
        double dist = maxDistance - (Math.max(Math.max(Math.abs(worm.getX().getVelocity()),
                Math.abs(worm.getZ().getVelocity())),
                Math.abs(worm.getY().getVelocity())) + 1);
        return maxIterations > 0 &&
                ((x * x) - (worm.getX().getPosition() * worm.getX().getPosition()))
            + ((y * y) - (worm.getY().getPosition() * worm.getY().getPosition()))
            + ((z * z) - (worm.getZ().getPosition() * worm.getZ().getPosition())) < dist * dist;
    }

    public Worm3 next(NoiseProvider p)
    {
        if(worm == null)
        {
            worm = new Worm3(x, y, z, 0, 0, 0);
        }

        worm.getX().setVelocity(p.noise(worm.getX().getPosition(), 0));
        worm.getY().setVelocity(p.noise(worm.getY().getPosition(), 0));
        worm.getZ().setVelocity(p.noise(worm.getZ().getPosition(), 0));
        worm.step();

        return worm;
    }
}
