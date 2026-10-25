package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.matter.MatterMarker;

import java.util.Optional;
import java.util.function.BiConsumer;

final class ObjectBlockPass {

    private final BiConsumer<BlockPosition, NativeBlockState> listener;

    private final PlacementContext context;
    private final PlacementState state;
    private final ObjectPlacementBake bake;
    private final PlacementPalette palette;
    private final String[] markers;
    private final PlacementLifecycle lifecycle;

    ObjectBlockPass(
            PlacementContext context,
            PlacementState state,
            PlacementLifecycle lifecycle,
            BiConsumer<BlockPosition, NativeBlockState> listener
    ) {
        this.context = context;
        this.state = state;
        this.lifecycle = lifecycle;
        this.listener = listener;
        this.bake = state.bake;
        this.palette = state.palette;
        this.markers = state.config.getMarkers().isNotEmpty() && state.placer.getEngine() != null
                ? ObjectMarkerPlacement.resolve(context, state.config, bake) : null;
    }

    void process() {
        boolean warped = !state.config.getWarp().isFlat();
        boolean hasEdits = !state.config.getEdit().isEmpty();
        CNG surfaceWarp = warped ? state.config.getSurfaceWarp(context.rng, context.self.getLoader()) : null;
        double warpHalf = warped ? state.config.getWarp().getMultiplier() / 2D : 0D;
        boolean waterlogCandidate = state.config.isWaterloggable() || state.config.isUnderwater();
        double[] exact = warped || hasEdits ? bake.exact(context.self, state.spin) : null;
        int[] tileBlocks = bake.tileBlocks();
        TileData[] tileValues = bake.tileValues();
        int tileCursor = 0;
        int count = bake.count();

        for (int index = 0; index < count; index++) {
            int entry = bake.paletteIndex(index);
            TileData tile = null;
            if (tileBlocks != null && tileCursor < tileBlocks.length && tileBlocks[tileCursor] == index) {
                tile = tileValues[tileCursor++];
            }

            NativeBlockState data = palette.decayed(entry);
            boolean edited = false;
            if (hasEdits) {
                EditResult result = applyEdits(exact, index, data, tile);
                data = result.data();
                tile = result.tile();
                edited = result.edited();
            }

            PlacementPalette.Facts facts = edited ? palette.factsOf(data) : palette.facts(entry);
            NativeBlockState rotated = facts.rotated();
            if (rotated == null) {
                continue;
            }
            int xx = context.x + bake.offsetX(index);
            int yy = state.y + bake.offsetY(index);
            int zz = context.z + bake.offsetZ(index);

            if (warped) {
                double exactX = exact[index * 3];
                double exactY = exact[index * 3 + 1];
                double exactZ = exact[index * 3 + 2];
                xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, exactX + context.x, exactY + state.y, exactZ + context.z);
                zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, exactZ + context.z, exactY + state.y, exactX + context.x);
            }

            if (state.paintProjection != null) {
                int surface = state.paintProjection.surfaceY(xx, zz);
                if (surface == IrisPaintSurfaceProjection.MISSING) {
                    continue;
                }
                if (!B.isVineBlock(rotated)) {
                    yy = bake.offsetY(index) + Math.floorDiv(context.self.h, 2) + surface + state.yrand;
                }
            }

            if (state.config.isMeld() && !is(ObjectPlaceMode.STRUCTURE_PIECE) && !state.placer.isSolid(xx, yy, zz)) {
                continue;
            }

            boolean canopy = bake.canopy(index);
            if (canopy && !state.placer.beginTreeCanopy(new BlockPosition(xx, yy, zz))) {
                continue;
            }

            try {
                data = rotated;
                if (facts.waterloggable()) {
                    data = waterlogCandidate || facts.authoredWaterlogged()
                            ? IrisProceduralBlocks.normalizeWaterlogging(data, state.placer.get(xx, yy, zz), waterlogCandidate)
                            : facts.dry();
                }

                if (!is(ObjectPlaceMode.STRUCTURE_PIECE) && B.isVineBlock(data)) {
                    data = attachVineFaces(state.placer, data, xx, yy, zz);
                }

                String marker = markers == null ? null : markers[index];
                if (handleCustomData(data, facts.air(), tile, marker, xx, yy, zz)) {
                    lifecycle.run(PlacementStage.BLOCK_WRITTEN, context, state, new PlacementBlockWrite(xx, yy, zz, data));
                    if (state.isRejected()) {
                        return;
                    }
                }
            } finally {
                if (canopy) {
                    state.placer.endTreeCanopy();
                }
            }
        }
    }

    private boolean is(ObjectPlaceMode mode) {
        return state.config.getMode() == mode;
    }

    static boolean shouldPlaceObjectBlock(boolean rawStructurePiece, boolean air, boolean wouldReplace) {
        return !wouldReplace && (rawStructurePiece || !air);
    }

    private boolean handleCustomData(NativeBlockState data, boolean air, TileData tile, String marker, int xx, int yy, int zz) {
        // Short-circuit order matters for cost only: the mantle read is paid solely
        // for vine blocks. Both operands are pure, so the value is unchanged.
        boolean wouldReplace = !is(ObjectPlaceMode.STRUCTURE_PIECE) && B.isVineBlock(data) && B.isSolid(state.placer.get(xx, yy, zz));
        boolean place = shouldPlaceObjectBlock(is(ObjectPlaceMode.STRUCTURE_PIECE), air, wouldReplace);
        if (data.isCustom() || place) {
            state.placer.set(xx, yy, zz, data);
            if (tile != null) {
                state.placer.setTile(xx, yy, zz, tile);
            }
            if (marker != null) {
                state.placer.setData(xx, yy, zz, new MatterMarker(marker));
            }
            if (listener != null) {
                listener.accept(new BlockPosition(xx, yy, zz), data);
            }
            return true;
        }
        return false;
    }

    private EditResult applyEdits(double[] exact, int index, NativeBlockState data, TileData tile) {
        double exactX = exact[index * 3];
        double exactY = exact[index * 3 + 1];
        double exactZ = exact[index * 3 + 2];
        boolean edited = false;
        for (IrisObjectReplace j : state.config.getEdit()) {
            if (context.rng.chance(j.getChance())) {
                for (NativeBlockState k : j.getFind(context.rdata)) {
                    if (j.isExact() ? k.matches(data) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(data))) {
                        NativeBlockState newData = j.getReplace(context.rng, exactX + context.x, exactY + state.y, exactZ + context.z, context.rdata);

                        boolean sameMaterial = IrisObjectShaping.materialKey(newData).equals(IrisObjectShaping.materialKey(data));
                        if (sameMaterial && !(newData.isCustom() || data.isCustom()))
                            data = BlockDataMergeSupport.merge(data, newData);
                        else
                            data = newData;
                        edited = true;

                        Optional<TileData> t = j.getReplace().getTile(context.rng, context.x, state.y, context.z, context.rdata);
                        if (t.isPresent()) {
                            tile = t.get();
                        } else if (!sameMaterial) {
                            tile = null;
                        }
                    }
                }
            }
        }
        return new EditResult(data, tile, edited);
    }

    static NativeBlockState normalizePlacedWaterlogging(NativeBlockState state, IObjectPlacer placer,
                                                        int x, int y, int z, boolean enabled) {
        if (state == null || !IrisProceduralBlocks.hasProperty(state, "waterlogged")) {
            return state;
        }
        if (!enabled && !"true".equals(IrisProceduralBlocks.propertyValue(state, "waterlogged"))) {
            return IrisProceduralBlocks.normalizeWaterlogging(state, null, false);
        }
        return IrisProceduralBlocks.normalizeWaterlogging(state, placer.get(x, y, z), enabled);
    }

    static NativeBlockState attachVineFaces(IObjectPlacer placer, NativeBlockState data, int x, int y, int z) {
        NativeBlockState result = data;
        for (String face : IrisProceduralBlocks.FACE_PROPERTIES) {
            if (!IrisProceduralBlocks.hasProperty(data, face)) {
                continue;
            }
            int[] mod = IrisProceduralBlocks.faceOffset(face);
            NativeBlockState facing = placer.get(x + mod[0], y + mod[1], z + mod[2]);
            if (B.isSolid(facing) && !B.isVineBlock(facing)) {
                result = result.withProperty(face, "true");
            }
        }
        return result;
    }

    private record EditResult(NativeBlockState data, TileData tile, boolean edited) {}
}
