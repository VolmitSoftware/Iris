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
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.basic.IrisRange;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.object.noise.IrisGeneratorStyle;
import com.volmit.iris.engine.object.noise.IrisNoiseGenerator;
import com.volmit.iris.engine.object.noise.IrisStyledRange;
import com.volmit.iris.engine.object.noise.NoiseStyle;
import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.Worm;
import com.volmit.iris.util.noise.WormIterator2;
import com.volmit.iris.util.noise.WormIterator3;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Generate worms")
@Data
public class IrisWormGenerator implements IRare {
    @Required
    @Desc("Typically a 1 in RARITY on a per chunk basis")
    @MinNumber(1)
    private int rarity = 15;

    @Desc("The style used to determine the curvature of this worm")
    private IrisGeneratorStyle angleStyle = new IrisGeneratorStyle();

    @Desc("The max block distance this worm can travel from its start. This can have performance implications at ranges over 1,000 blocks but it's not too serious, test.")
    private int maxDistance = 128;

    @Desc("The max segments, or iterations this worm can execute on. Setting this to -1 will allow it to run up to the maxDistance's value of iterations (default)")
    private int maxSegments = -1;

    @Desc("The thickness of the worm over distance")
    private IrisStyledRange girth = new IrisStyledRange().setMin(3).setMax(7)
            .setStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));

    private transient final AtomicCache<NoiseProvider> angleProviderCache = new AtomicCache<>();

    public void test()
    {

    }

    public NoiseProvider getAngleProvider(RNG rng, IrisData data)
    {
        return angleProviderCache.aquire(() -> (xx, zz) -> angleStyle.create(rng, data).noise(xx, zz));
    }

    public WormIterator2 iterate2D(RNG rng, IrisData data, int x, int z)
    {
        return WormIterator2.builder()
                .maxDistance(maxDistance)
                .maxIterations(maxSegments == -1 ? maxDistance : maxSegments)
                .noise(getAngleProvider(rng, data)).x(x).z(z)
                .build();
    }

    public WormIterator3 iterate3D(RNG rng, IrisData data, int x, int y, int z)
    {
        return WormIterator3.builder()
                .maxDistance(maxDistance)
                .maxIterations(maxSegments == -1 ? maxDistance : maxSegments)
                .noise(getAngleProvider(rng, data)).x(x).z(z).y(y)
                .build();
    }
}
