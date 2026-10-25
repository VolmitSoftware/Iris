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

import art.arcane.iris.generation.decoration.HydrologyVegetation;
import art.arcane.iris.generation.decoration.IrisCeilingDecorator;
import art.arcane.iris.generation.decoration.IrisSeaFloorDecorator;
import art.arcane.iris.generation.decoration.IrisSeaSurfaceDecorator;
import art.arcane.iris.generation.decoration.IrisShoreLineDecorator;
import art.arcane.iris.generation.decoration.IrisSurfaceDecorator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedActuator;
import art.arcane.iris.generation.runtime.EngineDecorator;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import lombok.Getter;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<NativeBlockState> {
    private static final Predicate<NativeBlockState> PREDICATE_SOLID = (s) -> s != null && !B.isAirOrFluid(s);
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
    @Getter
    private final HydrologyVegetation hydrologyVegetation;
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
        hydrologyVegetation = new HydrologyVegetation(engine.getSeedManager().getDecorator(), engine);
    }

    static ShorelineDecorationMode shorelineDecorationMode(WaterRole role, int height, int fluidHeight) {
        return switch (role) {
            case NONE -> height == fluidHeight ? ShorelineDecorationMode.LEGACY : ShorelineDecorationMode.NONE;
            case SHORE, POND_SHORE -> ShorelineDecorationMode.ACCEPTED;
            case CHANNEL, BANK, POND, DAM, MOUTH, CASCADE, WATERFALL, INLET -> ShorelineDecorationMode.NONE;
        };
    }

    static boolean skipsDecoration(WaterRole role) {
        return switch (role) {
            case CHANNEL, POND, CASCADE, WATERFALL -> true;
            case NONE, BANK, SHORE, POND_SHORE, DAM, MOUTH, INLET -> false;
        };
    }

    static boolean hasConnectedSurfaceWater(
            Hunk<NativeBlockState> output,
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
            Hunk<NativeBlockState> output,
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
            Hunk<NativeBlockState> output,
            int x,
            int y,
            int z,
            NativeBlockState original
    ) {
        NativeBlockState placed = output.get(x, y, z);
        if (placed != original && IrisSurfaceDecorator.isAquaticPlacement(placed)) {
            output.set(x, y, z, original);
        }
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<NativeBlockState> output, boolean multicore, ChunkContext context) {
        if (!getEngine().getDimension().isDecorate()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        HydrologyVegetation.Water water = new ComplexWater(getComplex());
        for (int i = 0; i < output.getWidth(); i++) {
            int height;
            int realX = Math.round(x + i);
            int realZ;
            IrisBiome biome, cave;
            for (int j = 0; j < output.getDepth(); j++) {
                realZ = Math.round(z + j);
                if (!getComplex().allowsNewDiscreteContentAt(realX, realZ)) {
                    continue;
                }
                height = context.getRoundedHeight(i, j);
                biome = context.getBiome().get(i, j);
                cave = shouldRay ? context.getCave().get(i, j) : null;
                WaterRole role = getComplex().getWaterRoleStream().get(realX, realZ);
                ChunkContext.TransitionTerrainColumn transitionTerrain = context.getTransitionTerrain(i, j);
                int surfaceFluidHeight = transitionTerrain == null ? (int) Math.round(
                        getComplex().getWaterHeadStream().get(realX, realZ)) : transitionTerrain.fluidHeight();

                if (role != WaterRole.NONE) {
                    getHydrologyVegetation().decorate(output, i, j, realX, realZ, role, water, getData());
                }

                if (biome.getDecorators().isEmpty() && (cave == null || cave.getDecorators().isEmpty())) {
                    continue;
                }

                if (!skipsDecoration(role)) {
                    if (PREDICATE_SOLID.test(output.get(i, height, j))
                            && hasConnectedSurfaceWater(output, i, j, height, surfaceFluidHeight)) {
                        int seaSurfaceY = surfaceFluidHeight + 1;
                        NativeBlockState seaSurfaceOriginal = seaSurfaceY < output.getHeight()
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

                    ShorelineDecorationMode shorelineMode = shorelineDecorationMode(role, height, surfaceFluidHeight);
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

                    Terrain3DColumn terrainColumn = getComplex().terrainColumn(realX, realZ);
                    if (terrainColumn != null) {
                        decorateTerrainLedges(output, terrainColumn, transitionTerrain, biome, i, j, realX, realZ);
                    }
                }

                if (cave != null && cave.getDecorators().isNotEmpty()) {
                    decorateCaveColumn(output, cave, i, j, realX, realZ, height);
                }
            }
        }

        getEngine().getMetrics().getDecoration().put(p.getMilliseconds());

    }

    void decorateCaveColumn(Hunk<NativeBlockState> output, IrisBiome cave, int localX, int localZ, int worldX, int worldZ, int height) {
        int emptyFor = 0;
        int lastSolid = 0;
        for (int k = Math.min(height, output.getHeight() - 1); k > 0; k--) {
            if (PREDICATE_SOLID.test(output.get(localX, k, localZ))) {
                if (emptyFor > 0) {
                    getSurfaceDecorator().decorate(localX, localZ, worldX, worldZ, output, cave, k, lastSolid);
                    getCeilingDecorator().decorate(localX, localZ, worldX, worldZ, output, cave, lastSolid - 1, emptyFor);
                    emptyFor = 0;
                }
                lastSolid = k;
            } else {
                emptyFor++;
            }
        }
    }

    private boolean shouldRayDecorate() {
        return false; // TODO CAVES
    }

    private void decorateTerrainLedges(Hunk<NativeBlockState> output, Terrain3DColumn column,
                                       ChunkContext.TransitionTerrainColumn transition,
                                       IrisBiome biome, int localX, int localZ, int worldX, int worldZ) {
        Terrain3DColumn ledges = transition == null ? column : transition.solids();
        for (int span = 0; span + 1 < ledges.spanCount(); span++) {
            int floorY = ledges.floor(span);
            int ceilingY = ledges.ceiling(span + 1);
            if (transition != null && resolveTerrainLedge(column, span, transition) == null) {
                continue;
            }
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

    static TerrainLedge resolveTerrainLedge(Terrain3DColumn natural, int span,
                                            ChunkContext.TransitionTerrainColumn transition) {
        if (transition == null) {
            return new TerrainLedge(natural.floor(span), natural.ceiling(span + 1));
        }
        Terrain3DColumn blended = transition.solids();
        int blendedFloor = blended.floor(span);
        int blendedCeiling = blended.ceiling(span + 1);
        for (int candidate = 0; candidate + 1 < natural.spanCount(); candidate++) {
            if (Math.max(natural.floor(candidate), blendedFloor) + 1
                    < Math.min(natural.ceiling(candidate + 1), blendedCeiling)) {
                return new TerrainLedge(blendedFloor, blendedCeiling);
            }
        }
        return null;
    }

    static boolean isTransitionTerrainOpening(Terrain3DColumn natural,
                                               ChunkContext.TransitionTerrainColumn transition, int y) {
        Terrain3DColumn blended = transition.solids();
        for (int span = 0; span + 1 < blended.spanCount(); span++) {
            if (y > blended.floor(span) && y < blended.ceiling(span + 1)) {
                return resolveTerrainLedge(natural, span, transition) != null;
            }
        }
        return false;
    }

    record TerrainLedge(int floorY, int ceilingY) {
    }

    private record ComplexWater(IrisComplex complex) implements HydrologyVegetation.Water {
        @Override
        public int ground(int x, int z) {
            return (int) Math.round(complex.getHeightStream().getDouble(x, z));
        }

        @Override
        public int head(int x, int z) {
            return (int) Math.round(complex.getWaterHeadStream().getDouble(x, z));
        }

        @Override
        public double slope(int x, int z) {
            return complex.getSlopeStream().getDouble(x, z);
        }

        @Override
        public HydrologyVegetation.Decorators decorators(int x, int z) {
            return complex.resolveWaterDecorators(x, z);
        }
    }

    enum ShorelineDecorationMode {
        NONE,
        LEGACY,
        ACCEPTED
    }
}
