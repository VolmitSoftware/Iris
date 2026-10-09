/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.decorator.IrisSeaFloorDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import art.arcane.iris.engine.decorator.IrisShoreLineDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.PackWorld;

import java.util.function.Predicate;

/**
 * Module {@code iris:decorant}: the biomes' decorators, placed on the surface the mantle's blocks left: the
 * surface itself, the sea surface and floor, and the shore line. A dimension turns decorating off with
 * {@code decorate}.
 */
public final class DecorantModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:decorant", "Decorators", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Decorant.DECORATE).requires(IrisStages.Matter.INSERT).run(DecorantModule::decorate);
    }

    private static void decorate(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        decorating(context.world()).decorate(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.context());
    }

    /** The world's decorating, kept in its state store. */
    static Decorating decorating(PackWorld world) {
        return world.state(Decorating.class, Decorating::new);
    }

    /** What decorating keeps for one world: one decorator per kind of place. */
    static final class Decorating {
        private static final Predicate<PlatformBlockState> PREDICATE_SOLID = (s) -> s != null && !B.isAirOrFluid(s);

        private final Engine engine;
        private final IrisSurfaceDecorator surfaceDecorator;
        private final EngineDecorator seaSurfaceDecorator;
        private final EngineDecorator seaFloorDecorator;
        private final EngineDecorator shoreLineDecorator;

        Decorating(PackWorld world) {
            this(IrisPackWorld.of(world).engine());
        }

        Decorating(Engine engine) {
            this.engine = engine;
            surfaceDecorator = new IrisSurfaceDecorator(engine);
            seaSurfaceDecorator = new IrisSeaSurfaceDecorator(engine);
            shoreLineDecorator = new IrisShoreLineDecorator(engine);
            seaFloorDecorator = new IrisSeaFloorDecorator(engine);
        }

        /** Decorates the 16 x 16 columns of the chunk whose first block is {@code x}, {@code z}, a biome's decorators on the surface its blocks left. */
        @BlockCoordinates
        void decorate(int x, int z, Hunk<PlatformBlockState> output, ChunkContext context) {
            if (!engine.getDimension().isDecorate()) {
                return;
            }

            PrecisionStopwatch p = PrecisionStopwatch.start();

            for (int i = 0; i < output.getWidth(); i++) {
                int height;
                int realX = Math.round(x + i);
                int realZ;
                IrisBiome biome;
                for (int j = 0; j < output.getDepth(); j++) {
                    realZ = Math.round(z + j);
                    height = context.getRoundedHeight(i, j);
                    biome = context.getBiome().get(i, j);

                    if (biome.getDecorators().isEmpty()) {
                        continue;
                    }

                    if (height < engine.getDimension().getFluidHeight() && PREDICATE_SOLID.test(output.get(i, height, j))
                            && height + 1 < output.getHeight() && B.isWater(output.get(i, height + 1, j))) {
                        seaSurfaceDecorator.decorate(i, j,
                                realX, Math.round(i + 1), Math.round(x + i - 1),
                                realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                                output, biome, engine.getDimension().getFluidHeight(), engine.getHeight());
                        seaFloorDecorator.decorate(i, j,
                                realX, realZ, output, biome, height + 1,
                                engine.getDimension().getFluidHeight() + 1);
                    }

                    if (height == engine.getDimension().getFluidHeight()) {
                        shoreLineDecorator.decorate(i, j,
                                realX, Math.round(x + i + 1), Math.round(x + i - 1),
                                realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                                output, biome, height, engine.getHeight());
                    }

                    if (height >= 0 && height < output.getHeight()) {
                        surfaceDecorator.decorate(i, j, realX, realZ, output, biome, height, engine.getHeight() - height);
                    }
                }
            }

            engine.getMetrics().getDecoration().put(p.getMilliseconds());
        }
    }
}
