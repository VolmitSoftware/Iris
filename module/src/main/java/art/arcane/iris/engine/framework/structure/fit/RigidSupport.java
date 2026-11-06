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

package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.block.BlockState;
import com.volmit.vanilla.StartKey;
import com.volmit.vanilla.TemplateCells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Closes the short gaps between two rigid pieces of one surface start whose ground planes are one or two blocks apart:
 * where the upper piece's lowest solid base sits two blocks over the lower piece's ground, the gap is filled with the
 * terrain under the lower piece, so steps and terraces stand on ground instead of air. Only the chunk's own blocks are
 * filled, never across starts, and nothing at all when the work would exceed its budget.
 */
final class RigidSupport {
    private static final int MAX_BATCH_CELLS = 524_288;
    private static final int MAX_BRIDGE_SPAN = 2;

    private RigidSupport() {
    }

    static Set<Long> bridgeRigidPieceSupport(FitBlocks blocks, Box area, List<FitStart> starts) {
        if (starts.isEmpty()) {
            return Set.of();
        }
        Map<Long, BlockState> planned;
        try {
            BatchCellBudget budget = new BatchCellBudget();
            List<SupportRequest> requests = new ArrayList<>();
            Set<StartKey> seenStarts = new HashSet<>();
            for (FitStart start : starts) {
                if (!SurfaceFitting.requiresSurfaceTerrain(start) || !seenStarts.add(start.key())) {
                    continue;
                }
                collectStartRequests(blocks, area, start, budget, requests);
            }
            planned = planWrites(blocks, area, requests, budget);
        } catch (BatchBudgetExceeded ignored) {
            return Set.of();
        }
        if (planned.isEmpty()) {
            return Set.of();
        }
        List<Map.Entry<Long, BlockState>> ordered = new ArrayList<>(planned.entrySet());
        ordered.sort(Map.Entry.comparingByKey());
        Set<Long> written = new HashSet<>();
        for (Map.Entry<Long, BlockState> entry : ordered) {
            int x = TemplateFootprint.packedX(entry.getKey());
            int y = TemplateFootprint.packedY(entry.getKey());
            int z = TemplateFootprint.packedZ(entry.getKey());
            BlockState existing = blocks.get(x, y, z);
            if (!blocks.isSolid(existing) && !blocks.isTree(existing) && !blocks.holdsFluid(existing)) {
                blocks.set(x, y, z, entry.getValue());
                written.add(entry.getKey());
            }
        }
        return Set.copyOf(written);
    }

    private static void collectStartRequests(FitBlocks blocks, Box area, FitStart start, BatchCellBudget budget,
                                             List<SupportRequest> requests) {
        List<RigidAnchor> anchors = rigidAnchors(start);
        if (anchors.size() < 2) {
            return;
        }
        for (RigidAnchor upper : anchors) {
            Map<Long, Integer> lowerMeets = candidateLowerMeets(area, upper, anchors, budget);
            if (lowerMeets.isEmpty()) {
                continue;
            }
            TemplateCells cells = upper.piece().cells(area);
            budget.consume(cells.readBlocks());
            if (!cells.resolved()) {
                continue;
            }
            for (Map.Entry<Long, TemplateFootprint.Cell> entry
                    : lowestBridgeableCells(cells, upper.piece(), area, lowerMeets).entrySet()) {
                TemplateFootprint.Cell cell = entry.getValue();
                Integer lowerMeet = lowerMeets.get(entry.getKey());
                if (lowerMeet == null || cell.blocker() || !blocks.isSolidBase(cell.state())
                        || cell.y() - lowerMeet < 2 || cell.y() - lowerMeet > MAX_BRIDGE_SPAN) {
                    continue;
                }
                budget.consume(1);
                requests.add(new SupportRequest(cell.x(), cell.z(), lowerMeet, cell.y()));
            }
        }
    }

    /**
     * The lowest cell of each column over a lower piece's ground, among the cells no higher than the widest bridge
     * above that ground: the cells the support could stand under.
     */
    private static Map<Long, TemplateFootprint.Cell> lowestBridgeableCells(TemplateCells cells, FitPiece piece, Box area,
                                                                          Map<Long, Integer> lowerMeets) {
        Map<Long, TemplateFootprint.Cell> lowest = new HashMap<>();
        for (int cell = 0; cell < cells.size(); cell++) {
            int x = cells.x(cell);
            int y = piece.cellY(cells, cell);
            int z = cells.z(cell);
            long column = TemplateFootprint.columnKey(x, z);
            Integer lowerMeet = lowerMeets.get(column);
            if (!area.contains(x, y, z) || lowerMeet == null || y > lowerMeet + MAX_BRIDGE_SPAN) {
                continue;
            }
            TemplateFootprint.Cell current = lowest.get(column);
            if (current == null || y < current.y()) {
                lowest.put(column, new TemplateFootprint.Cell(x, y, z, cells.state(cell)));
            }
        }
        return lowest;
    }

    private static List<RigidAnchor> rigidAnchors(FitStart start) {
        List<RigidAnchor> anchors = new ArrayList<>();
        for (FitPiece piece : start.pieces()) {
            if (!piece.isRigid()) {
                continue;
            }
            anchors.add(new RigidAnchor(piece, piece.box(), piece.groundY() - 1));
        }
        return List.copyOf(anchors);
    }

    private static Map<Long, Integer> candidateLowerMeets(Box area, RigidAnchor upper, List<RigidAnchor> anchors,
                                                          BatchCellBudget budget) {
        Map<Long, Integer> lowerMeets = new HashMap<>();
        for (RigidAnchor lower : anchors) {
            budget.consume(1);
            int span = upper.meetY() - lower.meetY();
            if (lower.piece() == upper.piece() || span < 1 || span > MAX_BRIDGE_SPAN
                    || lower.meetY() < area.minY() || lower.meetY() > area.maxY()) {
                continue;
            }
            int minimumX = Math.max(area.minX(), Math.max(upper.bounds().minX(), lower.bounds().minX()));
            int maximumX = Math.min(area.maxX(), Math.min(upper.bounds().maxX(), lower.bounds().maxX()));
            int minimumZ = Math.max(area.minZ(), Math.max(upper.bounds().minZ(), lower.bounds().minZ()));
            int maximumZ = Math.min(area.maxZ(), Math.min(upper.bounds().maxZ(), lower.bounds().maxZ()));
            if (minimumX > maximumX || minimumZ > maximumZ) {
                continue;
            }
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    budget.consume(1);
                    lowerMeets.merge(TemplateFootprint.columnKey(x, z), lower.meetY(), Math::max);
                }
            }
        }
        return lowerMeets;
    }

    private static Map<Long, BlockState> planWrites(FitBlocks blocks, Box area, List<SupportRequest> requests,
                                                    BatchCellBudget budget) {
        List<SupportRequest> eligible = new ArrayList<>();
        Set<MaterialKey> materialKeys = new HashSet<>();
        for (SupportRequest request : requests) {
            BlockState lowerTerrain = blocks.get(request.x(), request.lowerMeetY(), request.z());
            if (!isTerrainSupport(blocks, lowerTerrain) || !gapIsClear(blocks, area, request, budget)) {
                continue;
            }
            eligible.add(request);
            materialKeys.add(new MaterialKey(request.x(), request.z(), request.lowerMeetY()));
        }
        Map<MaterialKey, BlockState> materials = new HashMap<>();
        for (MaterialKey key : materialKeys) {
            materials.put(key, resolveSupportState(blocks, key.x(), key.z(), key.lowerMeetY(), budget));
        }
        Map<Long, BlockState> planned = new HashMap<>();
        Set<Long> conflicts = new HashSet<>();
        eligible.sort(Comparator.comparingInt(SupportRequest::x)
                .thenComparingInt(SupportRequest::z)
                .thenComparingInt(SupportRequest::lowerMeetY)
                .thenComparingInt(SupportRequest::baseY));
        for (SupportRequest request : eligible) {
            BlockState material = materials.get(new MaterialKey(request.x(), request.z(), request.lowerMeetY()));
            for (int y = request.lowerMeetY() + 1; y < request.baseY(); y++) {
                budget.consume(1);
                long positionKey = TemplateFootprint.packedPosition(request.x(), y, request.z());
                if (conflicts.contains(positionKey)) {
                    continue;
                }
                BlockState existing = planned.putIfAbsent(positionKey, material);
                if (existing != null && !existing.equals(material)) {
                    planned.remove(positionKey);
                    conflicts.add(positionKey);
                }
            }
        }
        return planned;
    }

    private static boolean gapIsClear(FitBlocks blocks, Box area, SupportRequest request, BatchCellBudget budget) {
        for (int y = request.lowerMeetY() + 1; y < request.baseY(); y++) {
            budget.consume(1);
            if (!area.contains(request.x(), y, request.z())) {
                return false;
            }
            BlockState state = blocks.get(request.x(), y, request.z());
            if (blocks.isSolid(state) || blocks.isTree(state) || blocks.holdsFluid(state)) {
                return false;
            }
        }
        return true;
    }

    private static BlockState resolveSupportState(FitBlocks blocks, int x, int z, int lowerMeetY, BatchCellBudget budget) {
        budget.consume(1);
        BlockState below = blocks.get(x, lowerMeetY - 1, z);
        if (isTerrainSupport(blocks, below)) {
            return below;
        }
        return blocks.get(x, lowerMeetY, z);
    }

    private static boolean isTerrainSupport(FitBlocks blocks, BlockState state) {
        return blocks.isSolid(state) && !blocks.isTree(state) && !blocks.holdsFluid(state);
    }

    private record RigidAnchor(FitPiece piece, Box bounds, int meetY) {
    }

    private record SupportRequest(int x, int z, int lowerMeetY, int baseY) {
    }

    private record MaterialKey(int x, int z, int lowerMeetY) {
    }

    private static final class BatchCellBudget {
        private int consumed;

        private void consume(int amount) {
            if (amount < 0 || consumed > MAX_BATCH_CELLS - amount) {
                throw BatchBudgetExceeded.INSTANCE;
            }
            consumed += amount;
        }
    }

    private static final class BatchBudgetExceeded extends RuntimeException {
        private static final BatchBudgetExceeded INSTANCE = new BatchBudgetExceeded();

        private BatchBudgetExceeded() {
            super(null, null, false, false);
        }
    }
}
