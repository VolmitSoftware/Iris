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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedActuator;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.util.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.iris.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.matter.MatterBiomeInject;
import art.arcane.iris.util.matter.slices.BiomeInjectMatter;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.bukkit.block.Biome;

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
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            for (int xf = 0; xf < h.getWidth(); xf++) {
                IrisBiome ib;
                for (int zf = 0; zf < h.getDepth(); zf++) {
                    ib = context.getBiome().get(xf, zf);
                    MatterBiomeInject matter;

                    if (ib.isCustom()) {
                        IrisBiomeCustom custom = ib.getCustomBiome(rng, x, 0, z);
                        matter = BiomeInjectMatter.get(INMS.get().getBiomeBaseIdForKey(getDimension().getLoadKey() + ":" + custom.getId()));
                    } else {
                        Biome v = ib.getSkyBiome(rng, x, 0, z);
                        matter = BiomeInjectMatter.get(v);
                    }

                    getEngine().getMantle().getMantle().set(x + xf, 0, z + zf, matter);
                }
            }
            getEngine().getMetrics().getBiome().put(p.getMilliseconds());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
