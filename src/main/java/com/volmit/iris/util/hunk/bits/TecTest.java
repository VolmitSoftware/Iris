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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.slices.BlockMatter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.Set;

public class TecTest {
    public static Set<BlockData> randomBlocks(int max) {
        KSet<BlockData> d = new KSet<>();

        while(d.size() < max) {
            Material m = Material.values()[RNG.r.i(Material.values().length - 1)];
            if(m.isBlock()) {
                d.add(m.createBlockData());
            }
        }

        return d;
    }

    public static void go() {

    }

    public static boolean test(int size, int pal) {
        try {
            Iris.info("Test? " + size + " " + pal);
            KList<BlockData> blocks = new KList<>(randomBlocks(pal));
            Iris.info("Fill " + pal + " -> " + size + " Entries");
            Writable<BlockData> writer = new BlockMatter();
            DataContainer<BlockData> dc = new DataContainer<>(writer, size);

            for(int i = 0; i < dc.size(); i++) {
                dc.set(i, blocks.getRandom());
            }

            Iris.info(dc.toString());
            byte[] dat = dc.write();
            DataContainer<BlockData> dx = new DataContainer<>(new DataInputStream(new ByteArrayInputStream(dat)), writer);
            Iris.info(dx.toString());
            byte[] dat2 = dx.write();
            Iris.info("Size: " + Form.memSize(dat.length, 2) + " -> " + Form.memSize(dat2.length, 2));

            if(Arrays.equals(dat, dat2)) {
                Iris.info("MATCH");
                return true;
            } else {
                for(int i = 0; i < dc.size(); i++) {
                    if(!dx.get(i).equals(dc.get(i))) {
                        Iris.info("FAIL Expected " + dc.get(i).getAsString(true) + " but got " + dx.get(i).getAsString(true));
                        return false;
                    }
                }
                Iris.info("MATCH but different output?");

                return true;
            }
        } catch(Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}
