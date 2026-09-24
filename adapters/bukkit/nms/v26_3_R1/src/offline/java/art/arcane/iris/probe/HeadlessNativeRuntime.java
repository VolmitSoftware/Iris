package art.arcane.iris.probe;

import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.DecoratorPlatformHooks;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.modded.ModdedDecoratorHooks;
import art.arcane.iris.modded.ModdedStateRotator;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTileReader;
import art.arcane.iris.pack.datapack.v263.DataFixerV263;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStateMerger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.packs.resources.ResourceManager;

final class HeadlessNativeRuntime implements RealPackProbeSupport.RuntimeBindings {
    private final Path stagingRoot;
    private NativeRegionTerrainWriter writer;
    private HeadlessNativeRegistries nativeRegistries;
    private HolderLookup.Provider registries;

    HeadlessNativeRuntime() {
        stagingRoot = null;
    }

    HeadlessNativeRuntime(Path stagingRoot) throws IOException {
        this.stagingRoot = stagingRoot.toRealPath();
    }

    @Override
    public IrisPlatform create(File root) {
        HeadlessNativeBootstrap.initialize();
        registries = VanillaRegistries.createWorldLookup();
        HeadlessNativePlatform platform = new HeadlessNativePlatform(root, () -> registries);
        ModdedDecoratorHooks decorators = new ModdedDecoratorHooks();
        DecoratorPlatformHooks.bind(decorators, decorators);
        IrisObjectRotation.bindPlatformRotator(new ModdedStateRotator());
        BlockDataMergeSupport.bindPlatformMerger(new NativeStateMerger(platform.blocks())::merge);
        NativeTileReader tiles = new NativeTileReader(() -> registries);
        TileData.bindPlatformReader(input -> ModdedTileData.wrap(tiles.read(input)));
        TileData.bindPlatformFactory(ModdedTileData::fromProperties);
        return platform;
    }

    @Override
    public void prepare(IrisData data, IrisDimension dimension) {
        try {
            if (nativeRegistries != null) {
                throw new IllegalStateException("Headless native runtime supports one immutable pack per process");
            }
            nativeRegistries = HeadlessNativeRegistries.compile(data.getDataFolder());
            registries = nativeRegistries.registries();
            writer = new NativeRegionTerrainWriter(nativeRegistries.registries());
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot load native generation registries", failure);
        }
    }

    @Override
    public KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return NativeStructureVolumeIndex.volumes(engine, minX, minZ, maxX, maxZ);
    }

    @Override
    public GenerationHistory createHistory(RealPackProbeSupport.HistoryRequest request) throws IOException {
        if (stagingRoot != null) {
            if (!request.worldRoot().getParent().toRealPath().startsWith(stagingRoot)) {
                throw new IOException("Unpublished native generation must remain inside its private staging directory");
            }
            return HeadlessGenerationHistorySession.createUnpublished(request, new DataFixerV263());
        }
        return HeadlessGenerationHistorySession.create(request, new DataFixerV263());
    }

    @Override
    public void close() throws IOException {
        if (nativeRegistries != null) {
            nativeRegistries.close();
            nativeRegistries = null;
        }
    }

    RegistryAccess.Frozen registries() {
        return nativeRegistries.registries();
    }

    ResourceManager resources() {
        return nativeRegistries.resources();
    }

    NativeRegionTerrainWriter writer() {
        return writer;
    }
}
