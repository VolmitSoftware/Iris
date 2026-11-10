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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.StructurePlacementScope;
import art.arcane.iris.engine.framework.structure.ColumnHeights;
import art.arcane.iris.engine.framework.structure.StructureWorld;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureStamp;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.Mantle;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacementOutcome;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places the Iris structures of the dimension, its regions and biomes into the mantle for
 * {@link StructureModule}: their pieces, foundations, bores and carves, clearing object trees they
 * intersect. One per world. Its reach is the largest structure any placement can produce.
 */
public class StructurePlacing {
    private static final CarvedVoid CARVE_VOID = CarvedVoid.AIR;
    private static final NoiseStyle CARVE_NOISE = NoiseStyle.of("IRIS");
    private static final long CARVE_ROLL_SIGNATURE = 0x2A17L;
    private static final int REJECTED_PIECE = -1;

    private final PackWorld world;
    private final ModuleLog log;

    public StructurePlacing(PackWorld world, ModuleLog log) {
        this.world = world;
        this.log = log;
    }

    /** Places what originates in the chunk {@code context} generates into its mantle. */
    @ChunkCoordinates
    public void place(MantleStageContext context) {
        int x = context.chunkX();
        int z = context.chunkZ();
        for (StructurePlacement placement : StructurePlacementScope.placementsAt(world, x, z)) {
            if (!placement.hasIrisStructures()) {
                continue;
            }
            placeFromPlacement(context, placement, x, z);
        }
    }

    @ChunkCoordinates
    private void placeFromPlacement(MantleStageContext context, StructurePlacement placement, int cx, int cz) {
        IrisStructureLocator.ResolvedPlacement resolved = IrisStructureLocator.resolvePlacement(
                world, placement, cx, cz);
        if (resolved == null) {
            return;
        }

        boolean trace = log.debugging();
        if (trace) {
            log.debug("[StructTrace] ORIGIN chunk=" + cx + "," + cz + " structures=" + placement.structures()
                    + " anchor=" + placement.resolvedAnchor() + " band=" + placement.minHeight() + ".." + placement.maxHeight());
        }

        String key = resolved.structureKey();
        StructureStamp stamp = resolved.stamp();
        KList<AssembledPiece> pieces = resolvedPiecesOrNull(resolved, cx, cz);
        if (pieces == null) {
            return;
        }
        RNG rng = resolved.rng();
        int baseY = resolved.baseY();
        if (trace) {
            log.debug("[StructTrace] ASSEMBLED chunk=" + cx + "," + cz + " key=" + key + " baseY=" + baseY + " pieces=" + pieces.size());
        }

        if (!placement.isAnchoredUnderground()) {
            clearIntersectingObjectTrees(context, resolved);
        }

        StructureTerrain terrain = placement.resolvedTerrain();
        StructureTerrainMode terrainMode = terrain.resolvedMode();
        if (terrainMode == StructureTerrainMode.FORCE_CARVE) {
            forceCarveStructure(context, pieces, terrain);
        } else if (terrainMode == StructureTerrainMode.BORE) {
            boreStructure(context, pieces, terrain);
        } else if (terrainMode != StructureTerrainMode.PRESERVE
                && terrainMode != StructureTerrainMode.SOURCE) {
            throw new IllegalStateException("Iris assembly terrain mode " + terrainMode
                    + " is not implemented for structure '" + key + "'");
        }
        ObjectPlaceMode mode = stamp.placeMode();
        int failedPieces = 0;
        StructureStilts stilt = placement.stilt().orElse(null);
        ColumnHeights foundationColumns = stilt == null
                ? null : new ColumnHeights(0);
        boolean supportNonOccluding = stilt != null && stilt.supportNonOccluding();
        if (placement.isAnchoredUnderground()) {
            ObjectPlaceMode undergroundMode = mode.placeMode().has(PlaceTrait.CAVE_ANCHORED)
                    ? mode : ObjectPlaceMode.STRUCTURE_PIECE;
            for (AssembledPiece p : pieces) {
                if (placeObject(context, key, stamp, p, undergroundMode, false,
                        foundationColumns, supportNonOccluding) == REJECTED_PIECE) {
                    failedPieces++;
                }
            }
        } else if (mode.placeMode().has(PlaceTrait.FREE_HEIGHT)) {
            for (AssembledPiece p : pieces) {
                if (placeObject(context, key, stamp, p, ObjectPlaceMode.STRUCTURE_PIECE, false,
                        foundationColumns, supportNonOccluding) == REJECTED_PIECE) {
                    failedPieces++;
                }
            }
        } else if (pieces.size() == 1) {
            if (placeObject(context, key, stamp, pieces.getFirst(), mode, true,
                    foundationColumns, supportNonOccluding) == REJECTED_PIECE) {
                failedPieces++;
            }
        } else {
            for (AssembledPiece p : pieces) {
                if (placeObject(context, key, stamp, p, ObjectPlaceMode.STRUCTURE_PIECE, false,
                        foundationColumns, supportNonOccluding) == REJECTED_PIECE) {
                    failedPieces++;
                }
            }
        }
        requireAppliedPieces(resolved, cx, cz, failedPieces);
        if (failedPieces == 0 && stilt != null) {
            placeFoundation(context, foundationColumns, stilt, rng, !placement.isAnchoredUnderground());
        }
    }

    private void placeFoundation(MantleStageContext context, ColumnHeights columns,
                                 StructureStilts settings, RNG rng,
                                 boolean surfaceStructure) {
        PaletteSpec palette = settings.palette().orElseThrow(
                () -> new NullPointerException("Structure stilt palette must not be null"));
        BlockRegistry blocks = world.resources().blocks();
        PaletteSampler picker = blocks.palette(palette, rng.getSeed());
        Mantle mantle = context.mantle();
        PlacementTerrain terrain = context.placementTerrain();
        RunningWorld running = StructureWorld.running(world);
        int mantleOffset = world.minWorldY();
        int maxDepth = Math.max(1, settings.maxDepth());
        columns.forEach((column, foundationY) -> {
            int worldX = StructureFoundationPlanner.unpackX(column);
            int worldZ = StructureFoundationPlanner.unpackZ(column);
            BlockState foundationState = mantle.blocks().get(worldX, foundationY, worldZ);
            if (foundationState == null || !blocks.hasTrait(foundationState, BlockTrait.SOLID)) {
                return;
            }
            int terrainHeight = running.layout().surfaceHeight(worldX, worldZ);
            int groundY = StructureFoundationPlanner.findGroundY(
                    foundationY, maxDepth, 0,
                    y -> {
                        BlockState overlay = mantle.blocks().get(worldX, y, worldZ);
                        boolean carved = mantle.carved().get(worldX, y, worldZ) != null;
                        return surfaceStructure
                                ? StructureFoundationPlanner.isSurfaceSupportBoundary(
                                        overlay, blocks, carved, y, terrainHeight)
                                : StructureFoundationPlanner.isGroundSolid(
                                        overlay, blocks, carved, y, terrainHeight);
                    });
            if (groundY == StructureFoundationPlanner.NO_GROUND) {
                return;
            }
            StructureFoundationPlanner.fillSupportColumn(foundationY, groundY, y -> {
                writeFoundationSupport(terrain, mantle, picker, worldX, y, worldZ, mantleOffset);
            });
        });
    }

    /** Writes the stilt palette's block at the column's mantle height, clearing any carve there. */
    static void writeFoundationSupport(PlacementTerrain terrain, Mantle mantle, PaletteSampler picker, int worldX,
                                       int mantleY, int worldZ, int mantleOffset) {
        int worldY = mantleY + mantleOffset;
        BlockState support = picker.at(worldX, worldY, worldZ);
        if (support == null) {
            throw new IllegalStateException("Structure stilt palette resolved no block at "
                    + worldX + "," + worldY + "," + worldZ);
        }
        mantle.carved().remove(worldX, mantleY, worldZ);
        terrain.set(worldX, mantleY, worldZ, support);
    }

    static KList<AssembledPiece> resolvedPiecesOrNull(
            IrisStructureLocator.ResolvedPlacement resolved,
            int chunkX,
            int chunkZ
    ) {
        if (resolved == null) {
            return null;
        }
        KList<AssembledPiece> pieces = resolved.pieces();
        if (!IrisStructureLocator.requirePlacementOutput(
                resolved.placement(), resolved.structureKey(), chunkX, chunkZ,
                pieces != null && !pieces.isEmpty(), "placement application received no assembled pieces")) {
            return null;
        }
        return pieces;
    }

    static void requireAppliedPieces(IrisStructureLocator.ResolvedPlacement resolved,
                                     int chunkX, int chunkZ, int failedPieces) {
        IrisStructureLocator.requirePlacementOutput(
                resolved.placement(), resolved.structureKey(), chunkX, chunkZ, failedPieces == 0,
                "object placement rejected " + failedPieces + " of " + resolved.pieces().size()
                        + " assembled piece(s)");
    }

    private void boreStructure(MantleStageContext context, KList<AssembledPiece> pieces,
                               StructureTerrain terrain) {
        int[] bounds = computePieceBounds(pieces);
        if (bounds == null) {
            return;
        }
        int pad = Math.max(0, terrain.horizontalPadding());
        int minX = bounds[0] - pad;
        int minY = bounds[1] - Math.max(0, terrain.floorPadding());
        int minZ = bounds[2] - pad;
        int maxX = bounds[3] + pad;
        int maxY = bounds[4] + Math.max(0, terrain.ceilingPadding());
        int maxZ = bounds[5] + pad;
        int worldMin = world.minWorldY() + 1;
        int worldMax = world.minWorldY() + StructureWorld.height(world) - 1;
        minY = Math.max(minY, worldMin);
        maxY = Math.min(maxY, worldMax);
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }
        int mantleOffset = world.minWorldY();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    carve(context, bx, by - mantleOffset, bz);
                }
            }
        }
    }

    private void forceCarveStructure(MantleStageContext context, KList<AssembledPiece> pieces,
                                     StructureTerrain terrain) {
        int[] bounds = computePieceBounds(pieces);
        if (bounds == null) {
            return;
        }
        StructureCarveShape shape = terrain.resolvedShape();
        int margin = Math.max(0, terrain.horizontalPadding());
        int head = Math.max(0, terrain.ceilingPadding());
        int floorCut = Math.max(0, terrain.floorPadding());
        double strength = terrain.resolvedErosionStrength();
        int mantleOffset = world.minWorldY();
        int worldMin = world.minWorldY() + 1;
        int worldMax = world.minWorldY() + StructureWorld.height(world) - 1;
        int upExtension = shape.maximumCeilingExtension(head, strength);

        if (shape == StructureCarveShape.BOX) {
            carveOverboreBox(context, bounds, margin, head, floorCut, mantleOffset, worldMin, worldMax);
            return;
        }

        long work = overboreCandidateVolume(bounds, margin, upExtension, floorCut);
        StructureCarvingFootprint footprint = StructureCarvingFootprint.fromColumns(
                sink -> StructurePieceColumns.emit(pieces, this::blocksOf, sink), margin, Integer.MAX_VALUE);
        if (footprint == null) {
            throw new IllegalStateException("Structure force-carve footprint is empty or exceeds addressable memory");
        }

        double frequency = terrain.resolvedErosionFrequency();
        boolean eroded = shape == StructureCarveShape.ERODED && strength > 0D;
        NoiseSampler blob = null;
        NoiseSampler roll = null;
        if (eroded) {
            long noiseSeed = StructureWorld.placementSeed(world) + CacheKey.key(bounds[0], bounds[2]);
            blob = world.resources().noise().sampler(CARVE_NOISE, noiseSeed);
            roll = world.resources().noise().sampler(CARVE_NOISE, noiseSeed + CARVE_ROLL_SIGNATURE);
        }

        if (log.debugging()) {
            log.info("Overbore carving structure cavern: shape=" + shape + " pieces=" + pieces.size()
                    + " footprint=" + footprint.width() + "x" + footprint.depth()
                    + " margin=" + margin + " head=" + head + " floorCut=" + floorCut + " work=" + work);
        }

        double sideReachSquared = (double) margin * margin;
        double downReach = Math.max(1D, floorCut);
        for (int bz = footprint.minZ(); bz <= footprint.maxZ(); bz++) {
            int footprintIndex = footprint.indexAt(footprint.minX(), bz);
            for (int bx = footprint.minX(); bx <= footprint.maxX(); bx++, footprintIndex++) {
                long horizontalDistanceSquared = footprint.distanceSquaredAt(footprintIndex);
                if (horizontalDistanceSquared < 0L || horizontalDistanceSquared > sideReachSquared) {
                    continue;
                }
                double normalizedHorizontalDistanceSquared = sideReachSquared == 0D
                        ? 0D : horizontalDistanceSquared / sideReachSquared;
                int sourceMinY = footprint.sourceMinYAt(footprintIndex);
                int sourceMaxY = footprint.sourceMaxYAt(footprintIndex);
                double upReach = eroded
                        ? StructureCarveEnvelope.erodedUpReach(roll, frequency, strength, bx, bz, head)
                        : Math.max(1D, head);
                int columnUpExtension = eroded
                        ? (int) Math.ceil(upReach) : head;
                int minY = Math.max(worldMin, sourceMinY - floorCut);
                int maxY = Math.min(worldMax, sourceMaxY + columnUpExtension);
                for (int by = minY; by <= maxY; by++) {
                    double normalizedY = StructureCarveEnvelope.normalizedVerticalDistance(
                            by, sourceMinY, sourceMaxY, upReach, downReach);
                    double distanceSquared = normalizedHorizontalDistanceSquared
                            + normalizedY * normalizedY;
                    if (distanceSquared <= 0D) {
                        carve(context, bx, by - mantleOffset, bz);
                        continue;
                    }
                    if (distanceSquared > 1D) {
                        continue;
                    }
                    if (!eroded) {
                        carve(context, bx, by - mantleOffset, bz);
                        continue;
                    }
                    double noise = blob.sample(bx * frequency, by * frequency, bz * frequency);
                    if (StructureCarveEnvelope.shouldCarveOverboreCell(
                            shape, distanceSquared, noise, strength)) {
                        carve(context, bx, by - mantleOffset, bz);
                    }
                }
            }
        }
    }

    private void carveOverboreBox(MantleStageContext context, int[] bounds, int margin, int head, int floorCut,
                                  int mantleOffset, int worldMin, int worldMax) {
        int minX = bounds[0] - margin;
        int minY = Math.max(worldMin, bounds[1] - floorCut);
        int minZ = bounds[2] - margin;
        int maxX = bounds[3] + margin;
        int maxY = Math.min(worldMax, bounds[4] + head);
        int maxZ = bounds[5] + margin;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    carve(context, bx, by - mantleOffset, bz);
                }
            }
        }
    }

    /** Carves the block open for good: the void is planned and any block a placement wrote there is taken back. */
    private static void carve(MantleStageContext context, int x, int mantleY, int z) {
        context.placementTerrain().clearBlock(x, mantleY, z);
        context.mantle().carved().set(x, mantleY, z, CARVE_VOID);
    }

    private static long overboreCandidateVolume(int[] bounds, int margin, int upExtension,
                                                int floorCut) {
        return inclusiveVolume(
                bounds[0] - margin, bounds[1] - floorCut, bounds[2] - margin,
                bounds[3] + margin, bounds[4] + upExtension, bounds[5] + margin);
    }

    private static long inclusiveVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = (long) maxZ - minZ + 1L;
        if (width <= 0L || height <= 0L || depth <= 0L
                || width > Long.MAX_VALUE / height
                || width * height > Long.MAX_VALUE / depth) {
            return Long.MAX_VALUE;
        }
        return width * height * depth;
    }

    private void clearIntersectingObjectTrees(MantleStageContext context, IrisStructureLocator.ResolvedPlacement resolved) {
        BlockRegistry blocks = world.resources().blocks();
        int mantleOffset = world.minWorldY();
        int worldHeight = StructureWorld.height(world);
        Map<PlacementRecord, ObjectMarkerBounds> intersectingObjects = new HashMap<>();
        for (AssembledPiece piece : resolved.pieces()) {
            for (ObjectBlock block : objectOf(piece).geometry().blocks()) {
                if (!isOccupiedStructureState(block.state(), blocks)) {
                    continue;
                }
                BlockPosition rotated = piece.turn().rotate(block.position());
                int y = piece.y() - mantleOffset + rotated.y();
                if (y >= 0 && y < worldHeight) {
                    collectIntersectingObjectTree(
                            context, piece.x() + rotated.x(), y,
                            piece.z() + rotated.z(), intersectingObjects);
                }
            }
        }
        for (Map.Entry<PlacementRecord, ObjectMarkerBounds> entry : intersectingObjects.entrySet()) {
            clearRecordOwnedObject(context, entry.getKey(), entry.getValue(), worldHeight);
        }
    }

    static boolean isOccupiedStructureState(BlockState state, BlockRegistry blocks) {
        return state != null && !blocks.hasTrait(state, BlockTrait.AIR);
    }

    private void collectIntersectingObjectTree(MantleStageContext context, int x, int y, int z,
                                               Map<PlacementRecord, ObjectMarkerBounds> intersectingObjects) {
        BlockState state = context.mantle().blocks().get(x, y, z);
        if (state == null || !isTreeBlock(state)) {
            return;
        }
        Optional<PlacementRecord> record = StructureWorld.running(world).placementAt(x, y, z);
        if (isOrdinaryObjectRecord(record)) {
            intersectingObjects.computeIfAbsent(record.get(), ignored -> new ObjectMarkerBounds())
                    .include(x, y, z);
        }
    }

    private boolean isTreeBlock(BlockState state) {
        BlockRegistry blocks = world.resources().blocks();
        return blocks.hasTrait(state, BlockTrait.LOG) || blocks.hasTrait(state, BlockTrait.LEAVES);
    }

    private void clearRecordOwnedObject(MantleStageContext context, PlacementRecord record,
                                        ObjectMarkerBounds intersections, int worldHeight) {
        Optional<PlaceableObject> object = world.resources().objects().object(record.object());
        if (object.isEmpty()) {
            return;
        }
        int horizontalSpan = Math.max(1, Math.max(object.get().width(), object.get().depth()));
        int verticalSpan = Math.max(1, object.get().height());
        ObjectMarkerBounds search = intersections.expand(horizontalSpan - 1, verticalSpan - 1, worldHeight);
        BlockBox prepared = context.mantle().bounds();
        RunningWorld running = StructureWorld.running(world);
        PlacementTerrain terrain = context.placementTerrain();
        List<ObjectBlockPosition> positions = new ArrayList<>();
        for (int x = search.minX; x <= search.maxX; x++) {
            for (int y = search.minY; y <= search.maxY; y++) {
                for (int z = search.minZ; z <= search.maxZ; z++) {
                    if (prepared.contains(x, y, z) && running.placementAt(x, y, z).filter(record::equals).isPresent()) {
                        positions.add(new ObjectBlockPosition(x, y, z));
                    }
                }
            }
        }
        for (ObjectBlockPosition position : positions) {
            terrain.clearBlock(position.x(), position.y(), position.z());
            terrain.clearAttached(position.x(), position.y(), position.z());
        }
    }

    static boolean isOrdinaryObjectRecord(Optional<PlacementRecord> record) {
        return record.isPresent() && record.get().structure().isEmpty();
    }

    static ObjectMarkerBounds markerSearchBounds(int minX, int minY, int minZ,
                                                 int maxX, int maxY, int maxZ,
                                                 int horizontalSpan, int verticalSpan,
                                                 int worldHeight) {
        ObjectMarkerBounds bounds = new ObjectMarkerBounds();
        bounds.minX = minX;
        bounds.minY = minY;
        bounds.minZ = minZ;
        bounds.maxX = maxX;
        bounds.maxY = maxY;
        bounds.maxZ = maxZ;
        return bounds.expand(
                Math.max(0, horizontalSpan - 1), Math.max(0, verticalSpan - 1), worldHeight);
    }

    private List<StructurePieceColumns.PieceBlock> blocksOf(AssembledPiece piece) {
        BlockRegistry blocks = world.resources().blocks();
        List<StructurePieceColumns.PieceBlock> pieceBlocks = new ArrayList<>();
        for (ObjectBlock block : objectOf(piece).geometry().blocks()) {
            pieceBlocks.add(new StructurePieceColumns.PieceBlock(
                    block.position(), blocks.hasTrait(block.state(), BlockTrait.AIR)));
        }
        return pieceBlocks;
    }

    /** The object of {@code piece}, loaded from the world's pack. */
    private PlaceableObject objectOf(AssembledPiece piece) {
        return world.resources().objects().object(piece.object())
                .orElseThrow(() -> new IllegalStateException("Structure piece object " + piece.object() + " is missing"));
    }

    private int[] computePieceBounds(KList<AssembledPiece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (AssembledPiece p : pieces) {
            minX = Math.min(minX, p.minX());
            minY = Math.min(minY, p.minY());
            minZ = Math.min(minZ, p.minZ());
            maxX = Math.max(maxX, p.maxX());
            maxY = Math.max(maxY, p.maxY());
            maxZ = Math.max(maxZ, p.maxZ());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /** A placement of the piece object {@code objectKey} whose containers draw from the stamp's loot tables. */
    public static ObjectPlacement lootPlacement(StructureStamp stamp, String objectKey) {
        return lootPlacementBuilder(stamp, objectKey).build();
    }

    private static ObjectPlacement.Builder lootPlacementBuilder(StructureStamp stamp, String objectKey) {
        ObjectPlacement.Builder placement = ObjectPlacement.builder().place(List.of(objectKey));
        if (stamp.loot().isEmpty()) {
            return placement;
        }
        List<ObjectLoot> loot = new ArrayList<>();
        for (String lootTable : stamp.loot()) {
            if (lootTable != null && !lootTable.isBlank()) {
                loot.add(new ObjectLoot(lootTable, 1));
            }
        }
        return placement.loot(loot);
    }

    private int placeObject(MantleStageContext context, String structureKey, StructureStamp stamp, AssembledPiece p,
                            ObjectPlaceMode mode, boolean sampledHeight, ColumnHeights foundationColumns,
                            boolean supportNonOccluding) {
        PlaceableObject object = objectOf(p);
        String objectKey = object.name();
        ObjectPlacement.Builder builder = lootPlacementBuilder(stamp, objectKey).mode(mode).rotation(moduleRotation(p.turn()));
        if (!stamp.edits().isEmpty()) {
            builder.edit(new KList<>(stamp.edits()));
        }
        if (!mode.placeMode().has(PlaceTrait.FREE_HEIGHT)) {
            builder.forcePlace(true);
        }
        ObjectPlacement config = builder.build();
        PlacementTerrain terrain = context.placementTerrain();
        ObjectRun run = sampledHeight
                ? ObjectRun.automatic(object, config, p.x(), p.z())
                : ObjectRun.of(object, config, p.x(), p.y() - world.minWorldY(), p.z());
        PlacementOutcome outcome = run.placeIn(world, terrain);
        if (!outcome.isPlaced()) {
            return REJECTED_PIECE;
        }
        recordPieceBlocks(terrain, run, structureKey, p, objectKey, foundationColumns, supportNonOccluding);
        return run.anchorY().orElseThrow(() -> new IllegalStateException(
                "The placement graph placed " + objectKey + " without anchoring it"));
    }

    /** Notes each block the piece wrote as a base cell of the foundation, and marks its storage blocks as the structure's. */
    private void recordPieceBlocks(PlacementTerrain terrain, ObjectRun run, String structureKey, AssembledPiece piece,
                                   String objectKey, ColumnHeights foundationColumns, boolean supportNonOccluding) {
        BlockRegistry blocks = world.resources().blocks();
        PlacementRecord record = structurePlacementRecord(structureKey, piece, objectKey);
        for (BlockPosition position : run.objectBlocks()) {
            BlockState state = terrain.get(position.x(), position.y(), position.z());
            StructureFoundationPlanner.recordBaseCell(
                    foundationColumns, position.x(), position.y(), position.z(), state, blocks,
                    supportNonOccluding);
            if (record != null && shouldWriteStructureMarker(state, blocks)) {
                terrain.recordPlacement(position.x(), position.y(), position.z(), record);
            }
        }
    }

    /** The placement rotation that turns a placed piece the way {@code turn} does. */
    public static ObjectRotation moduleRotation(PieceTurn turn) {
        return ObjectRotation.of(0, turn.yDegrees(), 0);
    }

    static boolean shouldWriteStructureMarker(BlockState state, BlockRegistry blocks) {
        return state != null && blocks.hasTrait(state, BlockTrait.STORAGE);
    }

    static int structurePlacementId(String structureKey, String objectKey, int x, int y, int z) {
        int hash = 17;
        hash = 31 * hash + structureKey.hashCode();
        hash = 31 * hash + objectKey.hashCode();
        hash = 31 * hash + x;
        hash = 31 * hash + y;
        hash = 31 * hash + z;
        return hash & Integer.MAX_VALUE;
    }

    private PlacementRecord structurePlacementRecord(String structureKey, AssembledPiece piece, String objectKey) {
        if (structureKey == null || structureKey.isBlank() || objectKey == null || objectKey.isBlank()) {
            return null;
        }
        PackResources pack = world.resources();
        int placementId = structurePlacementId(structureKey, objectKey, piece.x(), piece.y(), piece.z());
        return PlacementRecord.ofPiece(pack.idOf(objectKey), placementId, pack.idOf(structureKey));
    }

    /** How far, in blocks along X and Z, placements may write beyond their chunk. */
    public static int reach(PackSettings settings) {
        PackResources pack = settings.resources();
        List<StructureAssembly> providers = pack.services(StructureAssembly.class);
        int maxBlocks = 0;
        for (var declared : settings.declared(StructurePlacementSettings.class)) {
            maxBlocks = Math.max(maxBlocks, maxBlocksFrom(pack, providers, declared.value().placements()));
        }
        return maxBlocks;
    }

    private static int maxBlocksFrom(PackResources pack, List<StructureAssembly> providers, List<StructurePlacement> placements) {
        int max = 0;
        for (StructurePlacement placement : placements) {
            if (!placement.hasIrisStructures()) {
                continue;
            }
            StructureTerrain terrain = placement.resolvedTerrain();
            int carvePadding = terrain.resolvedMode() == StructureTerrainMode.FORCE_CARVE
                    || terrain.resolvedMode() == StructureTerrainMode.BORE
                    ? Math.max(0, terrain.horizontalPadding()) : 0;
            for (String key : placement.structures()) {
                var structure = pack.idOf(key);
                StructureAssembly provider = IrisStructureLocator.providerOf(providers, pack, structure);
                if (provider != null) {
                    max = Math.max(max, Math.max(1, provider.reachChunks(pack, structure).orElse(1)) * 16 + carvePadding);
                }
            }
        }
        return max;
    }

    static final class ObjectMarkerBounds {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void include(int x, int y, int z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        ObjectMarkerBounds expand(int horizontal, int vertical, int worldHeight) {
            ObjectMarkerBounds expanded = new ObjectMarkerBounds();
            expanded.minX = minX - horizontal;
            expanded.minY = Math.max(0, minY - vertical);
            expanded.minZ = minZ - horizontal;
            expanded.maxX = maxX + horizontal;
            expanded.maxY = Math.min(worldHeight - 1, maxY + vertical);
            expanded.maxZ = maxZ + horizontal;
            return expanded;
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        int minX() {
            return minX;
        }

        int minY() {
            return minY;
        }

        int minZ() {
            return minZ;
        }

        int maxX() {
            return maxX;
        }

        int maxY() {
            return maxY;
        }

        int maxZ() {
            return maxZ;
        }
    }

    private record ObjectBlockPosition(int x, int y, int z) {
    }
}
