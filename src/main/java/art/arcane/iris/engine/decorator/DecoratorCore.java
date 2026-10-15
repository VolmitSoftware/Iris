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
package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Map;

final class DecoratorCore {

    private static final long SEED_OFFSET = 29356788L;
    private static final long PART_FACTOR = 10439677L;
    private static final String HALF = "half";
    private static final ResourceId WEEPING_VINES = ResourceId.parse("minecraft:weeping_vines");
    private static final ResourceId WEEPING_VINES_PLANT = ResourceId.parse("minecraft:weeping_vines_plant");
    private static final ResourceId TWISTING_VINES = ResourceId.parse("minecraft:twisting_vines");
    private static final ResourceId TWISTING_VINES_PLANT = ResourceId.parse("minecraft:twisting_vines_plant");
    private static final ResourceId POINTED_DRIPSTONE = ResourceId.parse("minecraft:pointed_dripstone");
    private static final String[] DRIPSTONE_THICKNESS = {"tip", "frustum", "base"};
    private static final BlockState[] DRIPSTONE_UP = dripstones("up");
    private static final BlockState[] DRIPSTONE_DOWN = dripstones("down");
    private static final BlockState AIR = new BlockState("minecraft:air");
    /** The faces a vine can grow on, in the order Iris looks at them, with the neighbour each faces. */
    private static final List<Face> FACES = List.of(new Face(BlockFace.NORTH, "north", 0, 0, -1), new Face(BlockFace.EAST, "east", 1, 0, 0),
            new Face(BlockFace.SOUTH, "south", 0, 0, 1), new Face(BlockFace.WEST, "west", -1, 0, 0), new Face(BlockFace.UP, "up", 0, 1, 0),
            new Face(BlockFace.DOWN, "down", 0, -1, 0));
    private static final Map<BlockFace, BlockFace> OPPOSITES = Map.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.SOUTH, BlockFace.NORTH,
            BlockFace.EAST, BlockFace.WEST, BlockFace.WEST, BlockFace.EAST, BlockFace.UP, BlockFace.DOWN, BlockFace.DOWN, BlockFace.UP);
    private static final int CHUNK_LAST = 15;

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

    private record Face(BlockFace face, String property, int dx, int dy, int dz) {
    }

    static long partSeed(long baseSeed, int partOrdinal) {
        return baseSeed + SEED_OFFSET - (partOrdinal * PART_FACTOR);
    }

    static long partSeed(long baseSeed, DecorationPart part) {
        return partSeed(baseSeed, part.ordinal());
    }

    /** The decorator of {@code bucket} that decorates the column, drawing from {@code colRng} to choose between those whose chance noise passes. */
    static ResolvedDecorator pickDecorator(ResolvedDecorator[] bucket, RNG colRng, double realX, double realZ, ModuleLog log) {
        ResolvedDecorator picked = null;
        int count = 0;

        for (ResolvedDecorator d : bucket) {
            try {
                if (d.passesChanceGate(realX, realZ)) {
                    count++;
                    if (count == 1 || colRng.nextInt(count) == 0) {
                        picked = d;
                    }
                }
            } catch (Throwable e) {
                log.error("A decorator failed to pick for the column", e);
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

    static void placeSurfaceSingle(ResolvedDecorator decorator, DecorationScene scene,
                                   int x, int z, int realX, int height, int realZ,
                                   boolean underwater, boolean caveSkipFluid) {
        Hunk<BlockState> data = scene.blocks();
        if (height < 0 || height >= data.getHeight()) {
            return;
        }

        BlockState bdx = data.get(x, height, z);
        BlockState bd = decorator.pickBlock(realX, realZ);

        if (!underwater && !canGoOn(scene, bd, bdx)
                && !decorator.isForcePlace() && decorator.forceBlock() == null) {
            return;
        }

        if (decorator.forceBlock() != null) {
            if (caveSkipFluid && scene.isFluid(bdx)) {
                return;
            }
            data.set(x, height, z, fixFacesForHunk(scene, decorator.forceBlock(), x, z, realX, height, realZ));
        }

        if (!decorator.isForcePlace() && decorator.rejectsSurface(bdx)) {
            return;
        }

        String half = bd == null ? null : bd.properties().get(HALF);
        if (half != null) {
            int lowerY = height + 1;
            int upperY = height + 2;
            if (!canPlaceTwoBlockPlant(scene, x, z, lowerY, upperY, caveSkipFluid)) {
                return;
            }

            try {
                BlockState upper = bd.withProperty(HALF, topHalfValue(half));
                BlockState lower = fixFacesForHunk(scene,
                        bd.withProperty(HALF, bottomHalfValue(half)),
                        x, z, realX, lowerY, realZ);
                data.set(x, lowerY, z, lower);
                data.set(x, upperY, z, upper);
            } catch (Throwable e) {
                scene.log().error("Failed to place a two-block plant", e);
            }
            return;
        }

        int targetY = height + 1;
        if (bd != null && targetY < data.getHeight() && scene.isAir(data.get(x, targetY, z))) {
            data.set(x, targetY, z, fixFacesForHunk(scene, bd, x, z, realX, targetY, realZ));
        }
    }

    static void placeSingleAt(ResolvedDecorator decorator, DecorationScene scene, int x, int z,
                              int realX, int height, int realZ, boolean applyFixFaces) {
        BlockState bd = decorator.pickBlock(realX, realZ);
        if (bd == null) {
            return;
        }
        if (applyFixFaces) {
            bd = fixFacesForHunk(scene, bd, x, z, realX, height, realZ);
        }
        scene.blocks().set(x, height, z, bd);
    }

    static void placeStackUp(ResolvedDecorator decorator, DecorationScene scene, int x, int z, int realX, int realZ,
                             int height, int max, PlaceOpts opts) {
        Hunk<BlockState> data = scene.blocks();
        if (height < 0 || height >= data.getHeight()) {
            return;
        }

        int effectiveMax = max;
        if (opts.underwater && height < opts.fluidHeight) {
            effectiveMax = opts.fluidHeight;
        }

        int stack = computeStack(decorator, realX, realZ, effectiveMax);

        if (stack == 1) {
            int targetY = height + 1;
            if (targetY >= data.getHeight()) {
                return;
            }

            BlockState existing = data.get(x, targetY, z);
            if (!canReplaceStackTarget(scene, existing, opts.underwater)
                    || (opts.caveSkipFluid && scene.isFluid(existing))) {
                return;
            }

            BlockState block = decorator.pickTopBlock(realX, realZ);
            if (block == null || (!opts.underwater && !canGoOn(scene, block, data.get(x, height, z)))) {
                return;
            }

            block = stackedVineBlock(block, stack, 0);
            data.set(x, targetY, z, block);
            return;
        }

        BlockState bdx = data.get(x, height, z);

        for (int i = 0; i < stack; i++) {
            double threshold = ((double) i) / (stack - 1);
            BlockState bd = threshold >= decorator.topThreshold()
                    ? decorator.pickTopBlock(realX, realZ)
                    : decorator.pickBlock(realX, realZ);

            if (bd == null) {
                break;
            }

            if (i == 0 && !opts.underwater && !canGoOn(scene, bd, bdx)) {
                break;
            }

            if (opts.underwater && height + 1 + i > opts.fluidHeight) {
                break;
            }

            if (height + 1 + i >= data.getHeight()) {
                break;
            }

            BlockState existing = data.get(x, height + 1 + i, z);
            if (!canReplaceStackTarget(scene, existing, opts.underwater)
                    || (opts.caveSkipFluid && scene.isFluid(existing))) {
                break;
            }

            if (bd.material().equals(POINTED_DRIPSTONE)) {
                bd = dripstoneBlock(stack, i, DRIPSTONE_UP);
            }

            bd = stackedVineBlock(bd, stack, i);
            data.set(x, height + 1 + i, z, bd);
        }
    }

    static void placeStackDown(ResolvedDecorator decorator, DecorationScene scene, int x, int z, int realX, int realZ,
                               int height, int minHeight, int max, PlaceOpts opts) {
        Hunk<BlockState> data = scene.blocks();
        if (height < 0 || height >= data.getHeight()) {
            return;
        }

        int stack = computeStack(decorator, realX, realZ, max);

        if (stack == 1) {
            if (opts.caveSkipFluid && scene.isFluid(data.get(x, height, z))) {
                return;
            }
            BlockState block = decorator.pickTopBlock(realX, realZ);
            if (block == null) {
                return;
            }
            block = stackedVineBlock(block, stack, 0);
            data.set(x, height, z, fixFacesForHunk(scene, block, x, z, realX, height, realZ));
            return;
        }

        for (int i = 0; i < stack; i++) {
            int h = height - i;
            if (h < 0 || h < minHeight) {
                break;
            }

            double threshold = ((double) i) / (double) (stack - 1);
            BlockState bd = threshold >= decorator.topThreshold()
                    ? decorator.pickTopBlock(realX, realZ)
                    : decorator.pickBlock(realX, realZ);

            if (bd == null) {
                break;
            }

            if (bd.material().equals(POINTED_DRIPSTONE)) {
                bd = dripstoneBlock(stack, i, DRIPSTONE_DOWN);
            }

            bd = stackedVineBlock(bd, stack, i);
            if (opts.caveSkipFluid && scene.isFluid(data.get(x, h, z))) {
                break;
            }
            data.set(x, h, z, fixFacesForHunk(scene, bd, x, z, realX, h, realZ));
        }
    }

    static void placeFloatingSimple(ResolvedDecorator decorator, DecorationScene scene,
                                    int xf, int zf, int realX, int realZ,
                                    int height, int max) {
        Hunk<BlockState> data = scene.blocks();
        BlockState bd = decorator.pickBlock(realX, realZ);
        if (bd == null) {
            return;
        }

        String half = bd.properties().get(HALF);
        if (half != null) {
            int lowerY = height + 1;
            int upperY = height + 2;
            if (max <= 2 || !canPlaceTwoBlockPlant(scene, xf, zf, lowerY, upperY, false)) {
                return;
            }

            try {
                BlockState upper = bd.withProperty(HALF, topHalfValue(half));
                BlockState lower = bd.withProperty(HALF, bottomHalfValue(half));
                data.set(xf, lowerY, zf, lower);
                data.set(xf, upperY, zf, upper);
            } catch (Throwable e) {
                scene.log().error("Failed to place a two-block plant", e);
            }
            return;
        }

        if (max > 1 && height + 1 < data.getHeight()) {
            data.set(xf, height + 1, zf, bd);
        }
    }

    static int placeFloatingStacked(ResolvedDecorator decorator, DecorationScene scene,
                                    int xf, int zf, int realX, int realZ,
                                    int height, int max) {
        Hunk<BlockState> data = scene.blocks();
        int stack = decorator.stackHeight(realX, realZ);
        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.absoluteMaxStack());
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
            BlockState bd = threshold >= decorator.topThreshold()
                    ? decorator.pickTopBlock(realX, realZ)
                    : decorator.pickBlock(realX, realZ);
            if (bd == null) {
                break;
            }
            bd = stackedVineBlock(bd, stack, i);
            data.set(xf, h, zf, bd);
            placed++;
        }
        return placed;
    }

    /**
     * A vine placed at {@code x}, {@code y}, {@code z} (the chunk's local column {@code rX}, {@code rZ}) faces the neighbours that
     * can hold it: the blocks the mantle planned there, else the chunk's own blocks. A vine with no such neighbour faces down,
     * or up when it cannot face down. Any other block is returned as it is.
     */
    static BlockState fixFacesForHunk(DecorationScene scene, BlockState b, int rX, int rZ, int x, int y, int z) {
        if (b == null || !scene.has(BlockTrait.VINE, b)) {
            return b;
        }
        Hunk<BlockState> hunk = scene.blocks();
        BlockState fixed = b;
        for (Face face : FACES) {
            if (b.properties().containsKey(face.property())) {
                fixed = fixed.withProperty(face.property(), "false");
            }
        }

        boolean found = false;
        for (Face face : FACES) {
            int yy = y + face.dy();
            BlockFace opposite = OPPOSITES.get(face.face());

            BlockState planned = scene.plannedAt(x + face.dx(), yy, z + face.dz());
            BlockState support = planned == null ? AIR : planned;
            if (scene.registry().canAttach(b, support, opposite)) {
                if (b.properties().containsKey(face.property())) {
                    found = true;
                    fixed = fixed.withProperty(face.property(), "true");
                }
                continue;
            }

            int xx = rX + face.dx();
            int zz = rZ + face.dz();
            if (xx < 0 || xx > CHUNK_LAST || zz < 0 || zz > CHUNK_LAST || yy < 0 || yy >= hunk.getHeight()) {
                continue;
            }

            if (scene.registry().canAttach(b, hunk.get(xx, yy, zz), opposite)) {
                if (b.properties().containsKey(face.property())) {
                    found = true;
                    fixed = fixed.withProperty(face.property(), "true");
                }
            }
        }
        if (!found) {
            String fallback = b.properties().containsKey("down") ? "down" : "up";
            if (b.properties().containsKey(fallback)) {
                fixed = fixed.withProperty(fallback, "true");
            }
        }
        return fixed;
    }

    static boolean canGoOn(DecorationScene scene, BlockState decorator, BlockState surface) {
        return decorator != null && surface != null && scene.canGoOn(decorator, surface);
    }

    static boolean isValidShorelineSupport(DecorationScene scene, ResolvedDecorator decorator, BlockState decorant, BlockState surface) {
        return surface != null
                && scene.isSolid(surface)
                && (decorator.isForcePlace() || canGoOn(scene, decorant, surface));
    }

    static boolean canReplaceStackTarget(DecorationScene scene, BlockState state, boolean allowFluid) {
        return scene.isAir(state) || allowFluid && scene.isFluid(state);
    }

    private static boolean canPlaceTwoBlockPlant(DecorationScene scene, int x, int z,
                                                 int lowerY, int upperY, boolean caveSkipFluid) {
        Hunk<BlockState> data = scene.blocks();
        if (lowerY < 0 || upperY >= data.getHeight()) {
            return false;
        }

        BlockState lower = data.get(x, lowerY, z);
        BlockState upper = data.get(x, upperY, z);
        return scene.isAir(lower) && scene.isAir(upper)
                && (!caveSkipFluid || !scene.isFluid(lower) && !scene.isFluid(upper));
    }

    private static int computeStack(ResolvedDecorator decorator, double realX, double realZ, int max) {
        int stack = decorator.stackHeight(realX, realZ);
        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.absoluteMaxStack());
        } else {
            stack = Math.min(max, stack);
        }
        return stack;
    }

    private static BlockState[] dripstones(String direction) {
        BlockState[] arr = new BlockState[DRIPSTONE_THICKNESS.length];
        for (int k = 0; k < arr.length; k++) {
            arr[k] = BlockState.parse("minecraft:pointed_dripstone[thickness=" + DRIPSTONE_THICKNESS[k] + ",vertical_direction=" + direction + "]");
        }
        return arr;
    }

    private static BlockState dripstoneBlock(int stack, int i, BlockState[] byThickness) {
        int thIdx;
        if (i == stack - 1) {
            thIdx = 0;
        } else if (i == stack - 2) {
            thIdx = 1;
        } else {
            thIdx = 2;
        }
        return byThickness[thIdx];
    }

    /** The key of the vine block that fills a stack of vines at {@code index}: the tip at the end, the plant before it; null for other blocks. */
    static ResourceId stackedVineMaterial(BlockState state, int stack, int index) {
        ResourceId material = state.material();
        boolean tip = index == stack - 1;
        if (material.equals(WEEPING_VINES) || material.equals(WEEPING_VINES_PLANT)) {
            return tip ? WEEPING_VINES : WEEPING_VINES_PLANT;
        }
        if (material.equals(TWISTING_VINES) || material.equals(TWISTING_VINES_PLANT)) {
            return tip ? TWISTING_VINES : TWISTING_VINES_PLANT;
        }
        return null;
    }

    private static BlockState stackedVineBlock(BlockState state, int stack, int index) {
        ResourceId material = stackedVineMaterial(state, stack, index);
        return material == null ? state : new BlockState(material.toString());
    }
}
