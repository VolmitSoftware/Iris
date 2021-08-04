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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.RNG;
import lombok.Data;

@Data

@Desc("Represents a potential Iris zone")
public class IrisFeaturePotential {
    @MinNumber(0)
    @Required
    @Desc("The rarity is 1 in X chance per chunk")
    private int rarity = 100;

    @Required
    @Desc("")
    private IrisFeature zone = new IrisFeature();

    @ChunkCoordinates
    public boolean hasZone(RNG rng, int cx, int cz) {
        return rng.nextInt(rarity) == 0;
    }
}
