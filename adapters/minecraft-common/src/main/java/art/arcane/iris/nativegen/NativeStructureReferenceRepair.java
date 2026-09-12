package art.arcane.iris.nativegen;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.spi.IrisLogging;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeStructureReferenceRepair {
    private static final int REFERENCE_DISTANCE_CHUNKS =
            NativeStructureOwnershipRecord.MAX_REFERENCE_DISTANCE_CHUNKS;
    private static final Set<String> WARNED_POLICY_INVALIDATIONS = ConcurrentHashMap.newKeySet();

    private NativeStructureReferenceRepair() {
    }

    public static void createReferences(Engine engine, WorldGenLevel level,
                                        StructureManager structureManager, ChunkAccess targetChunk) {
        ChunkPos target = targetChunk.getPos();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ServerLevel serverLevel = level.getLevel();
        List<ScannedStart> starts = new ArrayList<>();
        for (int originChunkX = target.x() - REFERENCE_DISTANCE_CHUNKS;
             originChunkX <= target.x() + REFERENCE_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = target.z() - REFERENCE_DISTANCE_CHUNKS;
                 originChunkZ <= target.z() + REFERENCE_DISTANCE_CHUNKS; originChunkZ++) {
                try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                             openOriginRuntimeScope(engine, originChunkX, originChunkZ)) {
                    ChunkAccess originChunk = level.getChunk(
                            originChunkX, originChunkZ, ChunkStatus.STRUCTURE_STARTS);
                    for (Map.Entry<Structure, StructureStart> entry : originChunk.getAllStarts().entrySet()) {
                        ScannedStart start = scanStart(
                                engine, serverLevel, structureManager, targetChunk, registry,
                                originChunk, entry.getKey(), entry.getValue());
                        if (start != null && isTargetRelevant(engine, targetChunk, start)) {
                            starts.add(start);
                        }
                    }
                }
            }
        }
        for (ScannedStart start : starts) {
            boolean valid;
            synchronized (start.originChunk()) {
                StructureStart current = start.originChunk().getStartForStructure(start.structure());
                valid = current == start.start() && current.isValid();
            }
            if (!valid) {
                continue;
            }
            structureManager.addReferenceForStructure(
                    SectionPos.bottomOf(targetChunk), start.structure(),
                    start.origin().pack(), targetChunk);
        }
    }

    private static GenerationHistoryRuntimeRouter.CoordinateScope openOriginRuntimeScope(
            Engine engine,
            int chunkX,
            int chunkZ
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(chunkX << 4, chunkZ << 4);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to resolve native structure references for origin "
                    + chunkX + "," + chunkZ + " through generation history.", failure);
        }
    }

    private static boolean isTargetRelevant(
            Engine engine, ChunkAccess targetChunk, ScannedStart scanned) {
        StructureStart start = scanned.start();
        Structure structure = scanned.structure();
        ChunkPos target = targetChunk.getPos();
        if (scanned.ownership() != null) {
            return requiresReference(target, scanned.structureKey(), start, scanned.ownership());
        }
        if (scanned.registered()) {
            return requiresNaturalReference(
                    engine, target, scanned.structureKey(), structure, start);
        }
        return NativeStructureReferenceEnvelope.contentBounds(start).intersects(
                target.getMinBlockX(), target.getMinBlockZ(),
                target.getMaxBlockX(), target.getMaxBlockZ());
    }

    private static ScannedStart scanStart(
            Engine engine, ServerLevel level, StructureManager structureManager,
            ChunkAccess targetChunk, Registry<Structure> registry,
            ChunkAccess originChunk, Structure structure, StructureStart start) {
        Identifier identifier = registry.getKey(structure);
        String structureKey = identifier == null
                ? structure.getClass().getName() : identifier.toString();
        if (start == null || !start.isValid()) {
            return null;
        }
        if (!NativeStructureReferenceEnvelope.contentFitsReferenceRange(start)) {
            if (identifier != null) {
                NativeStructureOwnershipStore.discard(
                        engine, structureKey, start.getChunkPos().x(), start.getChunkPos().z());
            }
            invalidateStart(structureManager, originChunk, structure, start, targetChunk);
            return null;
        }
        ChunkPos startOrigin = start.getChunkPos();
        NativeStructureOwnershipRecord ownership = identifier == null ? null
                : NativeStructureOwnershipRecovery.resolve(
                engine, level, structureKey, structure, start);
        if (ownership != null) {
            return new ScannedStart(
                    originChunk, structure, start, structureKey, ownership, true);
        }
        if (identifier != null && !naturalPolicyAllows(engine, structureKey, structure)) {
            invalidateStart(structureManager, originChunk, structure, start, targetChunk);
            if (WARNED_POLICY_INVALIDATIONS.add(structureKey)) {
                IrisLogging.warn("Invalidating persisted natural structure start '"
                        + structureKey + "' because the current Iris dimension policy disables it");
            }
            return null;
        }
        return new ScannedStart(
                originChunk, structure, start, structureKey, null, identifier != null);
    }

    private static boolean naturalPolicyAllows(
            Engine engine, String structureKey, Structure structure) {
        IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(
                engine, structureKey,
                NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
        return naturalDecisionAllows(decision);
    }

    static boolean naturalDecisionAllows(IrisNativeStructureDecision decision) {
        return Objects.requireNonNull(
                decision, "Native structure decision must not be null").generate();
    }

    private static void invalidateStart(
            StructureManager structureManager, ChunkAccess originChunk,
            Structure structure, StructureStart start, ChunkAccess targetChunk) {
        synchronized (originChunk) {
            StructureStart current = originChunk.getStartForStructure(structure);
            if (current == start && current.isValid()) {
                structureManager.setStartForStructure(
                        SectionPos.bottomOf(originChunk), structure,
                        StructureStart.INVALID_START, originChunk);
            }
        }
        if (targetChunk != null) {
            removeTargetReference(targetChunk, structure, start.getChunkPos().pack());
        }
    }

    private static void removeTargetReference(
            ChunkAccess targetChunk, Structure structure, long origin) {
        LongSet current = targetChunk.getAllReferences().get(structure);
        if (current == null || !current.contains(origin)) {
            return;
        }
        Map<Structure, LongSet> updated = new HashMap<>(targetChunk.getAllReferences());
        LongSet retained = new LongOpenHashSet(current);
        retained.remove(origin);
        if (retained.isEmpty()) {
            updated.remove(structure);
        } else {
            updated.put(structure, retained);
        }
        targetChunk.setAllReferences(updated);
    }

    static boolean requiresNaturalReference(Engine engine, ChunkPos target, String structureKey,
                                            Structure structure, StructureStart start) {
        if (target == null || start == null || !start.isValid()) {
            return false;
        }
        IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(
                engine, structureKey,
                NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
        if (!decision.generate()) {
            return false;
        }
        BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                start, structure,
                NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain()),
                structureKey);
        return referenceBounds.intersects(
                target.getMinBlockX(), target.getMinBlockZ(),
                target.getMaxBlockX(), target.getMaxBlockZ());
    }

    static boolean requiresReference(ChunkPos target, String structureKey,
                                     StructureStart start,
                                     NativeStructureOwnershipRecord ownership) {
        if (target == null || start == null || !start.isValid()
                || ownership == null || !ownership.structureKey().equals(structureKey)
                || !ownership.covers(target.x(), target.z())) {
            return false;
        }
        return NativeStructureOwnershipFingerprint.matches(ownership, start);
    }

    private record ScannedStart(
            ChunkAccess originChunk,
            Structure structure,
            StructureStart start,
            String structureKey,
            NativeStructureOwnershipRecord ownership,
            boolean registered
    ) {
        private ChunkPos origin() {
            return ownership == null
                    ? start.getChunkPos()
                    : new ChunkPos(ownership.originChunkX(), ownership.originChunkZ());
        }
    }
}
