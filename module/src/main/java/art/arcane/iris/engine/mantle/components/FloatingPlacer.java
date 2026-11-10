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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.modules.floating.IslandAnchor;
import art.arcane.iris.modules.floating.IslandFootprint;
import art.arcane.iris.modules.objects.ForwardingTerrain;
import art.arcane.iris.modules.objects.TerrainTransaction;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.generation.GenerationFailures;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.PackWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places the objects of floating islands through the pack's placement stages into the mantle of the chunk being
 * generated: floating free, or against an island's top or underside, where nothing is written unless every block lands
 * where the island lets it. Each object's blocks are recorded as its placement.
 */
final class FloatingPlacer {
    private static final ObjectRotation NO_ROTATION = ObjectRotation.of(0, 0, 0);
    private static final Set<String> AIR_MATERIALS = Set.of("minecraft:air", "minecraft:cave_air");

    private final PackWorld world;
    private final BlockRegistry blocks;
    private final ModuleLog log;

    FloatingPlacer(PackWorld world, ModuleLog log) {
        this.world = world;
        this.blocks = world.resources().blocks();
        this.log = log;
    }

    /** Where {@code object} stands: its solid columns, its tallest column and that column's lowest solid block. */
    IslandFootprint footprint(PlaceableObject object) {
        Map<Long, int[]> columns = new HashMap<>();
        for (ObjectBlock block : object.geometry().blocks()) {
            if (!blocks.hasTrait(block.state(), BlockTrait.SOLID)) {
                continue;
            }
            BlockPosition position = block.position();
            int[] column = columns.computeIfAbsent(IslandFootprint.column(position.x(), position.z()),
                    ignored -> new int[]{position.y(), 0});
            column[0] = Math.min(column[0], position.y());
            column[1]++;
        }
        if (columns.isEmpty()) {
            return new IslandFootprint(0, 0, object.center().y(), new long[0]);
        }
        long tallest = tallestColumn(columns);
        long[] footprint = columns.keySet().stream().mapToLong(Long::longValue).toArray();
        return new IslandFootprint(IslandFootprint.columnX(tallest), IslandFootprint.columnZ(tallest), columns.get(tallest)[0], footprint);
    }

    /** The column with the most solid blocks; the lowest bottom wins a tie. */
    private static long tallestColumn(Map<Long, int[]> columns) {
        long best = 0L;
        int tallestCount = 0;
        int tallestLow = Integer.MAX_VALUE;
        for (Map.Entry<Long, int[]> entry : columns.entrySet()) {
            int low = entry.getValue()[0];
            int count = entry.getValue()[1];
            if (count > tallestCount || (count == tallestCount && low < tallestLow)) {
                tallestCount = count;
                tallestLow = low;
                best = entry.getKey();
            }
        }
        return best;
    }

    /** Places {@code object} floating free at the column, into the chunk {@code context} generates. */
    void placeFloating(MantleStageContext context, PlaceableObject object, int worldX, int worldZ, ObjectPlacement placement,
                       RNG random) {
        ObjectPlacement floating = placement.toBuilder().place(List.of(object.name())).build();
        int id = random.i(0, Integer.MAX_VALUE);
        try {
            place(ObjectRun.automatic(object, floating, worldX, worldZ), context.placementTerrain(), object, id);
        } catch (Throwable failure) {
            GenerationFailures.skipPlacement(log, failure, "floating object", object.name(), "at " + worldX + "," + worldZ);
        }
    }

    /**
     * Places {@code object} on an island's top, its lowest block at {@code y}; nothing is written unless every block it
     * writes is one {@code anchor} lets it write.
     */
    void placeOnTop(MantleStageContext context, PlaceableObject object, int worldX, int y, int worldZ, ObjectPlacement placement,
                    IslandAnchor anchor, RNG random) {
        placeAgainstIsland(context, object, worldX, y, worldZ, placement, NO_ROTATION, anchor, random, "anchored");
    }

    /** Places {@code object} upside down under an island, turned {@code yRotation} degrees about Y, as {@link #placeOnTop} does on top. */
    void placeUnder(MantleStageContext context, PlaceableObject object, int worldX, int y, int worldZ, int yRotation,
                    ObjectPlacement placement, IslandAnchor anchor, RNG random) {
        placeAgainstIsland(context, object, worldX, y, worldZ, placement, ObjectRotation.xFlip180WithY(yRotation), anchor, random,
                "inverted");
    }

    private void placeAgainstIsland(MantleStageContext context, PlaceableObject object, int worldX, int y, int worldZ,
                                    ObjectPlacement placement, ObjectRotation rotation, IslandAnchor anchor, RNG random, String kind) {
        ObjectPlacement anchored = placement.toBuilder()
                .place(List.of(object.name()))
                .mode(placement.mode().onFloatingIsland())
                .translate(new ObjectTranslate())
                .rotation(rotation)
                .forcePlace(true)
                .bottom(false)
                .build();
        IslandTransaction transaction = new IslandTransaction(new IslandTerrain(context.placementTerrain(), anchor), anchor);
        int id = random.i(0, Integer.MAX_VALUE);
        try {
            boolean placed = place(ObjectRun.of(object, anchored, worldX, y, worldZ), transaction, object, id);
            if (placed) {
                transaction.commit();
            } else {
                transaction.discard();
            }
        } catch (Throwable failure) {
            transaction.discard();
            GenerationFailures.skipPlacement(log, failure, kind + " floating object", object.name(), "at " + worldX + "," + y + "," + worldZ);
        }
    }

    /** Places the run and records the object's placement beside each block it wrote that is not air. */
    private boolean place(ObjectRun run, PlacementTerrain terrain, PlaceableObject object, int id) {
        if (!run.placeIn(world, terrain).isPlaced()) {
            return false;
        }
        PlacementRecord placement = PlacementRecord.of(world.resources().idOf(object.name()), id);
        for (BlockPosition position : run.objectBlocks()) {
            if (placesBlock(terrain.get(position.x(), position.y(), position.z()))) {
                terrain.recordPlacement(position.x(), position.y(), position.z(), placement);
            }
        }
        return true;
    }

    private boolean placesBlock(BlockState state) {
        return blocks.hasTrait(state, BlockTrait.CUSTOM) || !AIR_MATERIALS.contains(state.material().toString());
    }

    /**
     * The mantle as an object placed against an island sees it: heights are the island face's, nothing is under water,
     * and only the blocks the anchor lets the object write are written.
     */
    private static final class IslandTerrain extends ForwardingTerrain {
        private final IslandAnchor anchor;

        private IslandTerrain(PlacementTerrain delegate, IslandAnchor anchor) {
            super(delegate);
            this.anchor = anchor;
        }

        @Override
        public int highest(int x, int z, boolean ignoreFluid) {
            return anchor.faceY(x, z);
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            if (anchor.writes(x, y, z)) {
                delegate().set(x, y, z, state);
            }
        }

        @Override
        public void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
            if (anchor.writes(x, y, z)) {
                delegate().set(x, y, z, state, blockEntity);
            }
        }

        @Override
        public void recordPlacement(int x, int y, int z, PlacementRecord placement) {
            if (anchor.writes(x, y, z)) {
                delegate().recordPlacement(x, y, z, placement);
            }
        }

        @Override
        public void mark(PlacedMarker marker) {
            BlockPosition position = marker.position();
            if (anchor.writes(position.x(), position.y(), position.z())) {
                delegate().mark(marker);
            }
        }
    }

    /** Writes an object placed against an island only when the anchor lets it write every block. */
    private static final class IslandTransaction extends TerrainTransaction {
        private final IslandAnchor anchor;

        private IslandTransaction(PlacementTerrain delegate, IslandAnchor anchor) {
            super(delegate);
            this.anchor = anchor;
        }

        @Override
        protected boolean accepts(int x, int y, int z) {
            return anchor.writes(x, y, z);
        }
    }
}
