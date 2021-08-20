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

import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.project.loader.IrisRegistrant;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.noise.IrisWorm;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.Worm;
import com.volmit.iris.util.noise.Worm3;
import com.volmit.iris.util.noise.WormIterator3;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCave extends IrisRegistrant {
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

    public void generateCave(RNG rng, IrisData data, int x, int y, int z)
    {
        WormIterator3 w = worm.iterate3D(rng, data, x, y, z);

        while(w.hasNext())
        {
            Worm3 wx = w.next();
        }
    }
}
