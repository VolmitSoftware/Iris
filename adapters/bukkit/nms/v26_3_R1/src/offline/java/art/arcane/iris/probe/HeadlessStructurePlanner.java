package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureFactory;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureOwnershipFingerprint;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureReferenceEnvelope;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureReferenceRepair;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureTerrainIntegrator;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVegetationClearer;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVerticalPlacer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class HeadlessStructurePlanner {
    private HeadlessStructurePlanner() {
    }

    static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> PlannedChunk<O> plan(
            NativeContext context, Policies<P, O> policies, ChunkPos position) {
        ProtoChunk chunk = new ProtoChunk(position, UpgradeData.EMPTY,
                LevelHeightAccessor.create(context.generator().getMinY(), context.generator().getGenDepth()),
                PalettedContainerFactory.create(context.registries()), null);
        if (!context.enabled()) {
            chunk.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
            return new PlannedChunk<O>(context, chunk, Map.of(), Map.of());
        }
        try (NativeGenerationScope scope = policies.references().openOriginScope(chunk.getPos().x(), chunk.getPos().z())) {
            generateNaturalStarts(context, chunk);
            Map<Structure, O> ownership = inject(context, policies, chunk);
            Map<Structure, StructureTerrainSettings> terrain = adjust(context, policies, chunk, ownership);
            chunk.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
            return new PlannedChunk<>(context, chunk, Map.copyOf(ownership), Map.copyOf(terrain));
        }
    }

    static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> void references(
            Policies<P, O> policies, PlannedChunk<O> target, Map<Long, PlannedChunk<O>> grid) {
        ChunkPos position = target.chunk().getPos();
        List<PlannedChunk<O>> origins = new ArrayList<>(289);
        if (target.chunk().getPersistedStatus() != ChunkStatus.STRUCTURE_STARTS) {
            throw new IllegalArgumentException("Structure references require an unreferenced planned chunk: " + position);
        }
        if (!target.context().adapterEnabled()) {
            target.chunk().setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
            return;
        }
        for (int x = position.x() - 8; x <= position.x() + 8; x++) {
            for (int z = position.z() - 8; z <= position.z() + 8; z++) {
                PlannedChunk<O> origin = grid.get(ChunkPos.pack(x, z));
                if (origin == null || origin.context() != target.context()
                        || origin.chunk().getPos().x() != x || origin.chunk().getPos().z() != z
                        || !origin.chunk().getPersistedStatus().isOrAfter(ChunkStatus.STRUCTURE_STARTS)) {
                    throw new IllegalArgumentException("Missing matching structure origin at " + x + "," + z);
                }
                origins.add(origin);
            }
        }
        Map<Structure, LongSet> references = new HashMap<>();
        Registry<Structure> registry = target.context().registries().lookupOrThrow(Registries.STRUCTURE);
        for (PlannedChunk<O> origin : origins) {
            ChunkPos source = origin.chunk().getPos();
            try (NativeGenerationScope scope = policies.references().openOriginScope(source.x(), source.z())) {
                for (Map.Entry<Structure, StructureStart> entry : origin.chunk().getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (!start.isValid()) {
                        continue;
                    }
                    String key = Objects.requireNonNull(registry.getKey(entry.getKey()), "structure key").toString();
                    if (!NativeStructureReferenceEnvelope.contentFitsReferenceRange(start)) {
                        throw new IllegalStateException("Planned structure exceeds its reference range: " + key);
                    }
                    O ownership = origin.ownership().get(entry.getKey());
                    boolean relevant = ownership == null
                            ? NativeStructureReferenceRepair.requiresNaturalReference(policies.references(), position,
                            key, entry.getKey(), start)
                            : NativeStructureReferenceRepair.requiresReference(position, key, start, ownership);
                    if (relevant) {
                        references.computeIfAbsent(entry.getKey(), ignored -> new LongOpenHashSet()).add(source.pack());
                    }
                }
            }
        }
        target.chunk().setAllReferences(references);
        target.chunk().setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> Map<Structure, O> inject(
            NativeContext context, Policies<P, O> policies, ProtoChunk chunk) {
        Registry<Structure> registry = context.registries().lookupOrThrow(Registries.STRUCTURE);
        Map<Structure, O> ownership = new LinkedHashMap<>();
        for (P plan : policies.injection().plansAt(chunk.getPos().x(), chunk.getPos().z())) {
            Holder.Reference<Structure> holder = registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE,
                    Identifier.parse(plan.structureKey())));
            Structure structure = holder.value();
            if (ownership.containsKey(structure)) {
                policies.injection().duplicate(plan.structureKey());
                continue;
            }
            StructureStart existing = chunk.getStartForStructure(structure);
            boolean replace = plan.replacesSource() || existing != null && existing.isValid()
                    && policies.injection().sourceReplaced(plan.structureKey(),
                    NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
            if (!replace && existing != null && existing.isValid()) {
                continue;
            }
            int references = existing != null && existing.isValid() ? existing.getReferences() : 0;
            StructureStart generated = NativeStructureFactory.generate(new NativeStructureFactory.GenerationContext(
                    context.registries(), context.generator(), context.generator().getBiomeSource(),
                    context.structureState().randomState(), context.templates(), context.structureState().getLevelSeed(),
                    context.levelKey(), chunk, biome -> true, context.generator().getSeaLevel(),
                    policies.injection()::surfaceHeight), holder, plan, references);
            if (!generated.isValid()) {
                if (replace) {
                    chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                }
                policies.injection().discard(plan.structureKey(), chunk.getPos().x(), chunk.getPos().z());
                continue;
            }
            BoundingBox envelope = NativeStructureReferenceEnvelope.referenceBounds(generated, structure,
                    plan.terrain(), plan.structureKey());
            policies.injection().record(plan, NativeStructureOwnershipFingerprint.capture(plan.structureKey(), generated, envelope));
            O recorded;
            try {
                recorded = policies.references().findPersisted(plan.structureKey(), chunk.getPos().x(), chunk.getPos().z());
                if (!NativeStructureOwnershipFingerprint.matches(recorded, generated)) {
                    throw new IllegalStateException("Configured structure ownership was not retained: " + plan.structureKey());
                }
                chunk.setStartForStructure(structure, generated);
            } catch (RuntimeException | Error failure) {
                try {
                    policies.injection().discard(plan.structureKey(), chunk.getPos().x(), chunk.getPos().z());
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            ownership.put(structure, recorded);
        }
        return ownership;
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> Map<Structure, StructureTerrainSettings> adjust(
            NativeContext context, Policies<P, O> policies, ProtoChunk chunk, Map<Structure, O> ownership) {
        Registry<Structure> registry = context.registries().lookupOrThrow(Registries.STRUCTURE);
        Map<Structure, StructureTerrainSettings> terrain = new HashMap<>();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            String key = Objects.requireNonNull(registry.getKey(structure), "structure key").toString();
            if (!policies.allowsFootprint().test(start.getBoundingBox())) {
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                if (ownership.remove(structure) != null) {
                    policies.references().discard(key, chunk.getPos().x(), chunk.getPos().z());
                }
                continue;
            }
            O recorded = ownership.get(structure);
            if (recorded != null) {
                terrain.put(structure, NativeStructureTerrainIntegrator.resolveNativeTerrain(start, recorded.terrain()));
                continue;
            }
            boolean underground = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
            StructurePlacementDecision decision = policies.references().resolve(key, underground);
            if (!decision.generate()) {
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }
            NativeStructureVerticalPlacer.applyVerticalPlacement(start, key, decision.yShift(),
                    context.generator().getSeaLevel(), chunk.getMinY(), chunk.getMinY() + chunk.getHeight(),
                    underground, decision.preserveSourceY(), decision.yBand(), policies.injection()::surfaceHeight);
            StructureTerrainSettings resolved = NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain());
            StructureStart wrapped = NativeStructureReferenceEnvelope.wrapForPublication(start, structure,
                    start.getReferences(), resolved, key);
            chunk.setStartForStructure(structure, wrapped);
            if (wrapped.isValid()) {
                terrain.put(structure, resolved);
            }
        }
        return terrain;
    }

    static void generateNaturalStarts(NativeContext context, ChunkAccess chunk) {
        if (!context.enabled() || SharedConstants.DEBUG_DISABLE_STRUCTURES) {
            return;
        }
        RandomState random = context.structureState().randomState();
        Climate.Sampler climate = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
        for (Holder<StructureSet> holder : context.structureState().possibleStructureSets()) {
            generateSet(context, chunk, holder.value(), climate);
        }
    }

    private static void generateSet(NativeContext context, ChunkAccess chunk, StructureSet set, Climate.Sampler climate) {
        List<StructureSet.StructureSelectionEntry> entries = set.structures();
        for (StructureSet.StructureSelectionEntry entry : entries) {
            StructureStart existing = chunk.getStartForStructure(entry.structure().value());
            if (existing != null && existing.isValid()) {
                return;
            }
        }
        StructurePlacement placement = set.placement();
        ChunkPos position = chunk.getPos();
        ResourceKey<StructureSet> key = placement instanceof ChunkGeneratorStructureState.KeyedRandomSpreadStructurePlacement keyed
                ? keyed.key : null;
        if (!placement.isStructureChunk(context.structureState(), position.x(), position.z(), key)) {
            return;
        }
        if (entries.size() == 1) {
            generate(context, chunk, entries.getFirst(), climate);
            return;
        }
        ArrayList<StructureSet.StructureSelectionEntry> remaining = new ArrayList<>(entries);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(context.structureState().getLevelSeed(), position.x(), position.z());
        int weight = 0;
        for (StructureSet.StructureSelectionEntry entry : remaining) {
            weight += entry.weight();
        }
        while (!remaining.isEmpty()) {
            int selection = random.nextInt(weight);
            int index = 0;
            for (StructureSet.StructureSelectionEntry entry : remaining) {
                selection -= entry.weight();
                if (selection < 0) {
                    break;
                }
                index++;
            }
            StructureSet.StructureSelectionEntry selected = remaining.get(index);
            if (generate(context, chunk, selected, climate)) {
                return;
            }
            remaining.remove(index);
            weight -= selected.weight();
        }
    }

    private static boolean generate(NativeContext context, ChunkAccess chunk,
                                    StructureSet.StructureSelectionEntry entry, Climate.Sampler climate) {
        Structure structure = entry.structure().value();
        StructureStart existing = chunk.getStartForStructure(structure);
        int references = existing == null ? 0 : existing.getReferences();
        StructureStart generated = structure.generate(entry.structure(), context.levelKey(), context.registries(),
                context.generator(), context.generator().getBiomeSource(), climate, context.structureState().randomState(),
                context.templates(), context.structureState().getLevelSeed(), chunk.getPos(), references, chunk,
                structure.biomes()::contains);
        if (!generated.isValid()) {
            return false;
        }
        chunk.setStartForStructure(structure, generated);
        return true;
    }

    record NativeContext(RegistryAccess registries, ChunkGenerator generator,
                         ChunkGeneratorStructureState structureState, StructureTemplateManager templates,
                         ResourceKey<Level> levelKey, WorldOptions worldOptions, boolean adapterEnabled) {
        NativeContext {
            Objects.requireNonNull(registries, "registries");
            Objects.requireNonNull(generator, "generator");
            Objects.requireNonNull(structureState, "structureState");
            Objects.requireNonNull(templates, "templates");
            Objects.requireNonNull(levelKey, "levelKey");
            Objects.requireNonNull(worldOptions, "worldOptions");
            if (worldOptions.seed() != structureState.getLevelSeed()) {
                throw new IllegalArgumentException("Structure state seed differs from world options");
            }
        }

        boolean enabled() {
            return worldOptions.generateStructures() && adapterEnabled;
        }
    }

    record Policies<P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>>(
            StructureInjectionPolicy<P> injection, StructureReferencePolicy<P, O> references,
            Predicate<BoundingBox> allowsFootprint) {
        Policies {
            Objects.requireNonNull(injection, "injection");
            Objects.requireNonNull(references, "references");
            Objects.requireNonNull(allowsFootprint, "allowsFootprint");
        }
    }

    record PlannedChunk<O extends StructureOwnershipRecordView<O>>(NativeContext context, ProtoChunk chunk,
            Map<Structure, O> ownership, Map<Structure, StructureTerrainSettings> terrain) {
    }
}
