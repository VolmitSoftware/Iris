package art.arcane.iris.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore;
import art.arcane.iris.structure.nativegen.NativeStructurePlacementPlanner;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.iris.spi.IrisLogging;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeStructureStartInjector {
    private static final Set<String> WARNED_DUPLICATE_STRUCTURES = ConcurrentHashMap.newKeySet();

    private NativeStructureStartInjector() {
    }

    public static Map<Structure, NativeStructureStartPlan> inject(InjectionContext context) {
        Objects.requireNonNull(context, "Native structure injection context must not be null");
        ChunkAccess chunk = context.chunk();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        SectionPos section = SectionPos.bottomOf(chunk);
        Map<Structure, NativeStructureStartPlan> configuredStarts = new LinkedHashMap<>();
        for (NativeStructureStartPlan plan : NativeStructurePlacementPlanner.plansAt(
                context.engine(), chunk.getPos().x(), chunk.getPos().z())) {
            Identifier identifier = Identifier.tryParse(plan.source().getStructure());
            if (identifier == null) {
                throw new IllegalStateException("Configured native structure key is invalid: "
                        + plan.source().getStructure());
            }
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                throw new IllegalStateException("Configured native structure is not registered: " + identifier);
            }
            Holder<Structure> holder = registry.wrapAsHolder(structure);
            if (configuredStarts.containsKey(structure)) {
                if (WARNED_DUPLICATE_STRUCTURES.add(identifier.toString())) {
                    IrisLogging.warn("Ignoring duplicate native structure placements for '"
                            + identifier + "'; the first deterministic candidate owns each chunk start");
                }
                continue;
            }
            StructureStart existing = context.structureManager().getStartForStructure(
                    section, structure, chunk);
            boolean replacement = plan.placement().getNativeSuppression()
                    == NativeStructureSuppression.REPLACE_SOURCE;
            if (!replacement && existing != null && existing.isValid()) {
                NativeStructureGenerationStatus sourceStatus = NativeStructureGenerationPolicy.resolve(
                        context.engine(), identifier.toString(),
                        NativeStructureVegetationClearer.isUndergroundStep(structure.step())).status();
                replacement = sourceStatus == NativeStructureGenerationStatus.REPLACED_BY_IRIS;
            }
            if (!replacement && existing != null && existing.isValid()) {
                continue;
            }
            int references = existing != null && existing.isValid() ? existing.getReferences() : 0;
            NativeStructureFactory.GenerationContext generationContext =
                    new NativeStructureFactory.GenerationContext(
                            context.registryAccess(),
                            context.generator(),
                            context.biomeSource(),
                            context.structureState().randomState(),
                            context.templateManager(),
                            context.structureState().getLevelSeed(),
                            context.levelKey(),
                            chunk,
                            biome -> true,
                            context.generator().getSeaLevel(),
                            (x, z) -> Engine.hostHeight(context.engine(), x, z, true)
                                    + context.engine().getMinHeight()
                    );
            StructureStart generated = NativeStructureFactory.generate(
                    generationContext, holder, plan, references);
            if (!isUsableGeneratedStart(generated)) {
                if (replacement) {
                    context.structureManager().setStartForStructure(
                            section, structure, StructureStart.INVALID_START, chunk);
                }
                NativeStructureOwnershipStore.discard(
                        context.engine(), identifier.toString(),
                        chunk.getPos().x(), chunk.getPos().z());
                continue;
            }
            BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                    generated, structure, plan.placement().resolvedTerrain(), identifier.toString());
            NativeStructureOwnershipRecord ownership = NativeStructureOwnershipFingerprint.capture(
                    identifier.toString(), generated, plan, referenceBounds);
            NativeStructureOwnershipStore.record(context.engine(), ownership);
            try {
                context.structureManager().setStartForStructure(
                        section, structure, generated, chunk);
            } catch (RuntimeException | Error publicationError) {
                try {
                    NativeStructureOwnershipStore.discard(
                            context.engine(), identifier.toString(),
                            chunk.getPos().x(), chunk.getPos().z());
                } catch (RuntimeException | Error cleanupError) {
                    publicationError.addSuppressed(cleanupError);
                }
                throw publicationError;
            }
            configuredStarts.put(structure, plan);
        }
        return Map.copyOf(configuredStarts);
    }

    static boolean isUsableGeneratedStart(StructureStart start) {
        return start != null && start.isValid();
    }

    public record InjectionContext(
            Engine engine,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            ResourceKey<Level> levelKey,
            ChunkGenerator generator,
            BiomeSource biomeSource
    ) {
    }
}
