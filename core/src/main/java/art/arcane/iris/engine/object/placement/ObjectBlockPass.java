package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.marker.ObjectMarkerPlacement;
import art.arcane.iris.engine.object.material.BlockDataMergeSupport;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.BlockPosition;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

final class ObjectBlockPass {

    private List<ObjectMarkerPlacement.MarkerPlacement> matchingMarkers;
    private final BiConsumer<BlockPosition, PlatformBlockState> listener;

    private final PlacementContext context;
    private final PlacementState state;
    private final VectorMap<PlatformBlockState> blocks;
    private final VectorMap<TileData> states;
    private final ObjectMarkerPlacement markerPlacer;
    private final PlacementLifecycle lifecycle;

    ObjectBlockPass(
            PlacementContext context,
            PlacementState state,
            PlacementLifecycle lifecycle,
            BiConsumer<BlockPosition, PlatformBlockState> listener
    ) {
        this.context = context;
        this.state = state;
        this.lifecycle = lifecycle;
        this.listener = listener;
        this.blocks = context.self.blocks;
        this.states = context.self.states;

        if (state.config.getMarkers().isNotEmpty() && state.placer.getEngine() != null) {
            markerPlacer = new ObjectMarkerPlacement(context, state);
            markerPlacer.loadPossibleMarkers();
        } else {
            markerPlacer = null;
        }
    }

    void process() {
        VectorMap<PlatformBlockState>.Cursor cursor = blocks.cursor();
        IrisObjectTranslate translate = state.config.getTranslate();
        CNG surfaceWarp = !state.config.getWarp().isFlat() ? state.config.getSurfaceWarp(context.rng, context.self.getLoader()) : null;
        boolean preventingDecay = state.placer.isPreventingDecay();
        boolean waterlogCandidate = state.config.isWaterloggable() || state.config.isUnderwater();
        boolean hasStates = !states.isEmpty();
        int xx, zz;

        double warpHalf = !state.config.getWarp().isFlat() ? state.config.getWarp().getMultiplier() / 2D : 0D;

        while (cursor.next()) {
            var read = readStateFrom(cursor, hasStates);

            PlatformBlockState data = read.getA();
            TileData tile = read.getB();
            IrisBlockVector vector = cursor.key().clone();

            state.spin.rotate(vector);
            PlacementBlock block = new PlacementBlock(data, tile, vector);
            lifecycle.run(PlacementStage.TRANSFORM_BLOCK, context, state, block);
            if (state.isRejected()) {
                return;
            }

            if (translate.canTranslate()) {
                vector.add(state.translateOffset);
            }

            lifecycle.run(PlacementStage.COLLECT_BLOCK, context, state, block);
            if (state.isRejected()) {
                return;
            }
            if (preventingDecay && IrisProceduralBlocks.hasProperty(data, "distance") && "false".equals(IrisProceduralBlocks.propertyValue(data, "persistent"))) {
                data = data.withProperty("persistent", "true");
            }

            if (!state.config.getEdit().isEmpty()) {
                var result = applyEdits(vector, data, tile);
                data = result.data();
                tile = result.tile();
            }

            matchingMarkers = markerPlacer != null ? markerPlacer.matchMarkers(data) : null;

            data = state.spin.rotate(data);
            xx = context.x + (int) Math.round(vector.getX());
            int yy = state.y + (int) Math.round(vector.getY());
            zz = context.z + (int) Math.round(vector.getZ());

            if (!state.config.getWarp().isFlat()) {
                xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, vector.getX() + context.x, vector.getY() + state.y, vector.getZ() + context.z);
                zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, vector.getZ() + context.z, vector.getY() + state.y, vector.getX() + context.x);
            }

            if (context.yv < 0 && (is(ObjectPlaceMode.PAINT)) && !B.isVineBlock(data)) {
                yy = (int) Math.round(vector.getY()) + Math.floorDiv(context.self.h, 2) + state.placer.getHighest(xx, zz, context.self.getLoader(), state.config.isUnderwater());
            }

            if (state.config.isMeld() && !is(ObjectPlaceMode.STRUCTURE_PIECE) && !state.placer.isSolid(xx, yy, zz)) {
                continue;
            }

            if (waterlogCandidate && IrisProceduralBlocks.hasProperty(data, "waterlogged") && shouldAutoWaterlogBlock(context, state, xx, yy, zz)) {
                data = data.withProperty("waterlogged", "true");
            }

            if (B.isVineBlock(data)) {
                data = attachVineFaces(state.placer, data, xx, yy, zz);
            }

            if (handleCustomData(data, tile, xx, yy, zz)) {
                lifecycle.run(PlacementStage.BLOCK_WRITTEN, context, state, new PlacementBlockWrite(xx, yy, zz, data));
                if (state.isRejected()) {
                    return;
                }
            }
        }

        if (markerPlacer != null) {
            markerPlacer.placeMarkers();
        }


    }

    private boolean is(ObjectPlaceMode mode) {
        return state.config.getMode() == mode;
    }

    static boolean shouldPlaceObjectBlock(boolean rawStructurePiece, boolean air, boolean wouldReplace) {
        return !wouldReplace && (rawStructurePiece || !air);
    }

    private boolean handleCustomData(PlatformBlockState data, TileData tile, int xx, int yy, int zz) {
        // Short-circuit order matters for cost only: the mantle read is paid solely
        // for vine blocks. Both operands are pure, so the value is unchanged.
        boolean wouldReplace = B.isVineBlock(data) && B.isSolid(state.placer.get(xx, yy, zz));
        String material = IrisObjectShaping.materialKey(data);
        boolean air = material.equals("minecraft:air") || material.equals("minecraft:cave_air");
        boolean place = shouldPlaceObjectBlock(is(ObjectPlaceMode.STRUCTURE_PIECE), air, wouldReplace);
        if (data.isCustom() || place) {
            state.placer.set(xx, yy, zz, data);
            if (tile != null) {
                state.placer.setTile(xx, yy, zz, tile);
            }
            if (matchingMarkers != null) {
                BlockPosition markerPosition = new BlockPosition(xx, yy, zz);
                for (ObjectMarkerPlacement.MarkerPlacement markerPlacement : matchingMarkers) {
                    markerPlacement.candidates.add(markerPosition);
                }
            }
            if (listener != null) {
                listener.accept(new BlockPosition(xx, yy, zz), data);
            }
            return true;
        }
        return false;
    }

    private EditResult applyEdits(IrisBlockVector vector, PlatformBlockState data, TileData tile) {
        for (IrisObjectReplace j : state.config.getEdit()) {
            if (context.rng.chance(j.getChance())) {
                for (PlatformBlockState k : j.getFind(context.rdata)) {
                    if (j.isExact() ? k.matches(data) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(data))) {
                        PlatformBlockState newData = j.getReplace(context.rng, vector.getX() + context.x, vector.getY() + state.y, vector.getZ() + context.z, context.rdata);

                        if (IrisObjectShaping.materialKey(newData).equals(IrisObjectShaping.materialKey(data)) && !(newData.isCustom() || data.isCustom()))
                            data = BlockDataMergeSupport.merge(data, newData);
                        else
                            data = newData;

                        Optional<TileData> t = j.getReplace().getTile(context.rng, context.x, state.y, context.z, context.rdata);
                        if (t.isPresent()) {
                            tile = t.get();
                        }
                    }
                }
            }
        }
        return new EditResult(data, tile);
    }

    static boolean shouldAutoWaterlogBlock(PlacementContext context, PlacementState state, int x, int y, int z) {
        if (!(state.config.isWaterloggable() || state.config.isUnderwater())) {
            return false;
        }

        if (context.yv >= 0 && state.config.getCarvingSupport().equals(CarvingMode.CARVING_ONLY)) {
            return false;
        }

        PlatformBlockState existing = state.placer.get(x, y, z);
        if (existing == null) {
            return false;
        }

        return B.isWater(existing) || B.isWaterLogged(existing);
    }

    static PlatformBlockState attachVineFaces(IObjectPlacer placer, PlatformBlockState data, int x, int y, int z) {
        PlatformBlockState result = data;
        for (String face : IrisProceduralBlocks.FACE_PROPERTIES) {
            if (!IrisProceduralBlocks.hasProperty(data, face)) {
                continue;
            }
            int[] mod = IrisProceduralBlocks.faceOffset(face);
            PlatformBlockState facing = placer.get(x + mod[0], y + mod[1], z + mod[2]);
            if (B.isSolid(facing) && !B.isVineBlock(facing)) {
                result = result.withProperty(face, "true");
            }
        }
        return result;
    }


    private Pair<PlatformBlockState, TileData> readStateFrom(VectorMap<PlatformBlockState>.Cursor cursor, boolean hasStates) {
        PlatformBlockState block;
        TileData tile = null;
        IrisBlockVector vector = cursor.key();
        try {
            block = cursor.value();
            if (hasStates) {
                tile = states.get(vector);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Failed to read block node " + vector.getBlockX() + "," + vector.getBlockY() + "," + vector.getBlockZ() + " in object " + context.self.getLoadKey() + " (cme)");
            block = IrisObject.States.AIR;
        }

        if (block == null) {
            IrisLogging.warn("Failed to read block node " + vector.getBlockX() + "," + vector.getBlockY() + "," + vector.getBlockZ() + " in object " + context.self.getLoadKey() + " (null)");
            block = IrisObject.States.AIR;
        }
        if (state.placer.isDebugSmartBore() && IrisObject.States.VAIR.equals(block)) {
            block = IrisObject.States.VAIR_DEBUG;
        }
        return new Pair<>(block, tile);
    }

    private record EditResult(PlatformBlockState data, TileData tile) {}
}
