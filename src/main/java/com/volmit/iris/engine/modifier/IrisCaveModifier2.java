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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.engine.object.common.CaveResult;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.FastNoiseDouble;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.block.data.BlockData;

public class IrisCaveModifier2 extends EngineAssignedModifier<BlockData> {
    public static final BlockData CAVE_AIR = B.get("CAVE_AIR");
    public static final BlockData AIR = B.get("AIR");
    private static final KList<CaveResult> EMPTY = new KList<>();
    private final FastNoiseDouble gg;
    private final RNG rng;

    public IrisCaveModifier2(Engine engine) {
        super(engine, "Cave");
        rng = new RNG(engine.getWorld().seed() + 28934555);
        gg = new FastNoiseDouble(324895L * rng.nextParallelRNG(49678).imax());
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> a, boolean multicore) {
        if (!getDimension().isCaves()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (multicore) {
            BurstExecutor e = getEngine().burst().burst(a.getWidth());
            for (int i = 0; i < a.getWidth(); i++) {
                int finalI = i;
                e.queue(() -> modifySliver(x, z, finalI, a));
            }

            e.complete();
        } else {
            for (int i = 0; i < a.getWidth(); i++) {
                modifySliver(x, z, i, a);
            }
        }

        getEngine().getMetrics().getCave().put(p.getMilliseconds());
    }

    public void modifySliver(int x, int z, int finalI, Hunk<BlockData> a) {
        for (int j = 0; j < a.getDepth(); j++) {

        }
    }
}
