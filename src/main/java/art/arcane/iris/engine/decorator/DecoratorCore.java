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
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.decoration.IrisDecorationPart;
import art.arcane.iris.engine.object.decoration.IrisDecorator;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;

final class DecoratorCore {

    private static final long SEED_OFFSET = 29356788L;
    private static final long PART_FACTOR = 10439677L;
    private static final String WEEPING_VINES = "minecraft:weeping_vines";
    private static final String WEEPING_VINES_PLANT = "minecraft:weeping_vines_plant";
    private static final String TWISTING_VINES = "minecraft:twisting_vines";
    private static final String TWISTING_VINES_PLANT = "minecraft:twisting_vines_plant";
    private static volatile PlatformBlockState weepingVines;
    private static volatile PlatformBlockState weepingVinesPlant;
    private static volatile PlatformBlockState twistingVines;
    private static volatile PlatformBlockState twistingVinesPlant;

    static final ThreadLocal<PlaceOpts> SCRATCH_OPTS = ThreadLocal.withInitial(PlaceOpts::new);

    static final class PlaceOpts {
        boolean caveSkipFluid;
        boolean underwater;
        int fluidHeight;

        void reset() {
            caveSkipFluid = false;
            underwater = false;
            fluidHeight = 0;
        }
    }

    static long partSeed(long baseSeed, int partOrdinal) {
        return baseSeed + SEED_OFFSET - (partOrdinal * PART_FACTOR);
    }

    static long partSeed(long baseSeed, IrisDecorationPart part) {
        return partSeed(baseSeed, part.ordinal());
    }

    static IrisDecorator pickDecorator(IrisBiome biome, IrisDecorationPart part, RNG gRNG,
                                       RNG colRng, IrisData data, double realX, double realZ) {
        IrisDecorator[] bucket = biome.getDecoratorBucket(part);
        if (bucket.length == 0) {
            return null;
        }

        IrisDecorator picked = null;
        int count = 0;

        for (IrisDecorator d : bucket) {
            try {
                if (d.passesChanceGate(gRNG, realX, realZ, data)) {
                    count++;
                    if (count == 1 || colRng.nextInt(count) == 0) {
                        picked = d;
                    }
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }

        return picked;
    }

    private static String topHalfValue(String half) {
        return half.equals("upper") || half.equals("lower") ? "upper" : "top";
    }

    private static String bottomHalfValue(String half) {
        return half.equals("upper") || half.equals("lower") ? "lower" : "bottom";
    }

    static void placeSurfaceSingle(IrisDecorator decorator,
                                   int x, int z, int realX, int height, int realZ,
                                   Hunk<PlatformBlockState> data, RNG rng, IrisData irisData,
                                   boolean underwater, boolean caveSkipFluid, EngineMantle mantle) {
        if (height < 0 || height >= data.getHeight()) {
            return;
        }

        PlatformBlockState bdx = data.get(x, height, z);
        PlatformBlockState bd = decorator.pickBlockData(rng, irisData, realX, realZ);

        if (!underwater && !canGoOn(bd, bdx)
                && !decorator.isForcePlace() && decorator.getForceBlock() == null) {
            return;
        }

        if (decorator.getForceBlock() != null) {
            if (caveSkipFluid && B.isFluid(bdx)) {
                return;
            }
            data.set(x, height, z, fixFacesForHunk(
                    decorator.getForceBlock().getBlockData(irisData), data, x, z, realX, height, realZ, mantle));
        }

        if (!decorator.isForcePlace()) {
            if (decorator.getWhitelist() != null && !matchesPalette(decorator.getWhitelistArray(irisData), bdx)) {
                return;
            }
            if (decorator.getBlacklist() != null && matchesPalette(decorator.getBlacklistArray(irisData), bdx)) {
                return;
            }
        }

        String half = bd == null ? null : IrisProceduralBlocks.propertyValue(bd, "half");
        if (half != null) {
            int lowerY = height + 1;
            int upperY = height + 2;
            if (!canPlaceTwoBlockPlant(data, x, z, lowerY, upperY, caveSkipFluid)) {
                return;
            }

            try {
                PlatformBlockState upper = bd.withProperty("half", topHalfValue(half));
                PlatformBlockState lower = fixFacesForHunk(
                        bd.withProperty("half", bottomHalfValue(half)),
                        data, x, z, realX, lowerY, realZ, mantle);
                data.set(x, lowerY, z, lower);
                data.set(x, upperY, z, upper);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
            return;
        }

        int targetY = height + 1;
        if (targetY < data.getHeight() && B.isAir(data.get(x, targetY, z))) {
            data.set(x, targetY, z, fixFacesForHunk(bd, data, x, z, realX, targetY, realZ, mantle));
        }
    }

    private static boolean matchesPalette(PlatformBlockState[] palette, PlatformBlockState surface) {
        for (int i = 0; i < palette.length; i++) {
            if (palette[i].equals(surface)) {
                return true;
            }
        }

        return false;
    }

    static void placeSingleAt(IrisDecorator decorator, int x, int z,
                              int realX, int height, int realZ, Hunk<PlatformBlockState> data,
                              RNG rng, IrisData irisData, boolean applyFixFaces, EngineMantle mantle) {
        PlatformBlockState bd = decorator.pickBlockData(rng, irisData, realX, realZ);
        if (bd == null) {
            return;
        }
        if (applyFixFaces) {
            bd = fixFacesForHunk(bd, data, x, z, realX, height, realZ, mantle);
        }
        data.set(x, height, z, bd);
    }

    static void placeStackUp(IrisDecorator decorator, int x, int z, int realX, int realZ,
                             int height, int max, Hunk<PlatformBlockState> data,
                             RNG rng, IrisData irisData, PlaceOpts opts) {
        if (height < 0 || height >= data.getHeight()) {
            return;
        }

        int effectiveMax = max;
        if (opts.underwater && height < opts.fluidHeight) {
            effectiveMax = opts.fluidHeight;
        }

        int stack = computeStack(decorator, rng, realX, realZ, irisData, effectiveMax);

        if (stack == 1) {
            int targetY = height + 1;
            if (targetY >= data.getHeight()) {
                return;
            }

            PlatformBlockState existing = data.get(x, targetY, z);
            if (!canReplaceStackTarget(existing, opts.underwater)
                    || (opts.caveSkipFluid && B.isFluid(existing))) {
                return;
            }

            PlatformBlockState block = decorator.pickBlockDataTop(rng, irisData, realX, realZ);
            if (block == null || (!opts.underwater && !canGoOn(block, data.get(x, height, z)))) {
                return;
            }

            block = stackedVineBlock(block, stack, 0);
            data.set(x, targetY, z, block);
            return;
        }

        PlatformBlockState bdx = data.get(x, height, z);

        for (int i = 0; i < stack; i++) {
            int h = height + i;
            double threshold = ((double) i) / (stack - 1);
            PlatformBlockState bd = threshold >= decorator.getTopThreshold()
                    ? decorator.pickBlockDataTop(rng, irisData, realX, realZ)
                    : decorator.pickBlockData(rng, irisData, realX, realZ);

            if (bd == null) {
                break;
            }

            if (i == 0 && !opts.underwater && !canGoOn(bd, bdx)) {
                break;
            }

            if (opts.underwater && height + 1 + i > opts.fluidHeight) {
                break;
            }

            if (height + 1 + i >= data.getHeight()) {
                break;
            }

            PlatformBlockState existing = data.get(x, height + 1 + i, z);
            if (!canReplaceStackTarget(existing, opts.underwater)
                    || (opts.caveSkipFluid && B.isFluid(existing))) {
                break;
            }

            if (IrisProceduralBlocks.materialKey(bd).equals("minecraft:pointed_dripstone")) {
                bd = dripstoneBlock(stack, i, "up");
            }

            bd = stackedVineBlock(bd, stack, i);
            data.set(x, height + 1 + i, z, bd);
        }
    }

    static void placeStackDown(IrisDecorator decorator, int x, int z, int realX, int realZ,
                               int height, int minHeight, Hunk<PlatformBlockState> data,
                               RNG rng, IrisData irisData, int max, PlaceOpts opts, EngineMantle mantle) {
        if (height < 0 || height >= data.getHeight()) {
            return;
        }

        int stack = computeStack(decorator, rng, realX, realZ, irisData, max);

        if (stack == 1) {
            if (opts.caveSkipFluid && B.isFluid(data.get(x, height, z))) {
                return;
            }
            PlatformBlockState block = decorator.pickBlockDataTop(rng, irisData, realX, realZ);
            if (block == null) {
                return;
            }
            block = stackedVineBlock(block, stack, 0);
            data.set(x, height, z, fixFacesForHunk(block, data, x, z, realX, height, realZ, mantle));
            return;
        }

        for (int i = 0; i < stack; i++) {
            int h = height - i;
            if (h < 0 || h < minHeight) {
                break;
            }

            double threshold = ((double) i) / (double) (stack - 1);
            PlatformBlockState bd = threshold >= decorator.getTopThreshold()
                    ? decorator.pickBlockDataTop(rng, irisData, realX, realZ)
                    : decorator.pickBlockData(rng, irisData, realX, realZ);

            if (bd == null) {
                break;
            }

            if (IrisProceduralBlocks.materialKey(bd).equals("minecraft:pointed_dripstone")) {
                bd = dripstoneBlock(stack, i, "down");
            }

            bd = stackedVineBlock(bd, stack, i);
            if (opts.caveSkipFluid && B.isFluid(data.get(x, h, z))) {
                break;
            }
            data.set(x, h, z, fixFacesForHunk(bd, data, x, z, realX, h, realZ, mantle));
        }
    }

    static void placeFloatingSimple(IrisDecorator decorator,
                                    int xf, int zf, int realX, int realZ,
                                    int height, int max, Hunk<PlatformBlockState> data,
                                    RNG rng, IrisData irisData) {
        PlatformBlockState bd = decorator.pickBlockData(rng, irisData, realX, realZ);
        if (bd == null) {
            return;
        }

        String half = IrisProceduralBlocks.propertyValue(bd, "half");
        if (half != null) {
            int lowerY = height + 1;
            int upperY = height + 2;
            if (max <= 2 || !canPlaceTwoBlockPlant(data, xf, zf, lowerY, upperY, false)) {
                return;
            }

            try {
                PlatformBlockState upper = bd.withProperty("half", topHalfValue(half));
                PlatformBlockState lower = bd.withProperty("half", bottomHalfValue(half));
                data.set(xf, lowerY, zf, lower);
                data.set(xf, upperY, zf, upper);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
            return;
        }

        if (max > 1 && height + 1 < data.getHeight()) {
            data.set(xf, height + 1, zf, bd);
        }
    }

    static int placeFloatingStacked(IrisDecorator decorator,
                                    int xf, int zf, int realX, int realZ,
                                    int height, int max, Hunk<PlatformBlockState> data,
                                    RNG rng, IrisData irisData) {
        int stack = decorator.getHeight(rng, realX, realZ, irisData);
        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.getAbsoluteMaxStack());
        } else {
            stack = Math.min(max, stack);
        }

        int placed = 0;
        for (int i = 0; i < stack; i++) {
            int h = height + 1 + i;
            if (h >= height + max || h >= data.getHeight()) {
                break;
            }
            double threshold = stack == 1 ? 0.0 : ((double) i) / (stack - 1);
            PlatformBlockState bd = threshold >= decorator.getTopThreshold()
                    ? decorator.pickBlockDataTop(rng, irisData, realX, realZ)
                    : decorator.pickBlockData(rng, irisData, realX, realZ);
            if (bd == null) {
                break;
            }
            bd = stackedVineBlock(bd, stack, i);
            data.set(xf, h, zf, bd);
            placed++;
        }
        return placed;
    }

    static PlatformBlockState fixFacesForHunk(PlatformBlockState b, Hunk<PlatformBlockState> hunk, int rX, int rZ,
                                              int x, int y, int z, EngineMantle mantle) {
        if (!B.isVineBlock(b)) {
            return b;
        }
        DecoratorPlatformHooks.FaceFixer fixer = DecoratorPlatformHooks.faceFixer();
        if (fixer != null) {
            return fixer.fixFaces(b, hunk, rX, rZ, x, y, z, mantle);
        }
        BlockData rawB = (BlockData) b.nativeHandle();
        BlockData cloned = rawB.clone();
        MultipleFacing data = (MultipleFacing) cloned;
        data.getFaces().forEach(f -> data.setFace(f, false));

        boolean found = false;
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) {
                continue;
            }
            int yy = y + f.getModY();

            PlatformBlockState rs = null;
            if (mantle != null) {
                rs = mantle.getMantle().get(x + f.getModX(), yy, z + f.getModZ(), PlatformBlockState.class);
            }
            BlockData r = rs == null ? (BlockData) EngineMantle.AIR.nativeHandle() : (BlockData) rs.nativeHandle();
            if (r.isFaceSturdy(f.getOppositeFace(), BlockSupport.FULL)) {
                if (data.getAllowedFaces().contains(f)) {
                    found = true;
                    data.setFace(f, true);
                }
                continue;
            }

            int xx = rX + f.getModX();
            int zz = rZ + f.getModZ();
            if (xx < 0 || xx > 15 || zz < 0 || zz > 15 || yy < 0 || yy >= hunk.getHeight()) {
                continue;
            }

            r = (BlockData) hunk.get(xx, yy, zz).nativeHandle();
            if (r.isFaceSturdy(f.getOppositeFace(), BlockSupport.FULL)) {
                if (data.getAllowedFaces().contains(f)) {
                    found = true;
                    data.setFace(f, true);
                }
            }
        }
        if (!found) {
            BlockFace fallback = data.getAllowedFaces().contains(BlockFace.DOWN) ? BlockFace.DOWN : BlockFace.UP;
            if (data.getAllowedFaces().contains(fallback)) {
                data.setFace(fallback, true);
            }
        }
        return BukkitBlockState.of(cloned);
    }

    static boolean canGoOn(PlatformBlockState decorator, PlatformBlockState surface) {
        if (!B.canPlaceOnto(decorator, surface)) {
            return false;
        }
        DecoratorPlatformHooks.SurfaceSturdiness sturdiness = DecoratorPlatformHooks.surfaceSturdiness();
        if (sturdiness != null) {
            return sturdiness.canGoOn(surface);
        }
        return ((BlockData) surface.nativeHandle()).isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    static boolean isValidShorelineSupport(IrisDecorator decorator, PlatformBlockState decorant, PlatformBlockState surface) {
        return surface != null
                && B.isSolid(surface)
                && (decorator.isForcePlace() || canGoOn(decorant, surface));
    }

    static boolean canReplaceStackTarget(PlatformBlockState state, boolean allowFluid) {
        return B.isAir(state) || allowFluid && B.isFluid(state);
    }

    private static boolean canPlaceTwoBlockPlant(Hunk<PlatformBlockState> data, int x, int z,
                                                 int lowerY, int upperY, boolean caveSkipFluid) {
        if (lowerY < 0 || upperY >= data.getHeight()) {
            return false;
        }

        PlatformBlockState lower = data.get(x, lowerY, z);
        PlatformBlockState upper = data.get(x, upperY, z);
        return B.isAir(lower) && B.isAir(upper)
                && (!caveSkipFluid || !B.isFluid(lower) && !B.isFluid(upper));
    }

    private static int computeStack(IrisDecorator decorator, RNG rng, double realX, double realZ,
                                    IrisData irisData, int max) {
        int stack = decorator.getHeight(rng, realX, realZ, irisData);
        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.getAbsoluteMaxStack());
        } else {
            stack = Math.min(max, stack);
        }
        return stack;
    }

    private static volatile PlatformBlockState[] dripstoneUp;
    private static volatile PlatformBlockState[] dripstoneDown;

    private static PlatformBlockState[] buildDripstoneArr(String direction) {
        String[] order = {"tip", "frustum", "base"};
        PlatformBlockState[] arr = new PlatformBlockState[3];
        for (int k = 0; k < 3; k++) {
            arr[k] = B.getState("minecraft:pointed_dripstone[thickness=" + order[k] + ",vertical_direction=" + direction + "]");
        }
        return arr;
    }

    private static PlatformBlockState dripstoneBlock(int stack, int i, String direction) {
        int thIdx;
        if (i == stack - 1) {
            thIdx = 0;
        } else if (i == stack - 2) {
            thIdx = 1;
        } else {
            thIdx = 2;
        }
        if (direction.equals("up")) {
            if (dripstoneUp == null) {
                dripstoneUp = buildDripstoneArr("up");
            }
            return dripstoneUp[thIdx];
        }
        if (dripstoneDown == null) {
            dripstoneDown = buildDripstoneArr("down");
        }
        return dripstoneDown[thIdx];
    }

    static String stackedVineKey(PlatformBlockState state, int stack, int index) {
        String material = IrisProceduralBlocks.materialKey(state);
        boolean tip = index == stack - 1;
        return switch (material) {
            case WEEPING_VINES, WEEPING_VINES_PLANT -> tip ? WEEPING_VINES : WEEPING_VINES_PLANT;
            case TWISTING_VINES, TWISTING_VINES_PLANT -> tip ? TWISTING_VINES : TWISTING_VINES_PLANT;
            default -> null;
        };
    }

    private static PlatformBlockState stackedVineBlock(PlatformBlockState state, int stack, int index) {
        String key = stackedVineKey(state, stack, index);
        if (key == null) {
            return state;
        }
        return switch (key) {
            case WEEPING_VINES -> weepingVines == null ? weepingVines = B.getState(key) : weepingVines;
            case WEEPING_VINES_PLANT -> weepingVinesPlant == null ? weepingVinesPlant = B.getState(key) : weepingVinesPlant;
            case TWISTING_VINES -> twistingVines == null ? twistingVines = B.getState(key) : twistingVines;
            case TWISTING_VINES_PLANT -> twistingVinesPlant == null ? twistingVinesPlant = B.getState(key) : twistingVinesPlant;
            default -> state;
        };
    }
}
