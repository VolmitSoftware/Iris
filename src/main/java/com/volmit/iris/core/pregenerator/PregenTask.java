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

package com.volmit.iris.core.pregenerator;

import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraled;
import com.volmit.iris.util.math.Spiraler;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PregenTask {
    @Builder.Default
    private Position2 center = new Position2(0,0);

    @Builder.Default
    private int radius = 1;

    public void iterateRegions(Spiraled s)
    {
        new Spiraler(radius * 2, radius * 2, s)
                .setOffset(center.getX(), center.getZ()).drain();
    }

    public void iterateRegion(int x, int z, Spiraled s)
    {
        new Spiraler(33, 33, (xx, zz) -> {
            if (xx < 0 || xx > 31 || zz < 0 || zz > 31) {
                s.on(xx+(x<<5), zz+(z<<5));
            }
        }).setOffset(15, 15).drain();
    }

    public void iterateAllChunks(Spiraled s)
    {
        new Spiraler(radius * 2, radius * 2, (x, z) -> iterateRegion(x, z, s))
                .setOffset(center.getX(), center.getZ()).drain();
    }
}
