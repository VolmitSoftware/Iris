package art.arcane.iris.fabric;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedLifecycleCallbacks;
import art.arcane.iris.modded.service.ModdedTreeFellerService;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;
import art.arcane.volmlib.nativelib.minecraft26_2.fabric.NativeFabricLoader;
import art.arcane.volmlib.nativelib.minecraft26_2.fabric.NativeFabricBootstrap;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.volmlib.nativelib.minecraft26_2.fabric.NativeFabricPackSources;
import net.fabricmc.api.ModInitializer;

public final class IrisFabricBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        NativeFabricLoader loader = new NativeFabricLoader(new NativeLoaderOptions(
                "irisworldgen", "treefeller", ModdedTreeFellerService::runBreakProbe));
        NativeChunkGeneratorDefinition generator = IrisModdedChunkGenerator.DEFINITION;
        NativeFabricPackSources.bind(ModdedForcedDatapack::repositorySource);
        ModdedEngineBootstrap.bootCommon(loader, "Fabric", () -> NativeFabricBootstrap.registerGenerator(generator));
        FabricProtocolNetworking.install();
        NativeFabricBootstrap.install(ModdedLifecycleCallbacks.create());
    }
}
