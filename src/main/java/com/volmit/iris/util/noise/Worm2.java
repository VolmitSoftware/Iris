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

import com.volmit.iris.util.math.Position2;
import lombok.Data;

@Data
public class Worm2
{
    private final Worm x;
    private final Worm z;

    public Worm2(Worm x, Worm z)
    {
        this.x = x;
        this.z = z;
    }

    public Worm2(int x, int z, int vx, int vz)
    {
        this(new Worm(x, vx), new Worm(z, vz));
    }

    public void step()
    {
        x.step();
        z.step();
    }

    public void unstep()
    {
        x.unstep();
        z.unstep();
    }
}
