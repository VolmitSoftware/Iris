package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.pack.datapack.DatapackStructureScopeIndex;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainAccess;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainSnapshots;
import art.arcane.volmlib.nativelib.terrain.NativeWorldGeneration;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecycleFactory;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import lombok.experimental.Delegate;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BukkitBinding implements INMSBinding {
    @Delegate
    private final NativeTerrainAccess terrain;
    @Delegate
    private final NativeWorldLifecycleFactory.Controller worldLifecycle;
    private final NativeWorldGeneration generation;
    private final NativeTerrainSnapshots snapshots;
    private final NativeGenerationRegistry registry;
    private volatile DataVersion dataVersion;

    public BukkitBinding() {
        this(new Capabilities(NativeAdapters.require(NativeTerrainAccess.class),
                NativeAdapters.require(NativeWorldGeneration.class), NativeAdapters.require(NativeTerrainSnapshots.class),
                NativeAdapters.require(NativeGenerationRegistry.class)));
    }

    public BukkitBinding(Capabilities capabilities) {
        terrain = capabilities.terrain();
        generation = capabilities.generation();
        snapshots = capabilities.snapshots();
        registry = capabilities.registry();
        worldLifecycle = generation.lifecycle(BukkitWorldLifecyclePolicy.INSTANCE);
    }

    @Override
    public PlatformGenerationRegistry generationRegistry() {
        return new BukkitGenerationRegistry(registry, "bukkit-generation-registry-v1", generation.generationRendererIdentity());
    }

    @Override
    public CompoundTag serializeEntity(Entity entity) { return null; }

    @Override
    public Entity deserializeEntity(CompoundTag tag, Location position) { return null; }

    @Override
    public boolean supportsCustomHeight() { return true; }

    @Override
    public int getMinHeight(World world) { return world.getMinHeight(); }

    @Override
    public boolean supportsCustomBiomes() { return true; }

    @Override
    public boolean supportsIrisWorldGeneration() { return true; }

    @Override
    public boolean supportsStructureCapture() { return true; }

    @Override
    public boolean supportsDataPacks() { return true; }

    @Override
    public KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return NativeStructureVolumeIndex.volumes(engine, minX, minZ, maxX, maxZ);
    }

    @Override
    public void invalidateNativeStructureVolumeIndex(Engine engine) { NativeStructureVolumeIndex.invalidate(engine); }

    @Override
    public CompletableFuture<Void> flushSavedTerrainCapture(World world) {
        return snapshots.flush(world, SavedTerrainSnapshotPolicy.INSTANCE);
    }

    @Override
    public CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(World world, int chunkX, int chunkZ, int minimumY, int height) {
        return snapshots.capture(new NativeTerrainSnapshots.CaptureRequest(world, chunkX, chunkZ, minimumY, height), SavedTerrainSnapshotPolicy.INSTANCE);
    }

    @Override
    public void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException {
        validateDimension(engine, world, generation.dimension(world));
        generation.inject(world, new BukkitGeneratorContext(new BukkitGeneratorContext.Options(seed, engine, world)));
    }

    @Override
    public DatapackStructureScopeResult scopeDatapackStructures(World world, DatapackStructureScopeIndex scope,
                                                               Set<String> declaredSources, IrisImportedStructureControl control)
            throws NoSuchFieldException, IllegalAccessException {
        BukkitChunkGenerator generator = world.getGenerator() instanceof BukkitChunkGenerator current ? current : null;
        NativeWorldGeneration.ScopeMode mode = generator != null && generator.isAuthoringStudio()
                ? NativeWorldGeneration.ScopeMode.AUTHORING
                : generator != null && generator.isStudioEntryBootstrapActive()
                ? NativeWorldGeneration.ScopeMode.RETAINED : NativeWorldGeneration.ScopeMode.IMMEDIATE;
        NativeWorldGeneration.ScopeResult result = generation.scope(new NativeWorldGeneration.ScopeRequest(world, scope, declaredSources, control, mode));
        return new DatapackStructureScopeResult(result.retainedManagedSets(), result.excludedManagedSets());
    }

    @Override
    public CompletableFuture<Void> completeStudioStructureBootstrap(World world) throws NoSuchFieldException, IllegalAccessException {
        return generation.completeBootstrap(world);
    }

    @Override
    public void abandonStudioStructureBootstrap(World world) { generation.abandonBootstrap(world); }

    @Override
    public boolean missingDimensionTypes(String... keys) {
        return generation.missingDimensionTypes(Arrays.stream(keys).map(key -> new NamespacedKey("iris", key)).toList());
    }

    @Override
    public DataVersion getDataVersion() {
        DataVersion cached = dataVersion;
        if (cached == null) {
            MinecraftVersion version = MinecraftVersion.detect(Bukkit.getServer());
            cached = DataVersion.forMinecraftVersion(version == null ? null : version.value());
            dataVersion = cached;
        }
        return cached;
    }

    @Override
    public int getSpawnChunkCount(World world) { return 0; }

    private static void validateDimension(Engine engine, World world, NativeWorldGeneration.Dimension dimension) {
        IrisDimensionRuntimeContract expected = IrisDimensionRuntimeContract.expected(engine.getDimension(), "iris");
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract(dimension.key(), dimension.minimumY(), dimension.height(), dimension.logicalHeight());
        String runtimeName = "Bukkit world '" + world.getName() + "'";
        expected.requireExact(runtimeName, actual);
        expected.requireHeight(runtimeName, dimension.worldMinimumY(), dimension.worldHeight());
        expected.requireHeight(runtimeName, world.getMinHeight(), world.getMaxHeight() - world.getMinHeight());
        IrisLogging.debug("Loaded world " + world.getName() + " with exact Iris dimension type " + dimension.key());
    }

    public record Capabilities(NativeTerrainAccess terrain, NativeWorldGeneration generation,
                               NativeTerrainSnapshots snapshots, NativeGenerationRegistry registry) { }
}
