package art.arcane.iris.neoforge;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedLifecycleCallbacks;
import art.arcane.iris.modded.service.ModdedTreeFellerService;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;
import art.arcane.volmlib.nativelib.minecraft26_2.neoforge.NativeNeoForgeLoader;
import art.arcane.volmlib.nativelib.minecraft26_2.neoforge.NativeNeoForgeBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

@Mod("irisworldgen")
public final class IrisNeoForgeBootstrap {
    public IrisNeoForgeBootstrap(IEventBus modBus) {
        NativeNeoForgeLoader loader = new NativeNeoForgeLoader(new NativeLoaderOptions(
                "irisworldgen", "treefeller", ModdedTreeFellerService::runBreakProbe));
        NativeChunkGeneratorDefinition generator = IrisModdedChunkGenerator.DEFINITION;
        ModdedEngineBootstrap.bootCommon(loader, "NeoForge " + FMLLoader.getCurrent().getVersionInfo().neoForgeVersion(),
                () -> NativeNeoForgeBootstrap.registerGenerator(modBus, generator));
        NeoForgeProtocolNetworking.register(modBus);
        if (loader.clientEnvironment()) {
            IrisNeoForgeClient.init(modBus);
        }
        NativeNeoForgeBootstrap.install(modBus, loader, ModdedLifecycleCallbacks.create());
    }
}
