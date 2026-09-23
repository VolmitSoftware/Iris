package art.arcane.iris.probe;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.nms.BukkitBiomePolicy;
import art.arcane.iris.structure.nativegen.IrisStructurePolicy;
import art.arcane.iris.structure.nativegen.IrisStructureVolumePolicy;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeBiomeRegistryImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVolumeSource;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spigotmc.SpigotWorldConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

final class HeadlessTerrainContext implements AutoCloseable {
    private final Engine engine;
    private final LevelStorageSource.LevelStorageAccess storage;
    private final HeadlessStructurePlanner.NativeContext structures;
    private final HeadlessStructurePlanner.Policies<NativeStructureStartPlan, NativeStructureOwnershipRecord> policies;
    private final BukkitBiomePolicy<Holder<Biome>, Biome> biomes;
    private final HeadlessChunkSerialization.Context serialization;
    private final NativeRegionTerrainWriter writer;

    private HeadlessTerrainContext(Options options, LevelStorageSource.LevelStorageAccess storage) {
        engine = options.engine();
        this.storage = storage;
        HeadlessNativeRuntime runtime = options.runtime();
        writer = runtime.writer();
        HeadlessChunkGenerator generator = new HeadlessChunkGenerator(new HeadlessChunkGenerator.Options(
                engine, runtime.registries()));
        long seed = engine.getSeedManager().getSeed();
        NoiseGeneratorSettings settings = runtime.registries().lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(options.noiseSettings()).value();
        RandomState random = RandomState.create(runtime.registries().lookupOrThrow(Registries.NOISE), seed, settings);
        ChunkGeneratorStructureState state = generator.createState(
                runtime.registries().lookupOrThrow(Registries.STRUCTURE_SET), random, seed, options.configuration());
        initializeStructureState(state);
        StructureTemplateManager templates = new StructureTemplateManager(runtime.resources(), storage,
                DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
        structures = new HeadlessStructurePlanner.NativeContext(runtime.registries(), generator, state,
                templates, options.levelKey(), new WorldOptions(seed, true, false), true);
        IrisStructurePolicy policy = new IrisStructurePolicy(engine);
        policies = new HeadlessStructurePlanner.Policies<>(policy, policy,
                bounds -> engine.getComplex().allowsNewGenerationFootprint(
                        bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()));
        NativeBiomeRegistryImpl registry = new NativeBiomeRegistryImpl(runtime.registries().lookupOrThrow(Registries.BIOME));
        biomes = new BukkitBiomePolicy<>(new BukkitBiomePolicy.RuntimeOptions<>(seed, engine, null, () -> registry), registry);
        serialization = new HeadlessChunkSerialization.Context(new StructurePieceSerializationContext(
                runtime.resources(), runtime.registries(), templates), PalettedContainerFactory.create(runtime.registries()), 0L);
        NativeStructureVolumeSource<Engine, NativeStructureStartPlan> volumes = new NativeStructureVolumeSource<>(
                new NativeStructureVolumeSource.Context(runtime.registries(), templates, options.levelKey(),
                        LevelHeightAccessor.create(engine.getMinHeight(), engine.getHeight()),
                        () -> generator, generator::getBiomeSource, () -> state), IrisStructureVolumePolicy.INSTANCE);
        NativeStructureVolumeIndex.install(engine, volumes::volumesAt);
    }

    static HeadlessTerrainContext create(Options options) throws IOException {
        LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(options.templateStorage())
                .createAccess("native-templates");
        try {
            return new HeadlessTerrainContext(options, storage);
        } catch (RuntimeException | Error failure) {
            try {
                storage.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void initializeStructureState(ChunkGeneratorStructureState state) {
        state.ensureStructuresGenerated();
        Throwable failure = null;
        for (Holder<StructureSet> structureSet : state.possibleStructureSets()) {
            if (!(structureSet.value().placement() instanceof ConcentricRingsStructurePlacement rings)) {
                continue;
            }
            try {
                state.getRingPositionsFor(rings);
            } catch (RuntimeException | Error problem) {
                if (failure == null) {
                    failure = problem;
                } else {
                    failure.addSuppressed(problem);
                }
            }
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    @Override
    public void close() throws IOException {
        NativeStructureVolumeIndex.uninstall(engine);
        storage.close();
    }

    Engine engine() {
        return engine;
    }

    HeadlessStructurePlanner.NativeContext structures() {
        return structures;
    }

    HeadlessStructurePlanner.Policies<NativeStructureStartPlan, NativeStructureOwnershipRecord> policies() {
        return policies;
    }

    BukkitBiomePolicy<Holder<Biome>, Biome> biomes() {
        return biomes;
    }

    HeadlessChunkSerialization.Context serialization() {
        return serialization;
    }

    NativeRegionTerrainWriter writer() {
        return writer;
    }

    record Options(Engine engine, HeadlessNativeRuntime runtime, Path templateStorage, ResourceKey<Level> levelKey,
                   ResourceKey<NoiseGeneratorSettings> noiseSettings, SpigotWorldConfig configuration) {
        Options {
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(templateStorage, "templateStorage");
            Objects.requireNonNull(levelKey, "levelKey");
            Objects.requireNonNull(noiseSettings, "noiseSettings");
            Objects.requireNonNull(configuration, "configuration");
        }
    }
}
