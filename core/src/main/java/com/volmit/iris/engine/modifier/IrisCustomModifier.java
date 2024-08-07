/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.block.data.BlockData;

public class IrisCustomModifier extends EngineAssignedModifier<BlockData> {
    public IrisCustomModifier(Engine engine) {
        super(engine, "Custom");
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        var mc = getEngine().getMantle().getMantle().getChunk(x >> 4, z >> 4);
        if (!mc.isFlagged(MantleFlag.CUSTOM_ACTIVE)) return;

        BurstExecutor burst = MultiBurst.burst.burst(output.getHeight());
        burst.setMulticore(multicore);
        for (int y = 0; y < output.getHeight(); y++) {
            int finalY = y;
            burst.queue(() -> {
                for (int rX = 0; rX < output.getWidth(); rX++) {
                    for (int rZ = 0; rZ < output.getDepth(); rZ++) {
                        BlockData b = output.get(rX, finalY, rZ);
                        if (!(b instanceof IrisCustomData d)) continue;

                        mc.getOrCreate(finalY >> 4)
                                .slice(Identifier.class)
                                .set(rX, finalY & 15, rZ, d.getCustom());
                        output.set(rX, finalY, rZ, d.getBase());
                    }
                }
            });
        }
        burst.complete();
    }
}
