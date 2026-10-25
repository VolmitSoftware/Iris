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


package art.arcane.iris.studio.map;

import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.studio.map.BlockModel.Face;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The faces of an object's blocks that can be seen from outside. A face is left out when the neighbour on its side
 * covers that side, which is most of them in a large object. See-through faces, like water and stained glass, are
 * kept apart because they are drawn last, blended over everything behind them. Top faces covered only by the block
 * above are kept aside too, so the top layers can be cut away without building the mesh again; what is cut away stays
 * as a faint ghost of the object's shape.
 */
record ObjectMesh(List<Quad> solidQuads, List<Quad> translucentQuads, List<Quad> cutAwayQuads, List<Quad> topsUnderBlockAbove,
                  Bounds bounds) implements OrbitScene {
    private static final double SEE_THROUGH_OPACITY = 0.22D;
    private static final double FULL_OPACITY = 1D;
    private static final double CUT_AWAY_OPACITY = 0.1D;

    static ObjectMesh build(Map<IrisBlockVector, NativeBlockState> blocks, Function<NativeBlockState, BlockModel> modelOf) {
        return build(blocks, modelOf, Bounds.of(blocks.keySet()), block -> false);
    }

    /**
     * @param bounds       the whole object's extent, kept when only part of it is drawn so the view does not jump
     * @param isSeeThrough blocks drawn faintly, which hide nothing behind them, to look past them into the object
     */
    static ObjectMesh build(Map<IrisBlockVector, NativeBlockState> blocks, Function<NativeBlockState, BlockModel> modelOf, Bounds bounds,
                            Predicate<NativeBlockState> isSeeThrough) {
        List<Quad> solid = new ArrayList<>();
        List<Quad> translucent = new ArrayList<>();
        List<Quad> topsUnderBlockAbove = new ArrayList<>();
        BlockGrid grid = new BlockGrid(blocks, modelOf, bounds, isSeeThrough);
        for (IrisBlockVector position : blocks.keySet()) {
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            int cell = grid.cellAt(x, y, z);
            for (Face face : grid.modelIn(cell).faces()) {
                Quad quad = new Quad(face, x, y, z, grid.isSeeThroughIn(cell) ? SEE_THROUGH_OPACITY : FULL_OPACITY);
                if (!grid.isHiddenByNeighbour(x, y, z, face)) {
                    (quad.isBlended() ? translucent : solid).add(quad);
                } else if (face.cullFace() == CubeFace.UP) {
                    topsUnderBlockAbove.add(quad);
                }
            }
        }
        return new ObjectMesh(List.copyOf(solid), List.copyOf(translucent), List.of(), List.copyOf(topsUnderBlockAbove), bounds);
    }

    /**
     * This mesh with the blocks above the given layer faded to a ghost of the object's shape, and the tops of that
     * layer's blocks showing again. Quick enough to follow a slider as it moves.
     */
    ObjectMesh cutAbove(int highestShownY) {
        List<Quad> solid = new ArrayList<>();
        List<Quad> translucent = new ArrayList<>();
        List<Quad> cutAway = new ArrayList<>();
        for (Quad quad : solidQuads) {
            keepOrCutAway(quad, highestShownY, solid, cutAway);
        }
        for (Quad quad : translucentQuads) {
            keepOrCutAway(quad, highestShownY, translucent, cutAway);
        }
        for (Quad quad : topsUnderBlockAbove) {
            if (quad.y() == highestShownY) {
                (quad.isBlended() ? translucent : solid).add(quad);
            }
        }
        return new ObjectMesh(solid, translucent, cutAway, List.of(), bounds);
    }

    private static void keepOrCutAway(Quad quad, int highestShownY, List<Quad> kept, List<Quad> cutAway) {
        if (quad.y() <= highestShownY) {
            kept.add(quad);
        } else {
            cutAway.add(quad.faded(CUT_AWAY_OPACITY));
        }
    }

    @Override
    public boolean isEmpty() {
        return solidQuads.isEmpty() && translucentQuads.isEmpty() && cutAwayQuads.isEmpty();
    }

    @Override
    public OrbitPicture render(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return renderOnCard(camera, width, height, coveredLeftWidth)
                .orElseGet(() -> ObjectRenderer.render(this, camera, width, height, coveredLeftWidth));
    }

    private Optional<OrbitPicture> renderOnCard(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        if (!GpuLibrary.isLoaded()) {
            return Optional.empty();
        }
        return GpuObjectRenderer.render(this, camera, width, height, coveredLeftWidth);
    }

    @Override
    public boolean canRenderOnScreen() {
        return GpuLibrary.isLoaded() && GpuFrames.canDrawOnScreen();
    }

    @Override
    public Optional<OrbitPicture> renderOnScreen(ScreenTarget screen, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return GpuObjectRenderer.renderOnScreen(this, screen, camera, width, height, coveredLeftWidth);
    }

    @Override
    public OrbitProjection projection(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return ObjectRenderer.projection(this, camera, width, height, coveredLeftWidth);
    }

    int faceCount() {
        return solidQuads.size() + translucentQuads.size();
    }

    /**
     * The object's blocks laid out in an array over its bounds, so finding a block's neighbours is quick. Looking them
     * up by {@link BlockVector} in a map is slow, since its hash codes collide a lot.
     */
    private static final class BlockGrid {
        private final Bounds bounds;
        private final NativeBlockState[] blocks;
        private final BlockModel[] models;
        private final boolean[] seeThrough;

        BlockGrid(Map<IrisBlockVector, NativeBlockState> placed, Function<NativeBlockState, BlockModel> modelOf, Bounds bounds,
                  Predicate<NativeBlockState> isSeeThrough) {
            this.bounds = bounds;
            int cells = bounds.sizeX() * bounds.sizeY() * bounds.sizeZ();
            this.blocks = new NativeBlockState[cells];
            this.models = new BlockModel[cells];
            this.seeThrough = new boolean[cells];
            placed.forEach((position, block) -> {
                int cell = cellAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
                blocks[cell] = block;
                models[cell] = modelOf.apply(block);
                seeThrough[cell] = isSeeThrough.test(block);
            });
        }

        int cellAt(int x, int y, int z) {
            return ((y - bounds.minY()) * bounds.sizeZ() + (z - bounds.minZ())) * bounds.sizeX() + (x - bounds.minX());
        }

        BlockModel modelIn(int cell) {
            return models[cell];
        }

        boolean isSeeThroughIn(int cell) {
            return seeThrough[cell];
        }

        // Glass next to glass and water next to water hide the side between them too, as they do in the game. A
        // see-through block only hides sides of other see-through blocks, so whatever it covers shows through it.
        boolean isHiddenByNeighbour(int x, int y, int z, Face face) {
            CubeFace side = face.cullFace();
            if (side == null || !contains(x + side.stepX(), y + side.stepY(), z + side.stepZ())) {
                return false;
            }
            int cell = cellAt(x, y, z);
            int neighbourCell = cellAt(x + side.stepX(), y + side.stepY(), z + side.stepZ());
            NativeBlockState neighbour = blocks[neighbourCell];
            if (neighbour == null || (seeThrough[neighbourCell] && !seeThrough[cell])) {
                return false;
            }
            BlockModel neighbourModel = models[neighbourCell];
            if (neighbourModel.coveredSides().contains(side.opposite())) {
                return true;
            }
            return isSameBlockType(neighbour, blocks[cell]) && models[cell].hidesSameBlockSides()
                    && neighbourModel.hidesSameBlockSides();
        }

        private static boolean isSameBlockType(NativeBlockState first, NativeBlockState second) {
            return IrisProceduralBlocks.materialKey(first).equals(IrisProceduralBlocks.materialKey(second));
        }

        private boolean contains(int x, int y, int z) {
            return x >= bounds.minX() && x < bounds.maxX() && y >= bounds.minY() && y < bounds.maxY()
                    && z >= bounds.minZ() && z < bounds.maxZ();
        }
    }

    /**
     * A model face placed at a block in the object, drawn at the given opacity on top of its texture's own.
     */
    record Quad(Face face, int x, int y, int z, double opacity) {
        boolean isBlended() {
            return opacity < FULL_OPACITY || face.texture().isTranslucent();
        }

        Quad faded(double factor) {
            return new Quad(face, x, y, z, opacity * factor);
        }

        double originX() {
            return x + face.origin().x();
        }

        double originY() {
            return y + face.origin().y();
        }

        double originZ() {
            return z + face.origin().z();
        }
    }

    /**
     * The blocks' extent, with the maximum one past the last block so a single block spans one unit, and the height the
     * terrain surface meets the object at.
     */
    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int groundY) {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 1, 1, 1);

        /** An extent standing on its lowest block. */
        Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(minX, minY, minZ, maxX, maxY, maxZ, minY);
        }

        /** This extent with the terrain surface at {@code groundY}, so anything below it shows as buried. */
        Bounds standingOn(int groundY) {
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, groundY);
        }

        static Bounds of(Iterable<IrisBlockVector> positions) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (IrisBlockVector position : positions) {
                minX = Math.min(minX, position.getBlockX());
                minY = Math.min(minY, position.getBlockY());
                minZ = Math.min(minZ, position.getBlockZ());
                maxX = Math.max(maxX, position.getBlockX() + 1);
                maxY = Math.max(maxY, position.getBlockY() + 1);
                maxZ = Math.max(maxZ, position.getBlockZ() + 1);
            }
            return minX == Integer.MAX_VALUE ? EMPTY : new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        int sizeX() {
            return maxX - minX;
        }

        int sizeY() {
            return maxY - minY;
        }

        int sizeZ() {
            return maxZ - minZ;
        }

        int largestSize() {
            return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
        }
    }
}
