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

package com.volmit.iris.util.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.format.C;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

public class Mantle
{
    private final File dataFolder;
    private final int worldHeight;
    private final Map<Long, MantleRegion> loadedRegions;

    public Mantle(File dataFolder, int worldHeight)
    {
        this.dataFolder = dataFolder;
        this.worldHeight = worldHeight;
        dataFolder.mkdirs();
        loadedRegions = new KMap<>();
    }

    @RegionCoordinates
    public MantleRegion get(int x, int z)
    {
        Long k = key(x, z);
        MantleRegion region = loadedRegions.get(k);

        if(region != null)
        {
            return region;
        }

        synchronized (loadedRegions)
        {
            // Ensure we are the first loading thread
            region = loadedRegions.get(k);

            if(region != null)
            {
                return region;
            }

            File file = fileForRegion(x, z);

            if(file.exists())
            {
                try
                {
                    FileInputStream fin = new FileInputStream(file);
                    DataInputStream din = new DataInputStream(fin);
                    region = new MantleRegion(worldHeight, din);
                    din.close();
                    Iris.debug("Loaded Mantle Region " + C.RED + x + " " + z + C.DARK_AQUA + " " + file.getName());
                }

                catch(Throwable e)
                {
                    Iris.error("Failed to read Mantle Region " + file.getAbsolutePath() + " creating a new chunk instead.");
                    Iris.reportError(e);
                    e.printStackTrace();
                    region = null;
                }
            }

            if(region != null)
            {
                return region;
            }

            Iris.debug("Created new Mantle Region " + C.RED + x + " " + z);
            return new MantleRegion(worldHeight);
        }
    }

    private File fileForRegion(int x, int z) {
        return new File("m." + x + "." + z + ".mtl");
    }

    public Long key(int x, int z)
    {
        return Cache.key(x, z);
    }
}
