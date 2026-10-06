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

import art.arcane.iris.engine.decorator.IrisCeilingDecorator;
import art.arcane.iris.engine.decorator.IrisSeaFloorDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import art.arcane.iris.engine.decorator.IrisShoreLineDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedActuator;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.Getter;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<PlatformBlockState> {
    private static final Predicate<PlatformBlockState> PREDICATE_SOLID = (s) -> s != null && !B.isAirOrFluid(s);
    private final RNG rng;
    @Getter
    private final IrisSurfaceDecorator surfaceDecorator;
    @Getter
    private final IrisCeilingDecorator ceilingDecorator;
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
    public void onActuate(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        if (!getEngine().getDimension().isDecorate()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();

        for (int i = 0; i < output.getWidth(); i++) {
            int height;
            int realX = Math.round(x + i);
            int realZ;
            IrisBiome biome, cave;
            for (int j = 0; j < output.getDepth(); j++) {
                boolean solid;
                int emptyFor = 0;
                int lastSolid = 0;
                realZ = Math.round(z + j);
                height = context.getRoundedHeight(i, j);
                biome = context.getBiome().get(i, j);
                cave = shouldRay ? context.getCave().get(i, j) : null;

                if (biome.getDecorators().isEmpty() && (cave == null || cave.getDecorators().isEmpty())) {
                    continue;
                }

                if (height < getDimension().getFluidHeight() && PREDICATE_SOLID.test(output.get(i, height, j))
                        && height + 1 < output.getHeight() && B.isWater(output.get(i, height + 1, j))) {
                    getSeaSurfaceDecorator().decorate(i, j,
                            realX, Math.round(i + 1), Math.round(x + i - 1),
                            realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                            output, biome, getDimension().getFluidHeight(), getEngine().getHeight());
                    getSeaFloorDecorator().decorate(i, j,
                            realX, realZ, output, biome, height + 1,
                            getDimension().getFluidHeight() + 1);
                }

                if (height == getDimension().getFluidHeight()) {
                    getShoreLineDecorator().decorate(i, j,
                            realX, Math.round(x + i + 1), Math.round(x + i - 1),
                            realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                            output, biome, height, getEngine().getHeight());
                }

                if (height >= 0 && height < output.getHeight()) {
                    getSurfaceDecorator().decorate(i, j, realX, realZ, output, biome, height, getEngine().getHeight() - height);
                }


                if (cave != null && cave.getDecorators().isNotEmpty()) {
                    for (int k = Math.min(height, output.getHeight() - 1); k > 0; k--) {
                        solid = PREDICATE_SOLID.test(output.get(i, k, j));

                        if (solid) {
                            if (emptyFor > 0) {
                                getSurfaceDecorator().decorate(i, j, realX, realZ, output, cave, k, lastSolid);
                                getCeilingDecorator().decorate(i, j, realX, realZ, output, cave, lastSolid - 1, emptyFor);
                                emptyFor = 0;
                            }
                            lastSolid = k;
                        } else {
                            emptyFor++;
                        }
                    }
                }
            }
        }

        getEngine().getMetrics().getDecoration().put(p.getMilliseconds());

    }

    private boolean shouldRayDecorate() {
        return false; // TODO CAVES
    }
}
