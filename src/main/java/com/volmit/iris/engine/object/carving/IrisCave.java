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

package com.volmit.iris.engine.object.carving;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.noise.IrisWorm;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.Worm3;
import com.volmit.iris.util.noise.WormIterator3;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCave extends IrisRegistrant {
    private static final BlockData CAVE_AIR = B.get("CAVE_AIR");
    @Desc("Define the shape of this cave")
    private IrisWorm worm;

    @Override
    public String getFolderName() {
        return "caves";
    }

    @Override
    public String getTypeName() {
        return "Cave";
    }

    public void generate(MantleWriter writer, RNG rng, Engine engine, int x, int y, int z) {

        IrisData data = engine.getData();
        WormIterator3 w = getWorm().iterate3D(rng, data, x, y, z);
        KList<Vector> points = new KList<>();
        int itr = 0;
        while (w.hasNext()) {
            itr++;
            Worm3 wx = w.next();
            points.add(new Vector(wx.getX().getPosition(), wx.getY().getPosition(), wx.getZ().getPosition()));
        }


        Iris.info(x + " " + y + " " + z + " /." + " POS: " + points.convert((i) -> "[" + i.getBlockX() + "," + i.getBlockY() + "," + i.getBlockZ() + "]").toString(", "));

        writer.setLine(points.convert(IrisPosition::new), getWorm().getGirth().get(rng, x, z, data), true, CAVE_AIR);


        // TODO decorate somehow
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
