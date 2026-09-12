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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructurePlacementScope;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructureCarveShape;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@ComponentFlag(ReservedFlag.JIGSAW)
public class IrisStructureComponent extends IrisMantleComponent {
    private static final MatterCavern CARVE_CAVERN = new MatterCavern(true, "", (byte) 3);

    public IrisStructureComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.JIGSAW, 4);
    }

    @Override
    @ChunkCoordinates
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        if (!complex.allowsNewGenerationChunk(x, z)) {
            return;
        }
        for (IrisStructurePlacement placement : StructurePlacementScope.placementsAt(
                getEngineMantle().getEngine(), x, z)) {
            if (!placement.hasIrisStructures()) {
                continue;
            }
            placeFromPlacement(writer, placement, x, z, complex);
        }
    }

    @ChunkCoordinates
    private void placeFromPlacement(
            MantleWriter writer,
            IrisStructurePlacement placement,
            int cx,
            int cz,
            IrisComplex complex
    ) {
        IrisStructureLocator.ResolvedPlacement resolved = IrisStructureLocator.resolvePlacement(
                getEngineMantle().getEngine(), placement, cx, cz);
        if (resolved == null) {
            return;
        }

        boolean trace = IrisSettings.get().getGeneral().isDebug();
        if (trace) {
            IrisLogging.debug("[StructTrace] ORIGIN chunk=" + cx + "," + cz + " structures=" + placement.getStructures()
                    + " anchor=" + placement.resolvedAnchor() + " band=" + placement.getMinHeight() + ".." + placement.getMaxHeight());
        }

        String key = resolved.structureKey();
        IrisStructure structure = resolved.structure();
        KList<PlacedStructurePiece> pieces = resolvedPiecesOrNull(resolved, cx, cz);
        if (pieces == null) {
            return;
        }
        IrisStructureTerrain terrain = placement.resolvedTerrain();
        int[] bounds = computePieceBounds(pieces);
        int horizontalPadding = terrain.resolvedMode() == IrisStructureTerrainMode.FORCE_CARVE
                || terrain.resolvedMode() == IrisStructureTerrainMode.BORE
                ? Math.max(0, terrain.getHorizontalPadding())
                : 0;
        if (bounds == null || !complex.allowsNewGenerationFootprint(
                saturatedOffset(bounds[0], -horizontalPadding),
                saturatedOffset(bounds[2], -horizontalPadding),
                saturatedOffset(bounds[3], horizontalPadding),
                saturatedOffset(bounds[5], horizontalPadding))) {
            return;
        }
        RNG rng = resolved.rng();
        int baseY = resolved.baseY();
        if (trace) {
            IrisLogging.debug("[StructTrace] ASSEMBLED chunk=" + cx + "," + cz + " key=" + key + " baseY=" + baseY + " pieces=" + pieces.size());
        }

        if (!placement.isAnchoredUnderground()) {
            clearIntersectingObjectTrees(writer, resolved);
        }

        IrisStructureTerrainMode terrainMode = terrain.resolvedMode();
        if (terrainMode == IrisStructureTerrainMode.FORCE_CARVE) {
            forceCarveStructure(writer, pieces, terrain);
        } else if (terrainMode == IrisStructureTerrainMode.BORE) {
            boreStructure(writer, pieces, terrain);
        } else if (terrainMode != IrisStructureTerrainMode.PRESERVE
                && terrainMode != IrisStructureTerrainMode.SOURCE) {
            throw new IllegalStateException("Iris assembly terrain mode " + terrainMode
                    + " is not implemented for structure '" + key + "'");
        }

        ObjectPlaceMode mode = structure.getPlaceMode();
        int failedPieces = 0;
        IrisStructureStiltSettings stilt = placement.getStilt();
        Long2IntOpenHashMap foundationColumns = stilt == null
                ? null : new Long2IntOpenHashMap();
        boolean supportNonOccluding = stilt != null && stilt.isSupportNonOccluding();
        if (placement.isAnchoredUnderground()) {
            ObjectPlaceMode undergroundMode = (mode == ObjectPlaceMode.ORGANIC_STILT || mode == ObjectPlaceMode.CEILING_HANG)
                    ? mode : ObjectPlaceMode.STRUCTURE_PIECE;
            for (PlacedStructurePiece p : pieces) {
                if (placeObject(writer, structure, p, undergroundMode, p.getY(), rng,
                        foundationColumns, supportNonOccluding) == -1) {
                    failedPieces++;
                }
            }
        } else if (mode == ObjectPlaceMode.STRUCTURE_PIECE || mode == ObjectPlaceMode.FLOATING) {
            for (PlacedStructurePiece p : pieces) {
                if (placeObject(writer, structure, p, ObjectPlaceMode.STRUCTURE_PIECE, p.getY(), rng,
                        foundationColumns, supportNonOccluding) == -1) {
                    failedPieces++;
                }
            }
        } else if (pieces.size() == 1) {
            if (placeObject(writer, structure, pieces.getFirst(), mode, -1, rng,
                    foundationColumns, supportNonOccluding) == -1) {
                failedPieces++;
            }
        } else {
            for (PlacedStructurePiece p : pieces) {
                if (placeObject(writer, structure, p, ObjectPlaceMode.STRUCTURE_PIECE, p.getY(), rng,
                        foundationColumns, supportNonOccluding) == -1) {
                    failedPieces++;
                }
            }
        }
        requireAppliedPieces(resolved, cx, cz, failedPieces);
        if (failedPieces == 0 && stilt != null) {
            placeFoundation(writer, foundationColumns, stilt, rng, !placement.isAnchoredUnderground());
        }
    }

    private void placeFoundation(MantleWriter writer, Long2IntOpenHashMap columns,
                                 IrisStructureStiltSettings settings, RNG rng,
                                 boolean surfaceStructure) {
        IrisMaterialPalette palette = Objects.requireNonNull(
                settings.getPalette(), "Structure stilt palette must not be null");
        int mantleOffset = getEngineMantle().getEngine().getMinHeight();
        int maxDepth = Math.max(1, settings.getMaxDepth());
        for (Long2IntMap.Entry column : columns.long2IntEntrySet()) {
            int worldX = StructureFoundationPlanner.unpackX(column.getLongKey());
            int worldZ = StructureFoundationPlanner.unpackZ(column.getLongKey());
            int foundationY = column.getIntValue();
            PlatformBlockState foundationState = writer.getDataIfPresent(
                    worldX, foundationY, worldZ, PlatformBlockState.class);
            if (foundationState == null || !foundationState.isSolid()) {
                continue;
            }
            int terrainHeight = getEngineMantle().trueHeight(worldX, worldZ);
            int groundY = StructureFoundationPlanner.findGroundY(
                    foundationY, maxDepth, 0,
                    y -> {
                        PlatformBlockState overlay = writer.getDataIfPresent(
                                worldX, y, worldZ, PlatformBlockState.class);
                        boolean carved = writer.getDataIfPresent(
                                worldX, y, worldZ, MatterCavern.class) != null;
                        return surfaceStructure
                                ? StructureFoundationPlanner.isSurfaceSupportBoundary(
                                        overlay, carved, y, terrainHeight)
                                : StructureFoundationPlanner.isGroundSolid(
                                        overlay, carved, y, terrainHeight);
                    });
            if (groundY == StructureFoundationPlanner.NO_GROUND) {
                continue;
            }
            StructureFoundationPlanner.fillSupportColumn(foundationY, groundY, y -> {
                writeFoundationSupport(
                        writer, palette, rng, getData(), worldX, y, worldZ, mantleOffset);
            });
        }
    }

    static void writeFoundationSupport(MantleWriter writer, IrisMaterialPalette palette, RNG rng,
                                       IrisData data, int worldX, int mantleY, int worldZ,
                                       int mantleOffset) {
        int worldY = mantleY + mantleOffset;
        PlatformBlockState support = palette.get(rng, worldX, worldY, worldZ, data);
        if (support == null) {
            throw new IllegalStateException("Structure stilt palette resolved no block at "
                    + worldX + "," + worldY + "," + worldZ);
        }
        writer.clearData(worldX, mantleY, worldZ, MatterCavern.class);
        writer.set(worldX, mantleY, worldZ, support);
    }

    static KList<PlacedStructurePiece> resolvedPiecesOrNull(
            IrisStructureLocator.ResolvedPlacement resolved,
            int chunkX,
            int chunkZ
    ) {
        if (resolved == null) {
            return null;
        }
        KList<PlacedStructurePiece> pieces = resolved.pieces();
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

    private void boreStructure(MantleWriter writer, KList<PlacedStructurePiece> pieces,
                               IrisStructureTerrain terrain) {
        int[] bounds = computePieceBounds(pieces);
        if (bounds == null) {
            return;
        }
        int pad = Math.max(0, terrain.getHorizontalPadding());
        int minX = bounds[0] - pad;
        int minY = bounds[1] - Math.max(0, terrain.getFloorPadding());
        int minZ = bounds[2] - pad;
        int maxX = bounds[3] + pad;
        int maxY = bounds[4] + Math.max(0, terrain.getCeilingPadding());
        int maxZ = bounds[5] + pad;
        int worldMin = getEngineMantle().getEngine().getMinHeight() + 1;
        int worldMax = getEngineMantle().getEngine().getMinHeight() + getEngineMantle().getEngine().getHeight() - 1;
        minY = Math.max(minY, worldMin);
        maxY = Math.min(maxY, worldMax);
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }
        int mantleOffset = getEngineMantle().getEngine().getMinHeight();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    writer.setForcedCarve(bx, by - mantleOffset, bz, CARVE_CAVERN);
                }
            }
        }
    }

    private void forceCarveStructure(MantleWriter writer, KList<PlacedStructurePiece> pieces,
                                     IrisStructureTerrain terrain) {
        int[] bounds = computePieceBounds(pieces);
        if (bounds == null) {
            return;
        }
        IrisStructureCarveShape shape = terrain.resolvedShape();
        int margin = Math.max(0, terrain.getHorizontalPadding());
        int head = Math.max(0, terrain.getCeilingPadding());
        int floorCut = Math.max(0, terrain.getFloorPadding());
        double strength = terrain.resolvedErosionStrength();
        int mantleOffset = getEngineMantle().getEngine().getMinHeight();
        int worldMin = getEngineMantle().getEngine().getMinHeight() + 1;
        int worldMax = getEngineMantle().getEngine().getMinHeight() + getEngineMantle().getEngine().getHeight() - 1;
        int upExtension = shape.maximumCeilingExtension(head, strength);

        if (shape == IrisStructureCarveShape.BOX) {
            carveOverboreBox(writer, bounds, margin, head, floorCut, mantleOffset, worldMin, worldMax);
            return;
        }

        long work = overboreCandidateVolume(bounds, margin, upExtension, floorCut);
        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(
                pieces, margin, Integer.MAX_VALUE);
        if (footprint == null) {
            throw new IllegalStateException("Structure force-carve footprint is empty or exceeds addressable memory");
        }

        double frequency = terrain.resolvedErosionFrequency();
        boolean eroded = shape == IrisStructureCarveShape.ERODED && strength > 0D;
        CNG blob = null;
        CNG roll = null;
        if (eroded) {
            RNG noiseRng = new RNG(seed() + Cache.key(bounds[0], bounds[2]));
            blob = CNG.signature(noiseRng);
            roll = CNG.signature(noiseRng.nextParallelRNG(0x2A17));
        }

        if (IrisSettings.get().getGeneral().isDebug()) {
            IrisLogging.info("Overbore carving structure cavern: shape=" + shape + " pieces=" + pieces.size()
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
                        writer.setForcedCarve(bx, by - mantleOffset, bz, CARVE_CAVERN);
                        continue;
                    }
                    if (distanceSquared > 1D) {
                        continue;
                    }
                    if (!eroded) {
                        writer.setForcedCarve(bx, by - mantleOffset, bz, CARVE_CAVERN);
                        continue;
                    }
                    double noise = blob.fitDouble(
                            0D, 1D, bx * frequency, by * frequency, bz * frequency);
                    if (StructureCarveEnvelope.shouldCarveOverboreCell(
                            shape, distanceSquared, noise, strength)) {
                        writer.setForcedCarve(bx, by - mantleOffset, bz, CARVE_CAVERN);
                    }
                }
            }
        }
    }

    private void carveOverboreBox(MantleWriter writer, int[] bounds, int margin, int head, int floorCut,
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
                    writer.setForcedCarve(bx, by - mantleOffset, bz, CARVE_CAVERN);
                }
            }
        }
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

    private void clearIntersectingObjectTrees(MantleWriter writer, IrisStructureLocator.ResolvedPlacement resolved) {
        int mantleOffset = getEngineMantle().getEngine().getMinHeight();
        int worldHeight = getEngineMantle().getEngine().getHeight();
        Map<String, ObjectMarkerBounds> intersectingObjects = new HashMap<>();
        for (PlacedStructurePiece piece : resolved.pieces()) {
            for (IrisBlockVector local : piece.getObject().getBlocks().keys()) {
                PlatformBlockState structureState = piece.getObject().getBlocks().get(local);
                if (!isOccupiedStructureState(structureState)) {
                    continue;
                }
                IrisBlockVector rotated = piece.getRotation().rotate(local.clone());
                int y = piece.getY() - mantleOffset + rotated.getBlockY();
                if (y >= 0 && y < worldHeight) {
                    collectIntersectingObjectTreeMarker(
                            writer, piece.getX() + rotated.getBlockX(), y,
                            piece.getZ() + rotated.getBlockZ(), intersectingObjects);
                }
            }
        }
        for (Map.Entry<String, ObjectMarkerBounds> entry : intersectingObjects.entrySet()) {
            clearMarkerOwnedObject(writer, entry.getKey(), entry.getValue(), worldHeight);
        }
    }

    static boolean isOccupiedStructureState(PlatformBlockState state) {
        return !B.isAir(state);
    }

    private void collectIntersectingObjectTreeMarker(MantleWriter writer, int x, int y, int z,
                                                     Map<String, ObjectMarkerBounds> intersectingObjects) {
        PlatformBlockState state = writer.getDataIfPresent(x, y, z, PlatformBlockState.class);
        if (state == null || !state.isTreeBlock()) {
            return;
        }
        String marker = writer.getDataIfPresent(x, y, z, String.class);
        if (isOrdinaryObjectMarker(marker)) {
            intersectingObjects.computeIfAbsent(marker, ignored -> new ObjectMarkerBounds())
                    .include(x, y, z);
        }
    }

    private void clearMarkerOwnedObject(MantleWriter writer, String marker,
                                        ObjectMarkerBounds intersections, int worldHeight) {
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        if (decoded == null) {
            return;
        }
        IrisObject object = getData().load(IrisObject.class, decoded.objectKey(), false);
        if (object == null) {
            return;
        }
        double scale = Math.max(1D, getDimension().getAllObjectScaleFactor());
        int horizontalSpan = scaledMarkerSpan(Math.max(object.getW(), object.getD()), scale);
        int verticalSpan = scaledMarkerSpan(object.getH(), scale);
        ObjectMarkerBounds search = intersections.expand(horizontalSpan - 1, verticalSpan - 1, worldHeight);
        KList<ObjectBlockPosition> positions = new KList<>();
        int minChunkX = Math.floorDiv(search.minX, 16);
        int maxChunkX = Math.floorDiv(search.maxX, 16);
        int minChunkZ = Math.floorDiv(search.minZ, 16);
        int maxChunkZ = Math.floorDiv(search.maxZ, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                MantleChunk<Matter> chunk = writer.acquireChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int blockX = chunkX << 4;
                int blockZ = chunkZ << 4;
                chunk.iterate(String.class, (localX, y, localZ, value) -> {
                    int x = blockX + localX;
                    int z = blockZ + localZ;
                    if (marker.equals(value) && search.contains(x, y, z)) {
                        positions.add(new ObjectBlockPosition(x, y, z));
                    }
                });
            }
        }
        for (ObjectBlockPosition position : positions) {
            writer.clearBlock(position.x(), position.y(), position.z());
            writer.clearData(position.x(), position.y(), position.z(), String.class);
            writer.clearData(position.x(), position.y(), position.z(), TreeBlockMaterial.class);
            writer.clearData(position.x(), position.y(), position.z(), TileWrapper.class);
            writer.clearData(position.x(), position.y(), position.z(), MatterStructurePOI.class);
        }
    }

    static boolean isOrdinaryObjectMarker(String marker) {
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        return decoded != null && !decoded.structureAware();
    }

    static int scaledMarkerSpan(int dimension, double scale) {
        return scale <= 1D ? Math.max(1, dimension)
                : (int) Math.ceil(Math.max(1, dimension) * scale);
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

    private int[] computePieceBounds(KList<PlacedStructurePiece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PlacedStructurePiece p : pieces) {
            minX = Math.min(minX, p.getMinX());
            minY = Math.min(minY, p.getMinY());
            minZ = Math.min(minZ, p.getMinZ());
            maxX = Math.max(maxX, p.getMaxX());
            maxY = Math.max(maxY, p.getMaxY());
            maxZ = Math.max(maxZ, p.getMaxZ());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static int saturatedOffset(int coordinate, int offset) {
        long result = (long) coordinate + offset;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }

    private int placeObject(MantleWriter writer, IrisStructure structure, PlacedStructurePiece p,
                            ObjectPlaceMode mode, int y, RNG rng, Long2IntOpenHashMap foundationColumns,
                            boolean supportNonOccluding) {
        IrisObject object = p.getObject();
        String objectKey = object.getLoadKey();
        IrisObjectPlacement config = structure.createLootPlacement(objectKey);
        config.setMode(mode);
        config.setRotation(p.getRotation());
        if (!structure.getEdit().isEmpty()) {
            config.setEdit(structure.getEdit());
        }
        if (mode != ObjectPlaceMode.STRUCTURE_PIECE && mode != ObjectPlaceMode.FLOATING) {
            config.setForcePlace(true);
        }
        int placeY = (y == -1) ? -1 : y - getEngineMantle().getEngine().getMinHeight();
        String marker = structurePlacementMarker(structure, p, objectKey);
        return object.place(p.getX(), placeY, p.getZ(), writer, config, rng, (position, state) -> {
            StructureFoundationPlanner.recordBaseCell(
                    foundationColumns, position.getX(), position.getY(), position.getZ(), state,
                    supportNonOccluding);
            if (marker != null && shouldWriteStructureMarker(state)) {
                writer.setData(position.getX(), position.getY(), position.getZ(), marker);
            }
        }, null, getData());
    }

    static boolean shouldWriteStructureMarker(PlatformBlockState state) {
        return state != null && state.isStorageChest();
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

    private static String structurePlacementMarker(IrisStructure structure, PlacedStructurePiece piece, String objectKey) {
        String structureKey = structure.getLoadKey();
        if (structureKey == null || structureKey.isBlank() || objectKey == null || objectKey.isBlank()) {
            return null;
        }
        int placementId = structurePlacementId(structureKey, objectKey, piece.getX(), piece.getY(), piece.getZ());
        return StructurePlacementMarker.encodeStructure(objectKey, placementId, structureKey);
    }

    @Override
    protected int computeRadius() {
        IrisDimension dimension = getDimension();
        int maxBlocks = 0;

        for (IrisRegion region : dimension.getAllRegions(this::getData)) {
            maxBlocks = Math.max(maxBlocks, maxBlocksFrom(region.getStructures()));
        }
        for (IrisBiome biome : dimension.getReachableBiomes(this::getData)) {
            maxBlocks = Math.max(maxBlocks, maxBlocksFrom(biome.getStructures()));
        }
        maxBlocks = Math.max(maxBlocks, maxBlocksFrom(dimension.getStructures()));

        return maxBlocks;
    }

    private int maxBlocksFrom(KList<IrisStructurePlacement> placements) {
        int max = 0;
        for (IrisStructurePlacement placement : placements) {
            if (!placement.hasIrisStructures()) {
                continue;
            }
            IrisStructureTerrain terrain = placement.resolvedTerrain();
            int carvePadding = terrain.resolvedMode() == IrisStructureTerrainMode.FORCE_CARVE
                    || terrain.resolvedMode() == IrisStructureTerrainMode.BORE
                    ? Math.max(0, terrain.getHorizontalPadding()) : 0;
            for (String key : placement.getStructures()) {
                IrisStructure structure = getData().load(IrisStructure.class, key, false);
                if (structure != null) {
                    max = Math.max(max, Math.max(1, structure.getMaxSizeChunks()) * 16 + carvePadding);
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
