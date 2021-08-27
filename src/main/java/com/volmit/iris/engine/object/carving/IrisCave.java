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
import com.volmit.iris.engine.object.basic.IrisRange;
import com.volmit.iris.engine.object.block.IrisBlockData;
import com.volmit.iris.engine.object.noise.IrisWorm;
import com.volmit.iris.engine.object.objects.IrisObjectLimit;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCave extends IrisRegistrant {
    @Desc("Define the shape of this cave")
    private IrisWorm worm;

    @Desc("Define potential forking features")
    private IrisCarving fork = new IrisCarving();

    @Desc("Change the air block to fill worms with as caves")
    private IrisBlockData fill = new IrisBlockData("cave_air");

    @Desc("Limit the worm from ever getting higher or lower than this range")
    private IrisRange verticalRange = new IrisRange(3, 255);

    @Override
    public String getFolderName() {
        return "caves";
    }

    @Override
    public String getTypeName() {
        return "Cave";
    }

    public void generate(MantleWriter writer, RNG rng, Engine engine, int x, int y, int z) {

        writer.setLine(getWorm().generate(rng, engine.getData(), writer, verticalRange, x, y, z,
            (at) -> fork.doCarving(writer, rng, engine, at.getX(), at.getY(), at.getZ())),
            getWorm().getGirth().get(rng, x, z, engine.getData()), true,
            fill.getBlockData(engine.getData()));
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }

    public int getMaxSize(IrisData data) {
        return getWorm().getMaxDistance() + fork.getMaxRange(data);
    }
}
