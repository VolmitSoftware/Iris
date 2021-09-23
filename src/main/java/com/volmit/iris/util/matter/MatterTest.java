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
import com.volmit.iris.util.hunk.bits.DataContainer;
import com.volmit.iris.util.hunk.bits.Writable;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import org.checkerframework.checker.units.qual.K;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MatterTest {
    public static void test()
    {
        try
        {
            CNG cng = NoiseStyle.STATIC.create(new RNG(1337));
            Writable<Integer> ffs = new Writable<Integer>() {
                @Override
                public Integer readNodeData(DataInputStream din) throws IOException {
                    return din.readInt();
                }

                @Override
                public void writeNodeData(DataOutputStream dos, Integer integer) throws IOException {
                    dos.writeInt(integer);
                }
            };
            DataContainer<Integer> p = new DataContainer<>(ffs, 32);

            for(int i = 0; i < 32; i++)
            {
                p.set(i,cng.fit(1, 7, i, i * 2));
            }

            byte[] dat = p.write();
            Iris.info("RAW DATA: " + IO.bytesToHex(dat));

            DataContainer<Integer> f = DataContainer.read(new ByteArrayInputStream(dat), ffs);
            byte[] d2 = f.write();
            if(Arrays.equals(dat, d2))
            {
                Iris.info("Correct! All data matches!");
            }

            else
            {
                Iris.warn("No match");
                Iris.error("RAW DATA: " + IO.bytesToHex(d2));
            }
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }
    }
}
