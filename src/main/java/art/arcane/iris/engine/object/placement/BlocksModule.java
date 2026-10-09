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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.blocks.ObjectReplace;
import com.volmit.FeatureRegistry;
import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.blocks.ObjectMarkers;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.core.modules.IrisPlacement;
import art.arcane.iris.engine.object.marker.IrisMarker;
import art.arcane.iris.engine.object.marker.IrisObjectMarker;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;

import java.util.Set;

/**
 * Module {@code iris:blocks}: writes the object's blocks where the placement puts them, then its markers.
 * Each block is turned with the object, edited, warped, painted onto its column when the mode paints,
 * waterlogged in water and attached to the faces vines can hang from. Air is not written, except by
 * structure pieces, and neither is a vine into a solid block or a melded block into open space.
 */
public final class BlocksModule implements IrisModule {
    private static final Set<String> AIR_MATERIALS = Set.of("minecraft:air", "minecraft:cave_air");
    private static final String DISTANCE = "distance";
    private static final String PERSISTENT = "persistent";
    private static final String WATERLOGGED = "waterlogged";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final int MARKER_HEADROOM_BLOCKS = 2;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:blocks", "Object blocks", PlacementModules.VERSION);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerHeldType(ObjectReplace.class);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Blocks.WRITE)
                .requires(IrisStages.Prepare.TRANSFORMS, IrisStages.Anchor.FINAL, IrisStages.Bore.CLEAR)
                .accumulate(WrittenBlocks::new, BlocksModule::write);
        graph.define(IrisStages.Blocks.MARKERS)
                .requires(IrisStages.Blocks.WRITE, IrisStages.Prepare.TRANSFORMS)
                .run(BlocksModule::placeMarkers);
        graph.define(IrisStages.Blocks.COMPLETE)
                .requires(IrisStages.Blocks.WRITE, IrisStages.Blocks.MARKERS)
                .run(context -> {
                });
    }

    private static void write(BlockStageContext context, WrittenBlocks written) {
        IrisPlacement placement = IrisPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        PlacementWorld world = new PlacementWorld(context.access(), placement.states());
        ObjectBlock block = context.block();
        BlockPosition offset = prepared.transform(block.position());
        int anchorY = context.output(IrisStages.Anchor.FINAL).y();
        PlatformBlockState data = sourceState(block, placement, prepared);
        TileData tile = block.blockEntity().map(BlockEntities::tileOf).orElse(null);
        if (!config.getEdit().isEmpty()) {
            ObjectEdits.Edited edited = ObjectEdits.apply(config, data, tile, new RNG(context.random().nextLong()),
                    offset.x() + placement.x(), offset.y() + anchorY, offset.z() + placement.z(), placement.data());
            data = edited.data();
            tile = edited.tile();
        }
        data = prepared.rotate(data);
        int x = placement.x() + offset.x();
        int y = anchorY + offset.y();
        int z = placement.z() + offset.z();
        if (prepared.isWarped()) {
            x += prepared.warpOffset(offset.x() + placement.x(), y, offset.z() + placement.z());
            z += prepared.warpOffset(offset.z() + placement.z(), y, offset.x() + placement.x());
        }
        if (WorldVolume.isPainted(placement, prepared) && !B.isVineBlock(data)) {
            y = offset.y() + Math.floorDiv(placement.object().h, 2)
                    + prepared.terrain().getHighest(x, z, placement.object().getLoader(), config.isUnderwater());
        }
        boolean structurePiece = prepared.mode() == ObjectPlaceMode.STRUCTURE_PIECE;
        if (config.isMeld() && !structurePiece && !world.isSolid(x, y, z)) {
            return;
        }
        if (IrisProceduralBlocks.hasProperty(data, WATERLOGGED) && shouldWaterlog(world, config, placement.requestedY(), x, y, z)) {
            data = data.withProperty(WATERLOGGED, TRUE);
        }
        if (B.isVineBlock(data)) {
            data = attachVineFaces(world, data, x, y, z);
        }
        if (!shouldWrite(world, data, x, y, z, structurePiece)) {
            return;
        }
        PlacedBlock placed = world.set(x, y, z, data, tile);
        placement.objectBlockWritten(placed.position());
        written.add(placed);
    }

    /** The object block as Iris places it: debug air shows where smart boring bored, and leaves never decay where the placer prevents it. */
    private static PlatformBlockState sourceState(ObjectBlock block, IrisPlacement placement, PreparedPlacement prepared) {
        PlatformBlockState data = placement.states().toPlatform(block.state());
        IObjectPlacer terrain = prepared.terrain();
        if (terrain.isDebugSmartBore() && data.matches(IrisObject.States.VAIR)) {
            data = IrisObject.States.VAIR_DEBUG;
        }
        if (terrain.isPreventingDecay() && IrisProceduralBlocks.hasProperty(data, DISTANCE)
                && FALSE.equals(IrisProceduralBlocks.propertyValue(data, PERSISTENT))) {
            data = data.withProperty(PERSISTENT, TRUE);
        }
        return data;
    }

    private static boolean shouldWrite(PlacementWorld world, PlatformBlockState data, int x, int y, int z, boolean structurePiece) {
        if (data.isCustom()) {
            return true;
        }
        boolean vineIntoSolid = B.isVineBlock(data) && world.isSolid(x, y, z);
        return shouldPlaceObjectBlock(structurePiece, AIR_MATERIALS.contains(IrisObjectShaping.materialKey(data)), vineIntoSolid);
    }

    /** Air is written only by structure pieces, and a vine never replaces a solid block. */
    static boolean shouldPlaceObjectBlock(boolean structurePiece, boolean air, boolean vineIntoSolid) {
        return !vineIntoSolid && (structurePiece || !air);
    }

    /** Whether a waterloggable block lands in water a waterloggable or underwater placement keeps. */
    static boolean shouldWaterlog(PlacementWorld world, IrisObjectPlacement config, int requestedY, int x, int y, int z) {
        if (!(config.isWaterloggable() || config.isUnderwater())) {
            return false;
        }
        if (requestedY >= 0 && config.getCarvingSupport() == CarvingMode.CARVING_ONLY) {
            return false;
        }
        PlatformBlockState existing = world.get(x, y, z);
        return B.isWater(existing) || B.isWaterLogged(existing);
    }

    /** {@code vine} hanging from each neighboring solid block it has a face property for. */
    static PlatformBlockState attachVineFaces(PlacementWorld world, PlatformBlockState vine, int x, int y, int z) {
        PlatformBlockState attached = vine;
        for (String face : IrisProceduralBlocks.FACE_PROPERTIES) {
            if (!IrisProceduralBlocks.hasProperty(vine, face)) {
                continue;
            }
            int[] offset = IrisProceduralBlocks.faceOffset(face);
            PlatformBlockState facing = world.get(x + offset[0], y + offset[1], z + offset[2]);
            if (B.isSolid(facing) && !B.isVineBlock(facing)) {
                attached = attached.withProperty(face, TRUE);
            }
        }
        return attached;
    }

    /**
     * Each marker of the placement marks up to its maximum of the written blocks it targets, picked at random,
     * where the marker either allows anything above or finds two blocks of air.
     */
    private static void placeMarkers(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        if (config.getMarkers().isEmpty() || prepared.terrain().getEngine() == null) {
            return;
        }
        WrittenBlocks written = context.output(IrisStages.Blocks.WRITE);
        PlacementWorld world = new PlacementWorld(context.access(), placement.states());
        RNG markerRandom = new RNG(context.random().nextLong());
        int salt = 0;
        for (IrisObjectMarker markerConfig : config.getMarkers()) {
            IrisMarker marker = placement.object().getLoader().getMarkerLoader().load(markerConfig.getMarker());
            int markerSalt = salt++;
            if (marker == null) {
                continue;
            }
            KList<BlockPosition> candidates = candidates(written, markerConfig, markerConfig.getMark(placement.data()), placement);
            int remaining = markerConfig.getMaximumMarkers();
            for (BlockPosition position : candidates.shuffleCopy(markerRandom.nextParallelRNG(markerSalt))) {
                if (remaining <= 0) {
                    break;
                }
                if (!marker.isEmptyAbove() || hasAirAbove(world, position)) {
                    context.access().marker(ObjectMarkers.of(position, markerConfig.getMarker()));
                    remaining--;
                }
            }
        }
    }

    private static KList<BlockPosition> candidates(WrittenBlocks written, IrisObjectMarker markerConfig,
                                                   KList<PlatformBlockState> targets, IrisPlacement placement) {
        KList<BlockPosition> candidates = new KList<>();
        for (PlacedBlock block : written.blocks()) {
            PlatformBlockState state = placement.states().toPlatform(block.state());
            if (targets.stream().anyMatch(target -> marks(markerConfig, target, state))) {
                candidates.add(block.position());
            }
        }
        return candidates;
    }

    private static boolean marks(IrisObjectMarker markerConfig, PlatformBlockState target, PlatformBlockState state) {
        return markerConfig.isExact()
                ? target.matches(state)
                : IrisObjectShaping.materialKey(target).equals(IrisObjectShaping.materialKey(state));
    }

    private static boolean hasAirAbove(PlacementWorld world, BlockPosition position) {
        for (int above = 1; above <= MARKER_HEADROOM_BLOCKS; above++) {
            if (!B.isAir(world.get(position.x(), position.y() + above, position.z()))) {
                return false;
            }
        }
        return true;
    }
}
