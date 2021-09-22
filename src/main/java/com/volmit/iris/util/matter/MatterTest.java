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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.nbt.tag.ByteArrayTag;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MatterTest {
    public static long memorySample()
    {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    public static void test()
    {
        for(Thread i : Thread.getAllStackTraces().keySet())
        {
            if(i.getId() != Thread.currentThread().getId())
            {
                try {
                    i.wait(10000);
                } catch (Throwable ignored) {

                }
            }
        }

        System.gc();
        System.gc();
        J.sleep(250);

        try {

            double ms = 0;
            long a = memorySample();
            PrecisionStopwatch p = PrecisionStopwatch.start();
            IrisSettings.get().getPerformance().setUseExperimentalMantleMemoryCompression(true);
            Mantle mantle = new Mantle(new File("mantle-test/legacy"), 256);

            for(int i = 0; i < 512; i++)
            {
                for(int j = 0; j < 255; j++)
                {
                    for(int k = 0; k < 512; k++)
                    {
                        mantle.set(i,j,k,RNG.r.chance(0.5));
                        mantle.set(i,j,k,RNG.r.chance(0.5)?"a" : "b");
                    }
                }
            }

            ms += p.getMilliseconds();
            long b = memorySample() - a;
            Iris.info("Memory: " + Form.memSize(b, 0) + " (" + Form.f(b) + " bytes)");
           p = PrecisionStopwatch.start();
           mantle.saveAll();
            mantle.close();
            ms+=p.getMilliseconds();
            Iris.info("Closed, done! took " + Form.duration(ms, 2));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
