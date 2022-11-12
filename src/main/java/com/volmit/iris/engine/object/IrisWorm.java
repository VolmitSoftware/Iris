/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.function.Consumer;

@Snippet("worm")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Generate worms")
@Data
public class IrisWorm {
    @Desc("The style used to determine the curvature of this worm's x")
    private IrisShapedGeneratorStyle xStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN, -2, 2);

    @Desc("The style used to determine the curvature of this worm's y")
    private IrisShapedGeneratorStyle yStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN, -2, 2);

    @Desc("The style used to determine the curvature of this worm's z")
    private IrisShapedGeneratorStyle zStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN, -2, 2);

    @Desc("The max block distance this worm can travel from its start. This can have performance implications at ranges over 1,000 blocks but it's not too serious, test.")
    private int maxDistance = 128;

    @Desc("The iterations this worm can make")
    private int maxIterations = 512;

    @Desc("By default if a worm loops back into itself, it stops at that point and does not continue. This is an optimization, to prevent this turn this option on.")
    private boolean allowLoops = false;

    @Desc("The thickness of the worms. Each individual worm has the same thickness while traveling however, each spawned worm will vary in thickness.")
    private IrisStyledRange girth = new IrisStyledRange().setMin(3).setMax(5)
            .setStyle(new IrisGeneratorStyle(NoiseStyle.PERLIN));

    public KList<IrisPosition> generate(RNG rng, IrisData data, MantleWriter writer, IrisRange verticalRange, int x, int y, int z, Consumer<IrisPosition> fork) {
        int itr = maxIterations;
        double jx, jy, jz;
        double cx = x;
        double cy = y;
        double cz = z;
        IrisPosition start = new IrisPosition(x, y, z);
        KList<IrisPosition> pos = new KList<>();
        KSet<IrisPosition> check = allowLoops ? null : new KSet<>();
        CNG gx = xStyle.getGenerator().createNoCache(new RNG(rng.lmax()), data);
        CNG gy = xStyle.getGenerator().createNoCache(new RNG(rng.lmax()), data);
        CNG gz = xStyle.getGenerator().createNoCache(new RNG(rng.lmax()), data);

        while (itr-- > 0) {
            IrisPosition current = new IrisPosition(Math.round(cx), Math.round(cy), Math.round(cz));
            fork.accept(current);
            pos.add(current);

            if (check != null) {
                check.add(current);
            }

            jx = gx.fitDouble(xStyle.getMin(), xStyle.getMax(), cx, cy, cz);
            jy = gy.fitDouble(yStyle.getMin(), yStyle.getMax(), cx, cy, cz);
            jz = gz.fitDouble(zStyle.getMin(), zStyle.getMax(), cx, cy, cz);
            cx += jx;
            cy += jy;
            cz += jz;
            IrisPosition next = new IrisPosition(Math.round(cx), Math.round(cy), Math.round(cz));

            if (verticalRange != null && !verticalRange.contains(next.getY())) {
                break;
            }

            if (!writer.isWithin((int) Math.round(cx), verticalRange != null ? (int) Math.round(cy) : 5, (int) Math.round(cz))) {
                break;
            }

            if (next.isLongerThan(start, maxDistance)) {
                break;
            }

            if (check != null && check.contains(next)) {
                break;
            }
        }

        return pos;
    }
}
