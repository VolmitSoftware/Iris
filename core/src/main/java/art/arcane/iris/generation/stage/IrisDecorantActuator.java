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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.decoration.IrisCeilingDecorator;
import art.arcane.iris.generation.decoration.IrisSeaFloorDecorator;
import art.arcane.iris.generation.decoration.IrisSeaSurfaceDecorator;
import art.arcane.iris.generation.decoration.IrisShoreLineDecorator;
import art.arcane.iris.generation.decoration.IrisSurfaceDecorator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedActuator;
import art.arcane.iris.generation.runtime.EngineDecorator;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.hunk.Hunk;
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
    private final IrisShoreLineDecorator shoreLineDecorator;
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

    static ShorelineDecorationMode shorelineDecorationMode(
            HydrologyColumnSample sample,
            int height,
            int fluidHeight
    ) {
        HydrologyColumnLayer surfaceLayer = sample == null
                ? null
                : sample.primarySurfaceLayer().orElse(null);
        if (surfaceLayer != null) {
            return surfaceLayer.shore()
                    ? ShorelineDecorationMode.ACCEPTED
                    : ShorelineDecorationMode.NONE;
        }
        return height == fluidHeight
                ? ShorelineDecorationMode.LEGACY
                : ShorelineDecorationMode.NONE;
    }

    static boolean hasConnectedSurfaceWater(
            Hunk<PlatformBlockState> output,
            int x,
            int z,
            int height,
            int fluidHeight
    ) {
        return height < fluidHeight
                && height + 1 < output.getHeight()
                && IrisSurfaceDecorator.hasConnectedWater(output, x, height + 1, z);
    }

    static boolean hasConnectedWaterColumn(
            Hunk<PlatformBlockState> output,
            int x,
            int z,
            int lowerY,
            int upperY
    ) {
        int boundedLowerY = Math.max(0, lowerY);
        int boundedUpperY = Math.min(output.getHeight() - 1, upperY);
        if (boundedLowerY > boundedUpperY) {
            return false;
        }
        for (int y = boundedLowerY; y <= boundedUpperY; y++) {
            if (!IrisSurfaceDecorator.hasConnectedWater(output, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    static void restoreUnsupportedAquaticPlacement(
            Hunk<PlatformBlockState> output,
            int x,
            int y,
            int z,
            PlatformBlockState original
    ) {
        PlatformBlockState placed = output.get(x, y, z);
        if (placed != original && IrisSurfaceDecorator.isAquaticPlacement(placed)) {
            output.set(x, y, z, original);
        }
    }

    static boolean isFallingWaterfallThroat(HydrologyColumnSample sample) {
        if (sample == null) {
            return false;
        }
        for (HydrologyColumnLayer layer : sample.layers()) {
            if ((layer.feature().type() == HydrologyFeatureType.WATERFALL
                    || layer.feature().type() == HydrologyFeatureType.SINKHOLE)
                    && layer.channel()
                    && layer.fallingFluid()) {
                return true;
            }
        }
        return false;
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
                if (!getComplex().allowsNewDiscreteContentAt(realX, realZ)) {
                    continue;
                }
                height = context.getRoundedHeight(i, j);
                biome = context.getBiome().get(i, j);
                cave = shouldRay ? context.getCave().get(i, j) : null;
                HydrologyColumnSample hydrology = getComplex().sampleHydrologyColumn(realX, realZ);
                if (isFallingWaterfallThroat(hydrology)) {
                    continue;
                }
                int surfaceFluidHeight = (int) Math.round(
                        getComplex().getRiverWaterSurfaceStream().get(realX, realZ));

                if (biome.getDecorators().isEmpty() && (cave == null || cave.getDecorators().isEmpty())) {
                    continue;
                }

                if (PREDICATE_SOLID.test(output.get(i, height, j))
                        && hasConnectedSurfaceWater(output, i, j, height, surfaceFluidHeight)) {
                    int seaSurfaceY = surfaceFluidHeight + 1;
                    PlatformBlockState seaSurfaceOriginal = seaSurfaceY < output.getHeight()
                            ? output.get(i, seaSurfaceY, j)
                            : null;
                    getSeaSurfaceDecorator().decorate(i, j,
                            realX, Math.round(i + 1), Math.round(x + i - 1),
                            realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                            output, biome, surfaceFluidHeight, getEngine().getHeight());
                    if (seaSurfaceY < output.getHeight()) {
                        restoreUnsupportedAquaticPlacement(
                                output, i, seaSurfaceY, j, seaSurfaceOriginal);
                    }
                    if (hasConnectedWaterColumn(output, i, j, height + 1, surfaceFluidHeight)) {
                        getSeaFloorDecorator().decorate(i, j,
                                realX, realZ, output, biome, height + 1,
                                surfaceFluidHeight + 1);
                    }
                }

                ShorelineDecorationMode shorelineMode = shorelineDecorationMode(
                        hydrology,
                        height,
                        surfaceFluidHeight
                );
                if (shorelineMode == ShorelineDecorationMode.ACCEPTED) {
                    getShoreLineDecorator().decorateAcceptedShore(
                            i,
                            j,
                            realX,
                            realZ,
                            output,
                            biome,
                            height,
                            getEngine().getHeight()
                    );
                } else if (shorelineMode == ShorelineDecorationMode.LEGACY) {
                    getShoreLineDecorator().decorate(i, j,
                            realX, Math.round(x + i + 1), Math.round(x + i - 1),
                            realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                            output, biome, height, getEngine().getHeight());
                }

                if (height >= 0 && height < output.getHeight()) {
                    getSurfaceDecorator().decorate(i, j, realX, realZ, output, biome, height, getEngine().getHeight() - height);
                }

                Terrain3DColumn terrainColumn = getComplex().terrainColumn(realX, realZ, hydrology);
                if (terrainColumn != null) {
                    decorateTerrainLedges(output, terrainColumn, biome, i, j, realX, realZ);
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

    private void decorateTerrainLedges(Hunk<PlatformBlockState> output, Terrain3DColumn column,
                                       IrisBiome biome, int localX, int localZ, int worldX, int worldZ) {
        for (int span = 0; span + 1 < column.spanCount(); span++) {
            int floorY = column.floor(span);
            int ceilingY = column.ceiling(span + 1);
            int headroom = ceilingY - floorY - 1;
            if (headroom < 1 || !PREDICATE_SOLID.test(output.getRaw(localX, floorY, localZ))) {
                continue;
            }
            getSurfaceDecorator().decorate(localX, localZ, worldX, worldZ,
                    output, biome, floorY, headroom);
            if (PREDICATE_SOLID.test(output.getRaw(localX, ceilingY, localZ))) {
                getCeilingDecorator().decorate(localX, localZ, worldX, worldZ,
                        output, biome, ceilingY - 1, headroom);
            }
        }
    }

    enum ShorelineDecorationMode {
        NONE,
        LEGACY,
        ACCEPTED
    }
}
