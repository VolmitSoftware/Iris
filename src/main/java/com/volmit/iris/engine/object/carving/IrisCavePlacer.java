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
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.object.noise.IrisGeneratorStyle;
import com.volmit.iris.engine.object.noise.IrisStyledRange;
import com.volmit.iris.engine.object.noise.NoiseStyle;
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
    @Required
    @Desc("Typically a 1 in RARITY on a per chunk basis")
    @MinNumber(1)
    private int rarity = 15;

    @MinNumber(1)
    @Required
    @Desc("The cave to place")
    @RegistryListResource(IrisCave.class)
    private String cave;

    @Desc("If set to true, this cave is allowed to break the surface")
    private boolean breakSurface = true;

    @Desc("The height range this cave can spawn at. If breakSurface is false, the output of this range will be clamped by the current world height to prevent surface breaking.")
    private IrisStyledRange caveStartHeight = new IrisStyledRange(13, 120, new IrisGeneratorStyle(NoiseStyle.STATIC));

    private transient final AtomicCache<IrisCave> caveCache = new AtomicCache<>();
    private transient final AtomicBoolean fail = new AtomicBoolean(false);

    public IrisCave getRealCave(IrisData data) {
        return caveCache.aquire(() -> data.getCaveLoader().load(getCave()));
    }

    public void generateCave(MantleWriter mantle, RNG rng, Engine engine, int x, int z) {
        if (fail.get()) {
            return;
        }

        IrisData data = engine.getData();
        IrisCave cave = getRealCave(data);

        if (cave == null) {
            Iris.warn("Unable to locate cave for generation!");
            fail.set(true);
            return;
        }

        int h = (int) caveStartHeight.get(rng,x, z,data);
        int ma = (int) (engine.getComplex().getHeightStream().get(x, z) - 9);
        cave.generate(mantle, rng, engine, x, Math.min(h, ma), z);
    }

    public int getSize(IrisData data) {
        return getRealCave(data).getWorm().getMaxDistance();
    }
}
