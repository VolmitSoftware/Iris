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

package com.volmit.iris.engine.object.cave;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.Worm3;
import com.volmit.iris.util.noise.WormIterator3;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCavePlacer implements IRare {
    private static final BlockData CAVE_AIR = B.get("CAVE_AIR");

    @Required
    @Desc("Typically a 1 in RARITY on a per chunk basis")
    @MinNumber(1)
    private int rarity = 15;

    @MinNumber(1)
    @Required
    @Desc("The cave to place")
    @RegistryListResource(IrisCave.class)
    private String cave;

    private transient final AtomicCache<IrisCave> caveCache = new AtomicCache<>();
    private transient final AtomicBoolean fail = new AtomicBoolean(false);

    public IrisCave getRealCave(IrisData data) {
        return caveCache.aquire(() -> data.getCaveLoader().load(getCave()));
    }

    public void generateCave(Mantle mantle, RNG rng, IrisData data, int x, int y, int z) {
        if (fail.get()) {
            return;
        }

        IrisCave cave = getRealCave(data);

        if (cave == null) {
            Iris.warn("Unable to locate cave for generation!");
            fail.set(true);
            return;
        }

        WormIterator3 w = cave.getWorm().iterate3D(rng, data, x, y, z);
        KList<Vector> points = new KList<>();
        int itr = 0;
        while (w.hasNext()) {
            itr++;
            Worm3 wx = w.next();
            points.add(new Vector(wx.getX().getPosition(), wx.getY().getPosition(), wx.getZ().getPosition()));
        }


        Iris.info(x + " " + y + " " + z + " /." + " POS: " + points.convert((i) -> "[" + i.getBlockX() + "," + i.getBlockY() + "," + i.getBlockZ() + "]").toString(", "));

        mantle.setLine(points.convert(IrisPosition::new), cave.getWorm().getGirth().get(rng, x, z, data), true, CAVE_AIR);


        // TODO decorate somehow
    }
}
