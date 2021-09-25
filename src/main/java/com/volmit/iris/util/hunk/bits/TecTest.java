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

package com.volmit.iris.util.hunk.bits;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.RNG;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class TecTest {
    public static void go()
    {
        Mantle m = new Mantle(new File("dummy"), 256);

        int size = 255;
        int mx = (int) Math.pow(size, 3);

        for(int i = 0; i < mx; i++)
        {
            int[] p = Cache.to3D(i, size, size);
            m.set(p[0], p[1], p[2], RNG.r.s(1));
        }

        m.close();

        m = new Mantle(new File("dummy"), 256);
        m.get(0,0,0, String.class);
    }
}
