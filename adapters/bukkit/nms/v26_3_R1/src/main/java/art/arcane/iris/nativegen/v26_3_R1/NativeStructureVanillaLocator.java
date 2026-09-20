package art.arcane.iris.nativegen.v26_3_R1;

import com.mojang.datafixers.util.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeStructureVanillaLocator {
    private NativeStructureVanillaLocator() {
    }

    public static Candidate predict(ServerLevel level, HolderSet<Structure> holders,
                                    BlockPos origin, int radius, boolean findUnexplored) {
        if (SharedConstants.DEBUG_DISABLE_FEATURES
                || !level.getServer().getWorldGenSettings().options().generateStructures()) {
            return null;
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> byPlacement = new LinkedHashMap<>();
        for (Holder<Structure> holder : holders) {
            for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
                byPlacement.computeIfAbsent(placement, ignored -> new LinkedHashSet<>()).add(holder);
            }
        }
        if (byPlacement.isEmpty()) {
            return null;
        }

        StructureManager structureManager = level.structureManager();
        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        List<Map.Entry<RandomSpreadStructurePlacement, Set<Holder<Structure>>>> randomPlacements =
                new ArrayList<>(byPlacement.size());
        for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : byPlacement.entrySet()) {
            StructurePlacement placement = entry.getKey();
            if (placement instanceof ConcentricRingsStructurePlacement concentric) {
                Candidate candidate = predictConcentric(
                        entry.getValue(), level, structureManager, origin, findUnexplored,
                        state, concentric);
                if (candidate != null) {
                    double distance = origin.distSqr(candidate.result().getFirst());
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            } else if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
                randomPlacements.add(Map.entry(randomSpread, entry.getValue()));
            }
        }

        int centerChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        int searchRadius = Math.max(0, radius);
        for (int ring = 0; ring <= searchRadius; ring++) {
            boolean foundInRing = false;
            for (Map.Entry<RandomSpreadStructurePlacement, Set<Holder<Structure>>> entry
                    : randomPlacements) {
                Candidate candidate = predictRandomSpread(
                        entry.getValue(), level, structureManager,
                        centerChunkX, centerChunkZ, ring, findUnexplored,
                        state.getLevelSeed(), entry.getKey());
                if (candidate == null) {
                    continue;
                }
                foundInRing = true;
                double distance = origin.distSqr(candidate.result().getFirst());
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
            if (foundInRing) {
                return best;
            }
        }
        return best;
    }

    private static Candidate predictConcentric(Set<Holder<Structure>> holders,
                                                ServerLevel level,
                                                StructureManager structureManager,
                                                BlockPos origin,
                                                boolean findUnexplored,
                                                ChunkGeneratorStructureState state,
                                                ConcentricRingsStructurePlacement placement) {
        List<ChunkPos> positions = state.getRingPositionsFor(placement);
        if (positions == null) {
            throw new IllegalStateException(
                    "Tried to locate structures for an unavailable concentric placement");
        }
        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ChunkPos position : positions) {
            BlockPos locatePosition = new BlockPos(
                    SectionPos.sectionToBlockCoord(position.x(), 8),
                    32,
                    SectionPos.sectionToBlockCoord(position.z(), 8));
            double distance = locatePosition.distSqr(origin);
            if (best != null && distance >= bestDistance) {
                continue;
            }
            Candidate candidate = predictAt(
                    holders, level, structureManager, findUnexplored, placement, position);
            if (candidate != null) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Candidate predictRandomSpread(Set<Holder<Structure>> holders,
                                                  ServerLevel level,
                                                  StructureManager structureManager,
                                                  int centerChunkX, int centerChunkZ,
                                                  int ring, boolean findUnexplored,
                                                  long seed,
                                                  RandomSpreadStructurePlacement placement) {
        int spacing = placement.spacing();
        for (int offsetX = -ring; offsetX <= ring; offsetX++) {
            boolean edgeX = offsetX == -ring || offsetX == ring;
            for (int offsetZ = -ring; offsetZ <= ring; offsetZ++) {
                boolean edgeZ = offsetZ == -ring || offsetZ == ring;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                int gridChunkX = centerChunkX + spacing * offsetX;
                int gridChunkZ = centerChunkZ + spacing * offsetZ;
                ChunkPos candidatePosition = placement.getPotentialStructureChunk(
                        seed, gridChunkX, gridChunkZ);
                Candidate candidate = predictAt(
                        holders, level, structureManager,
                        findUnexplored, placement, candidatePosition);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Candidate predictAt(Set<Holder<Structure>> holders,
                                       ServerLevel level,
                                       StructureManager structureManager,
                                       boolean findUnexplored,
                                       StructurePlacement placement,
                                       ChunkPos candidatePosition) {
        for (Holder<Structure> holder : holders) {
            Structure structure = holder.value();
            StructureCheckResult result = structureManager.checkStructurePresence(
                    candidatePosition, structure, placement, findUnexplored);
            if (result == StructureCheckResult.START_NOT_PRESENT) {
                continue;
            }
            if (!findUnexplored && result == StructureCheckResult.START_PRESENT) {
                return new Candidate(
                        Pair.of(placement.getLocatePos(candidatePosition), holder), null);
            }
            ChunkAccess chunk = level.getChunk(
                    candidatePosition.x(), candidatePosition.z(), ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structureManager.getStartForStructure(
                    structure, chunk);
            if (start == null || !start.isValid()
                    || findUnexplored && !start.canBeReferenced()) {
                continue;
            }
            return new Candidate(
                    Pair.of(placement.getLocatePos(start.getChunkPos()), holder),
                    findUnexplored ? start : null);
        }
        return null;
    }

    public static final class Candidate {
        private final Pair<BlockPos, Holder<Structure>> result;
        private final StructureStart referenceStart;
        private final AtomicBoolean committed;

        private Candidate(Pair<BlockPos, Holder<Structure>> result,
                          StructureStart referenceStart) {
            this.result = result;
            this.referenceStart = referenceStart;
            this.committed = new AtomicBoolean();
        }

        public Pair<BlockPos, Holder<Structure>> result() {
            return result;
        }

        public boolean reference(StructureManager structureManager) {
            if (referenceStart == null || !committed.compareAndSet(false, true)) {
                return false;
            }
            if (!referenceStart.canBeReferenced()) {
                return false;
            }
            structureManager.addReference(referenceStart);
            return true;
        }
    }
}
