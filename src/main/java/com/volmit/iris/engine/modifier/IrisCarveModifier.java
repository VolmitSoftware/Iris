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

package com.volmit.iris.engine.modifier;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.deposits.IrisDepositGenerator;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.HeightMap;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.matter.slices.CavernMatter;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.util.Set;

public class IrisCarveModifier extends EngineAssignedModifier<BlockData> {
    private final RNG rng;
    private final BlockData AIR = Material.CAVE_AIR.createBlockData();

    public IrisCarveModifier(Engine engine) {
        super(engine, "Carve");
        rng = new RNG(getEngine().getWorld().seed() + 3297778).nextParallelRNG(67648777);
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        Mantle mantle = getEngine().getMantle().getMantle();
        MantleChunk mc = getEngine().getMantle().getMantle().getChunk(x, z);

        mc.iterate(MatterCavern.class, (xx, yy, zz, c) -> {
            int rx = xx & 15;
            int rz = zz & 15;
            BlockData current = output.get(rx, yy, rz);

            if(current.getMaterial().isAir())
            {
                return;
            }

            if(B.isFluid(current))
            {
                return;
            }

            output.set(rx, yy, rz, AIR);
        });
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }
}
