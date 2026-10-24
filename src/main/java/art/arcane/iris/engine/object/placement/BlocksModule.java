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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.blocks.ObjectMarkers;
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.placement.MarkerRule;
import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.placement.PlacementTerrain;
import art.arcane.iris.builtin.prepare.PreparedPlacement;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.object.BlockEntityData;
import com.volmit.object.ObjectBlock;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.PlacementStageContext;
import com.volmit.geometry.BlockPosition;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Module {@code iris:blocks}: writes the object's blocks where the placement puts them, then its markers.
 * Each block is turned with the object, edited, warped, painted onto its column when the mode paints,
 * waterlogged in water and attached to the faces vines can hang from. Air is not written, except by
 * structure pieces, and neither is a vine into a solid block or a melded block into open space.
 */
public final class BlocksModule implements IrisModule {
    private static final Set<String> AIR_MATERIALS = Set.of("minecraft:air", "minecraft:cave_air");
    private static final BlockState VOID_AIR = new BlockState("minecraft:void_air");
    private static final BlockState VOID_AIR_DEBUG = new BlockState("minecraft:cobweb");
    private static final String DISTANCE = "distance";
    private static final String PERSISTENT = "persistent";
    static final String WATERLOGGED = "waterlogged";
    static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final int MARKER_HEADROOM_BLOCKS = 2;
    private static final Map<String, int[]> FACE_OFFSETS = Map.of(
            "north", new int[]{0, 0, -1}, "east", new int[]{1, 0, 0}, "south", new int[]{0, 0, 1},
            "west", new int[]{-1, 0, 0}, "up", new int[]{0, 1, 0}, "down", new int[]{0, -1, 0});
    private static final String[] FACE_PROPERTIES = {"north", "east", "south", "west", "up", "down"};

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:blocks", "Object blocks", PlacementModules.VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var graph = setup.stages();
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
        setup.registry().registerHeldType(ObjectReplace.class);
    }

    private static void write(BlockStageContext context, WrittenBlocks written) {
        ObjectPlacement placement = context.extension(ObjectPlacement.class).orElseThrow();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        PlacementWorld world = new PlacementWorld(context.access(), prepared.traits());
        ObjectBlock block = context.block();
        BlockPosition offset = prepared.transform(block.position());
        int anchorY = context.output(IrisStages.Anchor.FINAL).y();
        BlockState data = sourceState(block, placement, prepared);
        Optional<BlockEntityData> blockEntity = block.blockEntity();
        if (!settings.edit().isEmpty()) {
            ObjectEdits.Edited edited = ObjectEdits.apply(settings.edit(), data, blockEntity, new RNG(context.random().nextLong()),
                    offset.x() + placement.x(), offset.y() + anchorY, offset.z() + placement.z(),
                    ResolvedReplaces.of(context), placement);
            data = edited.data();
            blockEntity = edited.blockEntity();
        }
        data = prepared.rotate(data);
        int x = placement.x() + offset.x();
        int y = anchorY + offset.y();
        int z = placement.z() + offset.z();
        if (prepared.isWarped()) {
            x += prepared.warpOffset(offset.x() + placement.x(), y, offset.z() + placement.z());
            z += prepared.warpOffset(offset.z() + placement.z(), y, offset.x() + placement.x());
        }
        if (WorldVolume.isPainted(placement, prepared) && !world.has(data, BlockTrait.VINE)) {
            y = offset.y() + Math.floorDiv(placement.object().height(), 2)
                    + prepared.terrain().highest(x, z, settings.underwater());
        }
        boolean structurePiece = prepared.mode().has(PlaceTrait.RAW_STAMP);
        if (settings.meld() && !structurePiece && !world.isSolid(x, y, z)) {
            return;
        }
        if (data.properties().containsKey(WATERLOGGED) && shouldWaterlog(world, settings, placement.requestedY(), x, y, z)) {
            data = data.withProperty(WATERLOGGED, TRUE);
        }
        if (world.has(data, BlockTrait.VINE)) {
            data = attachVineFaces(world, data, x, y, z);
        }
        if (!shouldWrite(world, data, x, y, z, structurePiece)) {
            return;
        }
        world.set(x, y, z, data, blockEntity);
        BlockPosition position = new BlockPosition(x, y, z);
        placement.objectBlockWritten(position);
        written.add(position, data);
    }

    /** The object block as Iris places it: debug air shows where smart boring bored, and leaves never decay where the placer prevents it. */
    private static BlockState sourceState(ObjectBlock block, ObjectPlacement placement, PreparedPlacement prepared) {
        BlockState data = block.state();
        PlacementTerrain terrain = prepared.terrain();
        if (terrain.debugsSmartBore() && placement.matches(data, VOID_AIR)) {
            data = VOID_AIR_DEBUG;
        }
        if (terrain.preventsDecay() && data.properties().containsKey(DISTANCE)
                && FALSE.equals(data.properties().get(PERSISTENT))) {
            data = data.withProperty(PERSISTENT, TRUE);
        }
        return data;
    }

    private static boolean shouldWrite(PlacementWorld world, BlockState data, int x, int y, int z, boolean structurePiece) {
        if (world.has(data, BlockTrait.CUSTOM)) {
            return true;
        }
        boolean vineIntoSolid = world.has(data, BlockTrait.VINE) && world.isSolid(x, y, z);
        return shouldPlaceObjectBlock(structurePiece, AIR_MATERIALS.contains(data.material().toString()), vineIntoSolid);
    }

    /** Air is written only by structure pieces, and a vine never replaces a solid block. */
    static boolean shouldPlaceObjectBlock(boolean structurePiece, boolean air, boolean vineIntoSolid) {
        return !vineIntoSolid && (structurePiece || !air);
    }

    /** Whether a waterloggable block lands in water a waterloggable or underwater placement keeps. */
    static boolean shouldWaterlog(PlacementWorld world, PlacementSettings settings, int requestedY, int x, int y, int z) {
        if (!(settings.waterloggable() || settings.underwater())) {
            return false;
        }
        if (requestedY >= 0 && settings.carvingSupport() == CarvingMode.CARVING_ONLY) {
            return false;
        }
        BlockState existing = world.get(x, y, z);
        return world.has(existing, BlockTrait.WATER) || TRUE.equals(existing.properties().get(WATERLOGGED));
    }

    /** {@code vine} hanging from each neighboring solid block it has a face property for. */
    static BlockState attachVineFaces(PlacementWorld world, BlockState vine, int x, int y, int z) {
        BlockState attached = vine;
        for (String face : FACE_PROPERTIES) {
            if (!vine.properties().containsKey(face)) {
                continue;
            }
            int[] offset = FACE_OFFSETS.get(face);
            BlockState facing = world.get(x + offset[0], y + offset[1], z + offset[2]);
            if (world.has(facing, BlockTrait.SOLID) && !world.has(facing, BlockTrait.VINE)) {
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
        ObjectPlacement placement = context.extension(ObjectPlacement.class).orElseThrow();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        if (settings.markers().isEmpty() || !prepared.terrain().hasWorld()) {
            return;
        }
        WrittenBlocks written = context.output(IrisStages.Blocks.WRITE);
        PlacementWorld world = new PlacementWorld(context.access(), prepared.traits());
        RNG markerRandom = new RNG(context.random().nextLong());
        int salt = 0;
        for (MarkerRule rule : settings.markers()) {
            Optional<SpawnMarker> marker = Optional.ofNullable(SpawnResources.marker(placement.resources(), rule.marker()));
            int markerSalt = salt++;
            if (marker.isEmpty()) {
                continue;
            }
            KList<BlockPosition> candidates = candidates(written, rule, placement);
            int remaining = rule.maximum();
            for (BlockPosition position : candidates.shuffleCopy(markerRandom.nextParallelRNG(markerSalt))) {
                if (remaining <= 0) {
                    break;
                }
                if (!marker.get().emptyAbove() || hasAirAbove(world, position)) {
                    context.access().marker(ObjectMarkers.of(position, rule.marker()));
                    remaining--;
                }
            }
        }
    }

    private static KList<BlockPosition> candidates(WrittenBlocks written, MarkerRule rule, ObjectPlacement placement) {
        KList<BlockPosition> candidates = new KList<>();
        written.forEach((position, state) -> {
            if (rule.targets().stream().anyMatch(target -> marks(placement, rule, target, state))) {
                candidates.add(position);
            }
        });
        return candidates;
    }

    private static boolean marks(ObjectPlacement placement, MarkerRule rule, BlockState target, BlockState state) {
        return rule.exact()
                ? placement.matches(target, state)
                : target.material().equals(state.material());
    }

    private static boolean hasAirAbove(PlacementWorld world, BlockPosition position) {
        for (int above = 1; above <= MARKER_HEADROOM_BLOCKS; above++) {
            if (!world.has(world.get(position.x(), position.y() + above, position.z()), BlockTrait.AIR)) {
                return false;
            }
        }
        return true;
    }
}
