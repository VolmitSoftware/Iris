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

package com.volmit.iris.engine.actuator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedActuator;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.view.BiomeGridHunkHolder;
import com.volmit.iris.util.hunk.view.BiomeGridHunkView;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterBiomeInject;
import com.volmit.iris.util.matter.slices.BiomeInjectMatter;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

public class IrisBiomeActuator extends EngineAssignedActuator<Biome> {
    private final RNG rng;
    private final ChronoLatch cl = new ChronoLatch(5000);

    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
        rng = new RNG(engine.getSeedManager().getBiome());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<Biome> h, boolean multicore, ChunkContext context) {
        try
        {
            PrecisionStopwatch p = PrecisionStopwatch.start();

            int m = 0;
            for(int xf = 0; xf < h.getWidth(); xf++) {
                IrisBiome ib;
                for(int zf = 0; zf < h.getDepth(); zf++) {
                    ib = context.getBiome().get(xf, zf);
                    int maxHeight = (int) (getComplex().getFluidHeight() + ib.getMaxWithObjectHeight(getData()));
                    MatterBiomeInject matter = null;

                    if(ib.isCustom()) {
                        IrisBiomeCustom custom = ib.getCustomBiome(rng, x, 0, z);
                        matter = BiomeInjectMatter.get(INMS.get().getBiomeBaseIdForKey(getDimension().getLoadKey() + ":" + custom.getId()));
                    } else {
                        Biome v = ib.getSkyBiome(rng, x, 0, z);
                        matter = BiomeInjectMatter.get(v);
                    }

                    for(int i = 0; i < maxHeight; i++) {
                        getEngine().getMantle().getMantle().set(x, i, z, matter);
                        m++;
                    }

                }
            }

            getEngine().getMetrics().getBiome().put(p.getMilliseconds());
            Iris.info("Biome Actuator: " + p.getMilliseconds() + "ms");
            Iris.info("Mantle: " + m + " blocks");
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }
    }
}
