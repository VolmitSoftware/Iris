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

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.concurrent.atomic.AtomicBoolean;

@Snippet("ravine-placer")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisRavinePlacer implements IRare {
    private transient final AtomicCache<IrisRavine> ravineCache = new AtomicCache<>();
    private transient final AtomicBoolean fail = new AtomicBoolean(false);
    @Required
    @Desc("Typically a 1 in RARITY on a per chunk/fork basis")
    @MinNumber(1)
    private int rarity = 15;
    @MinNumber(1)
    @Required
    @Desc("The ravine to place")
    @RegistryListResource(IrisRavine.class)
    private String ravine;

    public IrisRavine getRealRavine(IrisData data) {
        return ravineCache.aquire(() -> data.getRavineLoader().load(getRavine()));
    }

    public void generateRavine(MantleWriter mantle, RNG rng, Engine engine, int x, int y, int z) {
        generateRavine(mantle, rng, engine, x, y, z, -1);
    }

    public void generateRavine(MantleWriter mantle, RNG rng, Engine engine, int x, int y, int z, int waterHint) {
        if (fail.get()) {
            return;
        }

        if (rng.nextInt(rarity) != 0) {
            return;
        }

        IrisData data = engine.getData();
        IrisRavine ravine = getRealRavine(data);

        if (ravine == null) {
            Iris.warn("Unable to locate ravine for generation!");
            fail.set(true);
            return;
        }

        try {
            int xx = x + rng.nextInt(15);
            int zz = z + rng.nextInt(15);
            ravine.generate(mantle, rng, engine, xx, y, zz, waterHint);
        } catch (Throwable e) {
            e.printStackTrace();
            fail.set(true);
        }
    }

    public int getSize(IrisData data) {
        return getRealRavine(data).getMaxSize(data);
    }
}
