package art.arcane.iris.engine.object;

import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.Position2;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

class ObjectBlockPass {

    private KMap<Position2, Integer> heightmap;
    private List<ObjectMarkerPlacement.MarkerPlacement> matchingMarkers;
    private BiConsumer<BlockPosition, PlatformBlockState> listener;

    private PlacementContext context;
    private PlacementState state;
    private VectorMap<PlatformBlockState> blocks;
    private VectorMap<TileData> states;
    private  ObjectMarkerPlacement markerPlacer;
    private PlacementModule behavior;

     ObjectBlockPass(
             PlacementContext context,
             PlacementState state,
             PlacementModule behavior,
             BiConsumer<BlockPosition, PlatformBlockState> listener
     ) {
        this.context = context;
        this.state = state;
        this.behavior = behavior;
        this.listener = listener;
        this.blocks = context.self.blocks;
        this.states = context.self.states;
        this.heightmap = context.config.getSnow() > 0 ? new KMap<>() : null;

        if (context.config.getMarkers().isNotEmpty() && context.placer.getEngine() != null) {
            markerPlacer = new ObjectMarkerPlacement(context.config, context);
            markerPlacer.loadPossibleMarkers();
        }
    }

    void process() {
        VectorMap<PlatformBlockState>.Cursor cursor = blocks.cursor();
        IrisObjectTranslate translate = context.config.getTranslate();
        CNG surfaceWarp = !context.config.getWarp().isFlat() ? context.config.getSurfaceWarp(context.rng, context.self.getLoader()) : null;
        int xx, zz;

        double warpHalf = context.config.getWarp().isFlat() ? context.config.getWarp().getMultiplier() / 2D : 0D;

        while (cursor.next()) {
            var read = readStateFrom(cursor);

            PlatformBlockState data = read.getA();
            TileData tile = read.getB();
            IrisBlockVector vector = cursor.key().clone();

            context.spin.rotate(vector);
            behavior.onBlock(blocks, states, data, tile, vector);

            if (translate.canTranslate()) {
                vector.add(translateOffset);
            }

            if (context.placer.isPreventingDecay() && IrisProceduralBlocks.hasProperty(data, "distance") && "false".equals(IrisProceduralBlocks.propertyValue(data, "persistent"))) {
                data = data.withProperty("persistent", "true");
            }

            //todo move this under stilt mode
//                if (stilting && IrisObjectShaping.shouldStilt(data)) {
//                    if (i.getBlockY() < lowest) {
//                        lowest = i.getBlockY();
//                    }
//                    if (i.getBlockY() > topLayer) {
//                        topLayer = i.getBlockY();
//                    }
//                }

            if (!context.config.getEdit().isEmpty()) {
                var result = applyEdits(vector, data, tile);
                data = result.data();
                tile = result.tile();
            }

            matchingMarkers = markerPlacer != null ? markerPlacer.matchMarkers(data) : null;

            data = context.spin.rotate(data);
            xx = context.x + (int) Math.round(vector.getX());
            int yy = state.y + (int) Math.round(vector.getY());
            zz = context.z + (int) Math.round(vector.getZ());

            if (!context.config.getWarp().isFlat()) {
                xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, vector.getX() + context.x, vector.getY() + state.y, vector.getZ() + context.z);
                zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, vector.getZ() + context.z, vector.getY() + state.y, vector.getX() + context.x);
            }

            if (context.yv < 0 && (is(ObjectPlaceMode.PAINT)) && !B.isVineBlock(data)) {
                yy = (int) Math.round(vector.getY()) + Math.floorDiv(context.self.h, 2) + context.placer.getHighest(xx, zz, context.self.getLoader(), context.config.isUnderwater());
            }

            if (context.config.isMeld() && !is(ObjectPlaceMode.STRUCTURE_PIECE) && !context.placer.isSolid(xx, yy, zz)) {
                continue;
            }

            if ((context.config.isWaterloggable() || context.config.isUnderwater()) && IrisProceduralBlocks.hasProperty(data, "waterlogged") && shouldAutoWaterlogBlock(context.yv, xx, yy, zz)) {
                data = data.withProperty("waterlogged", "true");
            }

            if (B.isVineBlock(data)) {
                data = attachVineFaces(context.placer, data, xx, yy, zz);
            }

            handleCustomData(data, tile, xx, yy, zz);

            if (vacuuming && yy < vacuumLowest) {
                vacuumLowest = yy;
            }
            if (vacuuming && yy > vacuumHighest) {
                vacuumHighest = yy;
            }
        }

        if (markerPlacer != null) {
            markerPlacer.placeMarkers();
        }


    }

    private boolean is(ObjectPlaceMode mode) {
         return context.config.getMode() == mode;
    }

    static boolean shouldPlaceObjectBlock(boolean rawStructurePiece, boolean air, boolean wouldReplace) {
        return !wouldReplace && (rawStructurePiece || !air);
    }

    private void handleCustomData(PlatformBlockState data, TileData tile, int xx, int yy, int zz) {
        // Short-circuit order matters for cost only: the mantle read is paid solely
        // for vine blocks. Both operands are pure, so the value is unchanged.
        boolean wouldReplace = B.isVineBlock(data) && B.isSolid(context.placer.get(xx, yy, zz));
        String material = IrisObjectShaping.materialKey(data);
        boolean air = material.equals("minecraft:air") || material.equals("minecraft:cave_air");
        boolean place = shouldPlaceObjectBlock(is(ObjectPlaceMode.STRUCTURE_PIECE), air, wouldReplace);
        if (data.isCustom() || place) {
            context.placer.set(xx, yy, zz, data);
            if (heightmap != null) {
                Position2 pos = new Position2(xx, zz);
                Integer currentHeight = heightmap.get(pos);
                if (currentHeight == null || currentHeight < yy) {
                    heightmap.put(pos, yy);
                }
            }
            if (tile != null) {
                context.placer.setTile(xx, yy, zz, tile);
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
        }
    }

    private EditResult applyEdits(IrisBlockVector vector, PlatformBlockState data, TileData tile) {
        for (IrisObjectReplace j : context.config.getEdit()) {
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
        new EditResult(data, tile);
    }

    public boolean shouldAutoWaterlogBlock(int yv, int x, int y, int z) {
        if (!(context.config.isWaterloggable() || context.config.isUnderwater())) {
            return false;
        }

        if (yv >= 0 && context.config.getCarvingSupport().equals(CarvingMode.CARVING_ONLY)) {
            return false;
        }

        PlatformBlockState existing = context.placer.get(x, y, z);
        if (existing == null) {
            return false;
        }

        return B.isWater(existing) || B.isWaterLogged(existing);
    }

    private static PlatformBlockState attachVineFaces(IObjectPlacer placer, PlatformBlockState data, int x, int y, int z) {
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


    private Pair<PlatformBlockState, TileData> readStateFrom(VectorMap<PlatformBlockState>.Cursor cursor) {
        PlatformBlockState block;
        TileData tile = null;
        IrisBlockVector vector = cursor.key();
        boolean hasStates = !states.isEmpty();
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
        if (context.placer.isDebugSmartBore() && IrisObject.States.VAIR.equals(block)) {
            block = IrisObject.States.VAIR_DEBUG;
        }
        return new Pair<>(block, tile);
    }

    private record EditResult(PlatformBlockState data, TileData tile) {}
}
