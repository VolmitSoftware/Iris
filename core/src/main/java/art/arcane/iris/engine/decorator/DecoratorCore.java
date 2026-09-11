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
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
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
        EngineMantle mantle;

        void reset() {
            caveSkipFluid = false;
            underwater = false;
            fluidHeight = 0;
            mantle = null;
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

        if (!IrisSpeleothems.isSpike(bd) && !underwater && !canGoOn(bd, bdx)
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

        if (!IrisSugarCane.canPlace(bd, data, x, height + 1, z, realX, realZ,
                mantle == null ? null : mantle.getEngine())) {
            return;
        }

        if (IrisSpeleothems.isSpike(bd)) {
            placeSingleSpike(bd, data, x, z, height + 1, true, underwater && !caveSkipFluid);
            return;
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
        if (surface == null) {
            return false;
        }
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == surface || surface.matches(palette[i])) {
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
        if (!IrisSugarCane.canPlace(bd, data, x, height, z, realX, realZ,
                mantle == null ? null : mantle.getEngine())) {
            return;
        }
        if (IrisSpeleothems.isSpike(bd)) {
            if (height + 1 < data.getHeight() && allowsSurface(decorator, data.get(x, height + 1, z), irisData)) {
                placeSingleSpike(bd, data, x, z, height, false, false);
            }
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

        PlatformBlockState support = data.get(x, height, z);
        if (!allowsSurface(decorator, support, irisData)) {
            return;
        }
        int effectiveMax = opts.underwater ? Math.min(max, opts.fluidHeight - height) : max;
        int stack = computeStack(decorator, rng, realX, realZ, irisData, effectiveMax);
        int placed = 0;
        boolean hasSpikes = false;
        for (int i = 0; i < stack; i++) {
            int y = height + 1 + i;
            if (y >= data.getHeight()) {
                break;
            }
            PlatformBlockState existing = data.get(x, y, z);
            if (!canReplaceStackTarget(existing, opts.underwater)
                    || (opts.caveSkipFluid && B.isFluid(existing))) {
                break;
            }
            double threshold = stack == 1 ? 1.0 : ((double) i) / (stack - 1);
            PlatformBlockState block = threshold >= decorator.getTopThreshold()
                    ? decorator.pickBlockDataTop(rng, irisData, realX, realZ)
                    : decorator.pickBlockData(rng, irisData, realX, realZ);
            if (block == null) {
                break;
            }
            if (!IrisSugarCane.canPlace(block, data, x, y, z, realX, realZ,
                    opts.mantle == null ? null : opts.mantle.getEngine())) {
                break;
            }
            if (IrisSpeleothems.isSpike(block)) {
                if (!IrisSpeleothems.canPlace(block, data, x, z, y, true, opts.underwater)) {
                    break;
                }
                block = IrisSpeleothems.orient(block, existing, true);
                hasSpikes = true;
            } else if (i == 0 && !opts.underwater && !canGoOn(block, support)) {
                break;
            }
            data.set(x, y, z, stackedVineBlock(block, stack, i));
            placed++;
        }
        if (placed > 0 && placed < stack) {
            finishVineTip(data, x, z, height + placed);
        }
        if (hasSpikes) {
            IrisSpeleothems.finishColumn(data, x, z, height + 1, placed, true);
        }
    }

    static void placeStackDown(IrisDecorator decorator, int x, int z, int realX, int realZ,
                               int height, int minHeight, Hunk<PlatformBlockState> data,
                               RNG rng, IrisData irisData, int max, PlaceOpts opts, EngineMantle mantle) {
        if (height < 0 || height >= data.getHeight()) {
            return;
        }
        PlatformBlockState support = height + 1 < data.getHeight() ? data.get(x, height + 1, z) : null;
        if (!allowsSurface(decorator, support, irisData)) {
            return;
        }
        int stack = computeStack(decorator, rng, realX, realZ, irisData, max);
        int placed = 0;
        boolean hasSpikes = false;
        for (int i = 0; i < stack; i++) {
            int y = height - i;
            if (y < 0 || y < minHeight) {
                break;
            }
            PlatformBlockState existing = data.get(x, y, z);
            if (!canReplaceStackTarget(existing, opts.underwater)
                    || (opts.caveSkipFluid && B.isFluid(existing))) {
                break;
            }
            double threshold = stack == 1 ? 1.0 : ((double) i) / (stack - 1);
            PlatformBlockState block = threshold >= decorator.getTopThreshold()
                    ? decorator.pickBlockDataTop(rng, irisData, realX, realZ)
                    : decorator.pickBlockData(rng, irisData, realX, realZ);
            if (block == null) {
                break;
            }
            if (!IrisSugarCane.canPlace(block, data, x, y, z, realX, realZ,
                    mantle == null ? null : mantle.getEngine())) {
                break;
            }
            if (IrisSpeleothems.isSpike(block)) {
                if (!IrisSpeleothems.canPlace(block, data, x, z, y, false, opts.underwater)) {
                    break;
                }
                block = IrisSpeleothems.orient(block, existing, false);
                hasSpikes = true;
            }
            block = stackedVineBlock(block, stack, i);
            data.set(x, y, z, fixFacesForHunk(block, data, x, z, realX, y, realZ, mantle));
            placed++;
        }
        if (placed > 0 && placed < stack) {
            finishVineTip(data, x, z, height - placed + 1);
        }
        if (hasSpikes) {
            IrisSpeleothems.finishColumn(data, x, z, height, placed, false);
        }
    }

    static void placeFloatingSimple(IrisDecorator decorator,
                                    int xf, int zf, int realX, int realZ,
                                    int height, int max, Hunk<PlatformBlockState> data,
                                    RNG rng, IrisData irisData, EngineMantle mantle) {
        PlatformBlockState bd = decorator.pickBlockData(rng, irisData, realX, realZ);
        if (bd == null) {
            return;
        }
        if (!IrisSugarCane.canPlace(bd, data, xf, height + 1, zf, realX, realZ,
                mantle == null ? null : mantle.getEngine())) {
            return;
        }

        if (IrisSpeleothems.isSpike(bd)) {
            if (max > 1 && allowsSurface(decorator, data.get(xf, height, zf), irisData)) {
                placeSingleSpike(bd, data, xf, zf, height + 1, true, false);
            }
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
                                    RNG rng, IrisData irisData, EngineMantle mantle) {
        int stack = decorator.getHeight(rng, realX, realZ, irisData);
        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.getAbsoluteMaxStack());
        } else {
            stack = Math.min(max, stack);
        }

        int placed = 0;
        boolean hasSpikes = false;
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
            if (!IrisSugarCane.canPlace(bd, data, xf, h, zf, realX, realZ,
                    mantle == null ? null : mantle.getEngine())) {
                break;
            }
            if (IrisSpeleothems.isSpike(bd)) {
                if (!allowsSurface(decorator, data.get(xf, height, zf), irisData)
                        || !IrisSpeleothems.canPlace(bd, data, xf, zf, h, true, false)) {
                    break;
                }
                bd = IrisSpeleothems.orient(bd, data.get(xf, h, zf), true);
                hasSpikes = true;
            }
            bd = stackedVineBlock(bd, stack, i);
            data.set(xf, h, zf, bd);
            placed++;
        }
        if (placed > 0 && placed < stack) {
            finishVineTip(data, xf, zf, height + placed);
        }
        if (hasSpikes) {
            IrisSpeleothems.finishColumn(data, xf, zf, height + 1, placed, true);
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
            BlockData r = rs == null ? (BlockData) EngineMantle.AIR.get().nativeHandle() : (BlockData) rs.nativeHandle();
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
        return IrisSugarCane.isSugarCane(decorator) || IrisSpeleothems.isSturdy(surface, true);
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

    static int computeStack(IrisDecorator decorator, RNG rng, double realX, double realZ,
                                    IrisData irisData, int max) {
        int stack = decorator.getHeight(rng, realX, realZ, irisData);
        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.getAbsoluteMaxStack());
        } else {
            stack = Math.min(max, stack);
        }
        return stack;
    }

    private static boolean allowsSurface(IrisDecorator decorator, PlatformBlockState surface, IrisData data) {
        return decorator.isForcePlace()
                || (decorator.getWhitelist() == null || matchesPalette(decorator.getWhitelistArray(data), surface))
                && (decorator.getBlacklist() == null || !matchesPalette(decorator.getBlacklistArray(data), surface));
    }

    private static void placeSingleSpike(PlatformBlockState spike, Hunk<PlatformBlockState> data,
                                          int x, int z, int y, boolean upward, boolean allowWater) {
        if (!IrisSpeleothems.canPlace(spike, data, x, z, y, upward, allowWater)) {
            return;
        }
        data.set(x, y, z, IrisSpeleothems.orient(spike, data.get(x, y, z), upward));
        IrisSpeleothems.finishColumn(data, x, z, y, 1, upward);
    }

    private static void finishVineTip(Hunk<PlatformBlockState> data, int x, int z, int y) {
        PlatformBlockState state = data.get(x, y, z);
        PlatformBlockState tip = stackedVineBlock(state, 1, 0);
        if (tip != state) {
            data.set(x, y, z, tip);
        }
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
