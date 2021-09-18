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

package com.volmit.iris.engine.actuator;

import com.volmit.iris.engine.decorator.IrisCeilingDecorator;
import com.volmit.iris.engine.decorator.IrisSeaFloorDecorator;
import com.volmit.iris.engine.decorator.IrisSeaSurfaceDecorator;
import com.volmit.iris.engine.decorator.IrisShoreLineDecorator;
import com.volmit.iris.engine.decorator.IrisSurfaceDecorator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedActuator;
import com.volmit.iris.engine.framework.EngineDecorator;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<BlockData> {
    private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);
    private final RNG rng;
    @Getter
    private final EngineDecorator surfaceDecorator;
    @Getter
    private final EngineDecorator ceilingDecorator;
    @Getter
    private final EngineDecorator seaSurfaceDecorator;
    @Getter
    private final EngineDecorator seaFloorDecorator;
    @Getter
    private final EngineDecorator shoreLineDecorator;
    private final boolean shouldRay;

    public IrisDecorantActuator(Engine engine) {
        super(engine, "Decorant");
        shouldRay = shouldRayDecorate();
        this.rng = new RNG(engine.getSeedManager().getDecorator());
        surfaceDecorator = new IrisSurfaceDecorator(getEngine());
        ceilingDecorator = new IrisCeilingDecorator(getEngine());
        seaSurfaceDecorator = new IrisSeaSurfaceDecorator(getEngine());
        shoreLineDecorator = new IrisShoreLineDecorator(getEngine());
        seaFloorDecorator = new IrisSeaFloorDecorator(getEngine());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<BlockData> output, boolean multicore) {
        if (!getEngine().getDimension().isDecorate()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        BurstExecutor burst = burst().burst(multicore);

        for (int i = 0; i < output.getWidth(); i++) {
            int finalI = i;
            burst.queue(() -> {
                int height;
                int realX = Math.round(x + finalI);
                int realZ;
                IrisBiome biome, cave;
                for (int j = 0; j < output.getDepth(); j++) {
                    boolean solid;
                    int emptyFor = 0;
                    int lastSolid = 0;
                    realZ = Math.round(z + j);
                    height = (int) Math.round(getComplex().getHeightStream().get(realX, realZ));
                    biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                    cave = shouldRay ? getComplex().getCaveBiomeStream().get(realX, realZ) : null;

                    if (biome.getDecorators().isEmpty() && (cave == null || cave.getDecorators().isEmpty())) {
                        continue;
                    }

                    if (height < getDimension().getFluidHeight()) {
                        getSeaSurfaceDecorator().decorate(finalI, j,
                                realX, Math.round(+finalI + 1), Math.round(x + finalI - 1),
                                realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                                output, biome, getDimension().getFluidHeight(), getEngine().getHeight());
                        getSeaFloorDecorator().decorate(finalI, j,
                                realX, realZ, output, biome, height + 1,
                                getDimension().getFluidHeight() + 1);
                    }

                    if (height == getDimension().getFluidHeight()) {
                        getShoreLineDecorator().decorate(finalI, j,
                                realX, Math.round(x + finalI + 1), Math.round(x + finalI - 1),
                                realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                                output, biome, height, getEngine().getHeight());
                    }

                    getSurfaceDecorator().decorate(finalI, j, realX, realZ, output, biome, height, getEngine().getHeight() - height);


                    if (cave != null && cave.getDecorators().isNotEmpty()) {
                        for (int k = height; k > 0; k--) {
                            solid = PREDICATE_SOLID.test(output.get(finalI, k, j));

                            if (solid) {
                                if (emptyFor > 0) {
                                    getSurfaceDecorator().decorate(finalI, j, realX, realZ, output, cave, k, lastSolid);
                                    getCeilingDecorator().decorate(finalI, j, realX, realZ, output, cave, lastSolid - 1, emptyFor);
                                    emptyFor = 0;
                                }
                                lastSolid = k;
                            } else {
                                emptyFor++;
                            }
                        }
                    }
                }
            });
        }

        burst.complete();
        getEngine().getMetrics().getDecoration().put(p.getMilliseconds());

    }

    private boolean shouldRayDecorate() {
        return false; // TODO CAVES
    }
}
