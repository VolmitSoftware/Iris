package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.framework.NativeStructureGenerationStatus;
import art.arcane.iris.engine.framework.NativeStructureLayouts;
import art.arcane.iris.engine.framework.NativeStructureReach;
import art.arcane.iris.engine.framework.NativeStructureOwnershipRecord;
import art.arcane.iris.engine.framework.NativeStructureOwnershipStore;
import art.arcane.iris.engine.framework.NativeStructureStarts;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NativeStructureOwnershipRecovery {
    private NativeStructureOwnershipRecovery() {
    }

    public static NativeStructureOwnershipRecord resolve(
            Engine engine, ServerLevel level, String structureKey,
            Structure structure, StructureStart start) {
        Objects.requireNonNull(engine, "Native structure ownership recovery requires an engine");
        ServerLevel activeLevel = Objects.requireNonNull(
                level, "Native structure ownership recovery requires a level");
        Structure activeStructure = Objects.requireNonNull(
                structure, "Native structure ownership recovery requires a structure");
        if (start == null || !start.isValid() || start.getStructure() != activeStructure) {
            return null;
        }
        ChunkPos origin = start.getChunkPos();
        NativeStructureOwnershipRecord persisted = NativeStructureOwnershipStore.findPersisted(
                engine, structureKey, origin.x(), origin.z());
        if (persisted != null) {
            NativeStructureOwnershipRecord refreshed = refreshReferenceEnvelope(
                    structureKey, activeStructure, start, persisted, reachesOf(engine, structureKey, activeStructure,
                            persisted.restoredMemo()));
            if (refreshed != null) {
                if (refreshed != persisted) {
                    NativeStructureOwnershipStore.record(engine, refreshed);
                }
                return refreshed;
            }
            NativeStructureOwnershipStore.discard(
                    engine, structureKey, origin.x(), origin.z());
        }
        NativeStructureStartPlan plan = NativeStructureStarts.matchingPlan(
                engine, structureKey, origin.x(), origin.z());
        if (!matchesPlan(structureKey, origin, plan)) {
            return null;
        }
        Registry<Structure> registry = activeLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.wrapAsHolder(activeStructure);
        ChunkGeneratorStructureState state = activeLevel.getChunkSource().getGeneratorState();
        if (naturalStartIsAmbiguous(engine, structureKey, activeStructure, holder, state, plan)) {
            return null;
        }
        StructureStart expected = generateExpected(
                engine, activeLevel, holder, plan, start.getReferences(),
                reachesOf(engine, structureKey, activeStructure, plan.memo()));
        NativeStructureOwnershipRecord recovered = proveCandidate(
                structureKey, activeStructure, start, plan, expected, false,
                reachesOf(engine, structureKey, activeStructure, plan.memo()));
        if (recovered == null) {
            return null;
        }
        NativeStructureOwnershipStore.record(engine, recovered);
        return recovered;
    }

    /**
     * The record with its reference envelope grown to what today's reaches give its start: a module may reach further
     * around its starts than when it recorded one (VACUUM's fixed falloff did), and the start must then be referenced
     * from the farther chunks too. An envelope never shrinks, so no chunk that once referenced the start loses it.
     */
    static NativeStructureOwnershipRecord refreshReferenceEnvelope(
            String structureKey, Structure structure, StructureStart start,
            NativeStructureOwnershipRecord ownership, List<NativeStructureReach> reaches) {
        if (structure == null || start == null || !start.isValid()
                || start.getStructure() != structure || ownership == null
                || !ownership.structureKey().equals(normalize(structureKey))
                || !NativeStructureOwnershipFingerprint.matches(ownership, start)) {
            return null;
        }
        BoundingBox expected = NativeStructureReferenceEnvelope.referenceBounds(
                start, structure, reaches, structureKey);
        int referenceMinChunkX = Math.min(ownership.referenceMinChunkX(), expected.minX() >> 4);
        int referenceMaxChunkX = Math.max(ownership.referenceMaxChunkX(), expected.maxX() >> 4);
        int referenceMinChunkZ = Math.min(ownership.referenceMinChunkZ(), expected.minZ() >> 4);
        int referenceMaxChunkZ = Math.max(ownership.referenceMaxChunkZ(), expected.maxZ() >> 4);
        if (ownership.referenceMinChunkX() == referenceMinChunkX
                && ownership.referenceMaxChunkX() == referenceMaxChunkX
                && ownership.referenceMinChunkZ() == referenceMinChunkZ
                && ownership.referenceMaxChunkZ() == referenceMaxChunkZ) {
            return ownership;
        }
        return ownership.withReferenceEnvelope(
                referenceMinChunkX, referenceMaxChunkX,
                referenceMinChunkZ, referenceMaxChunkZ);
    }

    static NativeStructureOwnershipRecord proveCandidate(
            String structureKey, Structure structure, StructureStart persisted,
            NativeStructureStartPlan plan, StructureStart expected,
            boolean naturalStartAmbiguous, List<NativeStructureReach> reaches) {
        if (naturalStartAmbiguous || structure == null
                || persisted == null || !persisted.isValid()
                || expected == null || !expected.isValid()
                || persisted.getStructure() != structure
                || expected.getStructure() != structure
                || !matchesPlan(structureKey, persisted.getChunkPos(), plan)
                || !persisted.getChunkPos().equals(expected.getChunkPos())) {
            return null;
        }
        BoundingBox referenceEnvelope = NativeStructureReferenceEnvelope.referenceBounds(
                expected, structure, reaches, structureKey);
        NativeStructureOwnershipRecord candidate = NativeStructureOwnershipFingerprint.capture(
                structureKey, expected, plan, referenceEnvelope);
        return NativeStructureOwnershipFingerprint.matches(candidate, persisted)
                ? candidate : null;
    }

    private static boolean matchesPlan(String structureKey, ChunkPos origin,
                                       NativeStructureStartPlan plan) {
        if (structureKey == null || structureKey.isBlank() || plan == null) {
            return false;
        }
        return plan.chunkX() == origin.x()
                && plan.chunkZ() == origin.z()
                && normalize(structureKey).equals(normalize(plan.structureKey()));
    }

    private static StructureStart generateExpected(
            Engine engine, ServerLevel level, Holder<Structure> holder,
            NativeStructureStartPlan plan, int references, List<NativeStructureReach> reaches) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        NativeStructureFactory.GenerationContext generationContext =
                new NativeStructureFactory.GenerationContext(
                        level.registryAccess(),
                        generator,
                        biomeSource,
                        state.randomState(),
                        level.getStructureManager(),
                        state.getLevelSeed(),
                        level.dimension(),
                        level,
                        biome -> true,
                        generator.getSeaLevel(),
                        (x, z) -> engine.getHeight(x, z, true) + engine.getMinHeight()
                );
        return NativeStructureFactory.generate(generationContext, holder, plan, references, reaches);
    }

    private static boolean naturalStartIsAmbiguous(
            Engine engine, String structureKey, Structure structure,
            Holder<Structure> holder, ChunkGeneratorStructureState state,
            NativeStructureStartPlan plan) {
        if (plan.replacesPlatformStart()) {
            return false;
        }
        if (NativeStructureGenerationPolicy.resolve(engine, structureKey)
                == NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            return false;
        }
        for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
            if (placement.isStructureChunk(state, plan.chunkX(), plan.chunkZ())) {
                return true;
            }
        }
        return false;
    }

    private static List<NativeStructureReach> reachesOf(Engine engine, String structureKey, Structure structure,
                                                        NativeStartMemo memo) {
        return NativeStructureLayouts.reaches(engine, structureKey,
                NativeStructureVegetationClearer.isUndergroundStep(structure.step()), memo);
    }

    private static String normalize(String structureKey) {
        return structureKey == null ? "" : structureKey.trim().toLowerCase(Locale.ROOT);
    }
}
