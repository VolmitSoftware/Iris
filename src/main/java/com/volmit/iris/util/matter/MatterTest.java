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

package com.volmit.iris.util.matter;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.palette.PalettedContainer;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import org.checkerframework.checker.units.qual.K;

import java.util.Arrays;
import java.util.List;

public class MatterTest {
    public static void test()
    {
        CNG cng = NoiseStyle.STATIC.create(new RNG(1337));
        PalettedContainer<Integer> p = new PalettedContainer<>();

        for(int i = 0; i < 16; i++)
        {
            for(int j = 0; j < 16; j++)
            {
                for(int k = 0; k < 16; k++)
                {
                    p.set(i,j,k,cng.fit(1, 3, i,j,k));
                }
            }
        }

        KList<Integer> palette = new KList<>();
        long[] data = p.write(palette);

        Iris.info("RAW PALE: " + palette.toString(","));
        Iris.info("RAW DATA: " + IO.longsToHex(data));

        PalettedContainer<Integer> px = new PalettedContainer<>();
        px.read(palette, data);

        KList<Integer> palette2 = new KList<>();
        long[] data2 = px.write(palette);

        if(Arrays.equals(data, data2))
        {
            Iris.info("Correct! All data matches!");
        }

        else
        {
            Iris.warn("No match");
            Iris.error("RAW PALE: " + palette2.toString(","));
            Iris.error("RAW DATA: " + IO.longsToHex(data2));
        }
    }
}
