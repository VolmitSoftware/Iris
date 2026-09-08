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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisSurfaceDecorator extends IrisEngineDecorator {
    private final RNG partRNG;

    public IrisSurfaceDecorator(Engine engine) {
        super(engine, "Surface", IrisDecorationPart.NONE);
        this.partRNG = new RNG(DecoratorCore.partSeed(getSeed(), IrisDecorationPart.NONE));
    }

    protected IrisSurfaceDecorator(Engine engine, String name) {
        super(engine, name, IrisDecorationPart.NONE);
        this.partRNG = new RNG(DecoratorCore.partSeed(getSeed(), IrisDecorationPart.NONE));
    }

    protected boolean isSlopeValid(IrisDecorator decorator, int realX, int height, int realZ) {
        if (decorator.isForcePlace() || decorator.getSlopeCondition().isDefault()) {
            return true;
        }
        double slope = getComplex().hasTerrain3D()
                ? getComplex().terrainSurfaceSlope(realX, height, realZ)
                : getComplex().getSlopeStream().getDouble(realX, realZ);
        return decorator.getSlopeCondition().isValid(slope);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, int height, int max) {
        decorate(x, z, realX, realX1, realX_1, realZ, realZ1, realZ_1, data, biome, biome.getInferredType(), height, max);
    }

    @BlockCoordinates
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, InferredType inferredType, int height, int max) {
        int fluidHeight = getEngine().getMantle().getFluidHeight(realX, realZ);
        if (inferredType == InferredType.SHORE && height < fluidHeight) {
            return;
        }

        boolean underwater = isUnderwater(inferredType, height, fluidHeight);
        if (underwater && !hasFluidAbove(data, x, height, z)) {
            return;
        }
        boolean caveSkipFluid = skipsFluid(inferredType);
        RNG rng = getRNG(realX, realZ);
        IrisDecorator decorator = DecoratorCore.pickDecorator(biome, getPart(), partRNG, rng, getData(), realX, realZ);

        if (decorator == null || !isSlopeValid(decorator, realX, height, realZ)) {
            return;
        }

        AquaticPlacementSnapshot aquaticSnapshot = captureAquaticPlacement(
                decorator, getData(), data, x, z, height, max);
        if (decorator.isStacking()) {
            DecoratorCore.PlaceOpts opts = DecoratorCore.SCRATCH_OPTS.get();
            opts.reset();
            opts.underwater = underwater;
            opts.fluidHeight = fluidHeight;
            opts.caveSkipFluid = caveSkipFluid;
            DecoratorCore.placeStackUp(decorator, x, z, realX, realZ, height, max, data, rng, getData(), opts);
            aquaticSnapshot.restoreIfUnsupported(data, x, z);
            return;
        }

        DecoratorCore.placeSurfaceSingle(decorator, x, z, realX, height, realZ,
                data, rng, getData(), underwater, caveSkipFluid, getEngine().getMantle());
        aquaticSnapshot.restoreIfUnsupported(data, x, z);
    }

    static boolean isUnderwater(InferredType inferredType, int height, int fluidHeight) {
        return height < fluidHeight && inferredType != InferredType.CAVE;
    }

    static boolean skipsFluid(InferredType inferredType) {
        return inferredType == InferredType.CAVE;
    }

    static boolean hasFluidAbove(Hunk<PlatformBlockState> data, int x, int height, int z) {
        return height + 1 < data.getHeight() && data.get(x, height + 1, z).isFluid();
    }

    public static boolean isAquaticPlacement(PlatformBlockState state) {
        if (state == null) {
            return false;
        }
        if (state.isWaterLogged()) {
            return true;
        }

        String material = IrisProceduralBlocks.materialKey(state);
        if (material.equals("minecraft:seagrass")
                || material.equals("minecraft:tall_seagrass")
                || material.equals("minecraft:kelp")
                || material.equals("minecraft:kelp_plant")
                || material.equals("minecraft:sea_pickle")) {
            return true;
        }
        return material.startsWith("minecraft:")
                && material.contains("_coral")
                && !material.contains(":dead_");
    }

    public static boolean hasConnectedWater(Hunk<PlatformBlockState> data, int x, int y, int z) {
        if (y < 0 || y >= data.getHeight() || !isWater(data.get(x, y, z))) {
            return false;
        }
        return isWaterNeighbor(data, x - 1, y, z)
                || isWaterNeighbor(data, x + 1, y, z)
                || isWaterNeighbor(data, x, y, z - 1)
                || isWaterNeighbor(data, x, y, z + 1);
    }

    static AquaticPlacementSnapshot captureAquaticPlacement(
            IrisDecorator decorator,
            IrisData irisData,
            Hunk<PlatformBlockState> data,
            int x,
            int z,
            int height,
            int max
    ) {
        if (!mayPlaceAquatic(decorator, irisData)) {
            return AquaticPlacementSnapshot.EMPTY;
        }

        int highestOffset = decorator.isStacking()
                ? maximumStackHeight(decorator, max)
                : 2;
        int lowerY = Math.max(0, height);
        int upperY = Math.min(data.getHeight() - 1, height + highestOffset);
        if (lowerY > upperY) {
            return AquaticPlacementSnapshot.EMPTY;
        }

        PlatformBlockState[] originals = new PlatformBlockState[upperY - lowerY + 1];
        boolean[] connectedWater = new boolean[originals.length];
        for (int i = 0; i < originals.length; i++) {
            int y = lowerY + i;
            originals[i] = data.get(x, y, z);
            connectedWater[i] = hasConnectedWater(data, x, y, z);
        }
        return new AquaticPlacementSnapshot(lowerY, originals, connectedWater);
    }

    private static boolean mayPlaceAquatic(IrisDecorator decorator, IrisData irisData) {
        PlatformBlockState[] palette = decorator.getBlockDataArray(irisData);
        if (palette != null) {
            for (PlatformBlockState state : palette) {
                if (isAquaticPlacement(state)) {
                    return true;
                }
            }
        }
        PlatformBlockState[] topPalette = decorator.getBlockDataTopsArray(irisData);
        if (topPalette != null) {
            for (PlatformBlockState state : topPalette) {
                if (isAquaticPlacement(state)) {
                    return true;
                }
            }
        }
        return decorator.getForceBlock() != null
                && isAquaticPlacement(decorator.getForceBlock().getBlockData(irisData));
    }

    private static int maximumStackHeight(IrisDecorator decorator, int max) {
        if (decorator.isScaleStack()) {
            return Math.max(1, decorator.getAbsoluteMaxStack());
        }
        return Math.max(1, Math.min(Math.max(1, max), decorator.getStackMax()));
    }

    private static boolean isWaterNeighbor(Hunk<PlatformBlockState> data, int x, int y, int z) {
        if (x < 0 || x >= data.getWidth() || z < 0 || z >= data.getDepth()) {
            return false;
        }
        return isWater(data.get(x, y, z));
    }

    private static boolean isWater(PlatformBlockState state) {
        return state != null && state.isWater();
    }

    static final class AquaticPlacementSnapshot {
        private static final AquaticPlacementSnapshot EMPTY = new AquaticPlacementSnapshot(
                0, new PlatformBlockState[0], new boolean[0]);

        private final int lowerY;
        private final PlatformBlockState[] originals;
        private final boolean[] connectedWater;

        private AquaticPlacementSnapshot(
                int lowerY,
                PlatformBlockState[] originals,
                boolean[] connectedWater
        ) {
            this.lowerY = lowerY;
            this.originals = originals;
            this.connectedWater = connectedWater;
        }

        void restoreIfUnsupported(Hunk<PlatformBlockState> data, int x, int z) {
            if (!containsUnsupportedAquatic(data, x, z)) {
                return;
            }
            for (int i = 0; i < originals.length; i++) {
                data.set(x, lowerY + i, z, originals[i]);
            }
        }

        private boolean containsUnsupportedAquatic(Hunk<PlatformBlockState> data, int x, int z) {
            for (int i = 0; i < originals.length; i++) {
                PlatformBlockState placed = data.get(x, lowerY + i, z);
                if (placed != originals[i] && isAquaticPlacement(placed) && !connectedWater[i]) {
                    return true;
                }
            }
            return false;
        }
    }
}
