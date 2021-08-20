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

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.cave.IrisCave;
import com.volmit.iris.engine.object.cave.IrisCavePlacer;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructure;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructurePlacement;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Consumer;

public class MantleCaveComponent extends IrisMantleComponent {
    private final RNG crng;
    public MantleCaveComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.CAVE);
        crng = new RNG(getEngineMantle().getEngine().getWorld().seed() - 229333333);
    }

    @Override
    public void generateLayer(int x, int z, Consumer<Runnable> post) {
        RNG rng = new RNG(Cache.key(x, z) + seed());
        int xxx = (x << 4) + rng.i(16);
        int zzz = (z << 4) + rng.i(16);

        IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xxx, zzz);

        for(IrisCavePlacer i : biome.getCaves())
        {
            if(rng.nextInt(i.getRarity()) == 0)
            {
                place(i, xxx, getEngineMantle().trueHeight(xxx, zzz), zzz);
                return;
            }
        }

        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);

        for(IrisCavePlacer i : region.getCaves())
        {
            if(rng.nextInt(i.getRarity()) == 0)
            {
                place(i, xxx, getEngineMantle().trueHeight(xxx, zzz), zzz);
                return;
            }
        }

        for(IrisCavePlacer i : getDimension().getCaves())
        {
            if(rng.nextInt(i.getRarity()) == 0)
            {
                place(i, xxx, getEngineMantle().trueHeight(xxx, zzz), zzz);
                return;
            }
        }
    }

    private void place(IrisCavePlacer cave, int x, int y, int z) {
        cave.generateCave(getMantle(), crng, getData(), x, y, z);
    }
}
