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

import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.StructurePlacementScope;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacementRecord;
import com.volmit.resource.ResourceId;
import com.volmit.service.ObjectsAt;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which structure piece {@code iris:structure} plans at a block, for the host's block inspection. Generation records
 * only the storage blocks of a piece ({@link StructurePlacing}), so this assembles the starts within structure reach of
 * the block again and answers with the piece whose box holds it. A lone surface piece takes its height from the
 * terrain when it is placed, so for it only the column has to match. The module predicts no objects per chunk.
 */
public final class StructurePiecesAt implements ObjectsAt {
    private static final int BLOCKS_PER_CHUNK_SHIFT = 4;

    @Override
    public Set<ResourceId> objectsAt(PackWorld world, int chunkX, int chunkZ) {
        return Set.of();
    }

    @Override
    public PlacedBy placedBy(PackWorld world, ResourceId object) {
        return PlacedBy.NOTHING;
    }

    @Override
    public Optional<PlacementRecord> placementAt(PackWorld world, int worldX, int y, int worldZ) {
        int blockY = y + world.minWorldY();
        int reachChunks = (StructurePlacing.reach(world.settings()) >> BLOCKS_PER_CHUNK_SHIFT) + 1;
        int chunkX = worldX >> BLOCKS_PER_CHUNK_SHIFT;
        int chunkZ = worldZ >> BLOCKS_PER_CHUNK_SHIFT;
        for (int originX = chunkX - reachChunks; originX <= chunkX + reachChunks; originX++) {
            for (int originZ = chunkZ - reachChunks; originZ <= chunkZ + reachChunks; originZ++) {
                Optional<PlacementRecord> piece = pieceStartedIn(world, originX, originZ, worldX, blockY, worldZ);
                if (piece.isPresent()) {
                    return piece;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<PlacementRecord> pieceStartedIn(PackWorld world, int originChunkX, int originChunkZ,
                                                            int x, int blockY, int z) {
        for (StructurePlacement placement : StructurePlacementScope.placementsAt(world, originChunkX, originChunkZ)) {
            if (!placement.hasIrisStructures()) {
                continue;
            }
            IrisStructureLocator.ResolvedPlacement resolved =
                    IrisStructureLocator.resolvePlacement(world, placement, originChunkX, originChunkZ);
            if (resolved == null || resolved.pieces() == null) {
                continue;
            }
            Optional<PlacementRecord> piece = pieceCovering(world, resolved, x, blockY, z);
            if (piece.isPresent()) {
                return piece;
            }
        }
        return Optional.empty();
    }

    private static Optional<PlacementRecord> pieceCovering(PackWorld world, IrisStructureLocator.ResolvedPlacement resolved,
                                                           int x, int blockY, int z) {
        List<AssembledPiece> pieces = resolved.pieces();
        boolean heightPlanned = !StructurePlacing.samplesPieceHeight(resolved.placement(), resolved.stamp(), pieces);
        for (AssembledPiece piece : pieces) {
            if (covers(piece, x, blockY, z, heightPlanned)) {
                return Optional.ofNullable(recordOf(world, resolved.structureKey(), piece));
            }
        }
        return Optional.empty();
    }

    private static boolean covers(AssembledPiece piece, int x, int blockY, int z, boolean heightPlanned) {
        boolean inColumn = x >= piece.minX() && x <= piece.maxX() && z >= piece.minZ() && z <= piece.maxZ();
        return inColumn && (!heightPlanned || (blockY >= piece.minY() && blockY <= piece.maxY()));
    }

    private static PlacementRecord recordOf(PackWorld world, String structureKey, AssembledPiece piece) {
        String objectKey = world.resources().objects().object(piece.object()).map(PlaceableObject::name).orElse(null);
        return StructurePlacing.pieceRecord(world.resources(), structureKey, piece, objectKey);
    }
}
