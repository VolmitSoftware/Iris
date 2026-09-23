package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.EngineLifecycleTasks;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.structure.nativegen.IrisStructurePolicy;
import art.arcane.iris.structure.nativegen.ImportedFeaturePolicy;
import art.arcane.iris.structure.nativegen.IrisStructureVolumePolicy;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.NativeBiomeSpawnSelection;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBukkitGeneratorContext;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnSelection;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy;
import art.arcane.volmlib.nativelib.terrain.NativeStructureBootstrapPolicy;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureVolumePolicy;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import java.io.IOException;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class BukkitGeneratorContext extends BukkitGenerationPolicy implements NativeBukkitGeneratorContext<
        Engine, IrisDimension, NativeStructureStartPlan, NativeStructureOwnershipRecord, GenerationHistoryRuntimeRouter.RuntimeRoute> {
    private static final NamespacedKey STRUCTURE_ACTIVATION = new NamespacedKey("iris", "structure_activation");
    private final Options options;
    private final BukkitChunkGenerator generator;
    private final BukkitStructureStagePolicy structures;
    private final IrisStructurePolicy injection;

    public BukkitGeneratorContext(Options options) {
        super(options.engine(), options.world().getGenerator() instanceof BukkitChunkGenerator generator ? generator : null);
        this.options = options;
        this.generator = options.world().getGenerator() instanceof BukkitChunkGenerator generator ? generator : null;
        this.structures = new BukkitStructureStagePolicy(options.engine());
        this.injection = new IrisStructurePolicy(options.engine());
    }

    @Override
    public Engine current() { return options.engine(); }

    @Override
    public <H, V> NativeBiomeSourcePolicy<H> biomes(NativeBiomeRegistry<H, V> registry) {
        return new BukkitBiomePolicy<>(new BukkitBiomePolicy.RuntimeOptions<>(options.seed(), current(), generator,
                () -> worldBiomeRegistry(registry)), registry);
    }

    private <H, V> NativeBiomeRegistry<H, V> worldBiomeRegistry(NativeBiomeRegistry<H, V> registry) {
        World world = BukkitWorldBinding.world(current().getWorld());
        if (world == null) {
            throw new IllegalStateException("Iris biome source has no bound Bukkit world");
        }
        return registry.forWorld(world);
    }

    @Override
    public NativeImportedFeaturePolicy<IrisDimension> importedFeatures() { return new ImportedFeaturePolicy(current()); }

    @Override
    public NativeStructureBootstrapPolicy bootstrap() { return new BukkitStructureBootstrapPolicy(current(), options.world().getName()); }

    @Override
    public NativeTerrainColumnPolicy columns() { return new BukkitTerrainColumnPolicy(current(), generator); }

    @Override
    public BukkitStructureStagePolicy structures() { return structures; }

    @Override
    public BukkitStructureStagePolicy structureCache() { return structures; }

    @Override
    public IrisStructurePolicy injection() { return injection; }

    @Override
    public StructureVolumePolicy<Engine, NativeStructureStartPlan> volumes() { return IrisStructureVolumePolicy.INSTANCE; }

    @Override
    public void installVolumes(VolumeResolver<Engine> resolver) {
        NativeStructureVolumeIndex.install(current(), resolver::volumesAt);
    }

    @Override
    public void onRetirement(IntConsumer listener) {
        if (current() instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(listener);
        }
    }

    @Override
    public int fluidHeight() { return current().getDimension().getFluidHeight(); }

    @Override
    public int runtimeId() { return current().getCacheID(); }

    @Override
    public boolean generateStructures() { return generator == null || generator.shouldGenerateStructures(); }

    @Override
    public boolean allowsNewChunk(int chunkX, int chunkZ) { return current().getComplex().allowsNewGenerationChunk(chunkX, chunkZ); }

    @Override
    public boolean active() { return !current().isClosing() && !current().isClosed(); }

    @Override
    public boolean stacked() { return current().getDimensionStackContext() != null; }

    @Override
    public NativeGenerationScope openCoordinateScope(int blockX, int blockZ, String operation) {
        if (!(current() instanceof IrisEngine irisEngine)) { return null; }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(blockX, blockZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation + " could not route generation history at "
                    + blockX + "," + blockZ + ".", failure);
        }
    }

    @Override
    public NativeSpawnSelection spawnSelection(int blockX, int worldY, int blockZ, String physicalBiomeKey) {
        return NativeBiomeSpawnSelection.at(current(), blockX, worldY, blockZ, physicalBiomeKey);
    }

    @Override
    public <T> T admittedSpawns(Supplier<T> operation, T fallback) {
        return EngineLifecycleTasks.call(current(), "bukkit_nms_mob_spawns", operation, fallback);
    }

    @Override
    public NamespacedKey structureActivationKey() { return STRUCTURE_ACTIVATION; }

    @Override
    public long activationId(GenerationHistoryRuntimeRouter.RuntimeRoute route) { return route.activation().activationId(); }

    @Override
    public void failed(String operation, Throwable failure) { IrisLogging.reportError(operation, failure); }

    public record Options(long seed, Engine engine, World world) { }
}
