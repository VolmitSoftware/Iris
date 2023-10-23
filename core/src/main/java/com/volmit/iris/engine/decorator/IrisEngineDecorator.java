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

package com.volmit.iris.engine.decorator;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedComponent;
import com.volmit.iris.engine.framework.EngineDecorator;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDecorationPart;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.Getter;

public abstract class IrisEngineDecorator extends EngineAssignedComponent implements EngineDecorator {

    @Getter
    private final RNG rng;

    @Getter
    private final IrisDecorationPart part;

    public IrisEngineDecorator(Engine engine, String name, IrisDecorationPart part) {
        super(engine, name + " Decorator");
        this.part = part;
        this.rng = new RNG(getSeed() + 29356788 - (part.ordinal() * 10439677L));
    }

    protected IrisDecorator getDecorator(IrisBiome biome, double realX, double realZ) {
        KList<IrisDecorator> v = new KList<>();
        RNG rng = new RNG(Cache.key((int) realX, (int) realZ));

        for (IrisDecorator i : biome.getDecorators()) {
            try {
                if (i.getPartOf().equals(part) && i.getBlockData(biome, this.rng, realX, realZ, getData()) != null) {
                    v.add(i);
                }
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("PART OF: " + biome.getLoadFile().getAbsolutePath() + " HAS AN INVALID DECORATOR near 'partOf'!!!");
            }
        }

        if (v.isNotEmpty()) {
            return v.get(rng.nextInt(v.size()));
        }

        return null;
    }
}
