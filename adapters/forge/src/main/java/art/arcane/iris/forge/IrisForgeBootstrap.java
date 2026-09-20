package art.arcane.iris.forge;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedLifecycleCallbacks;
import art.arcane.iris.modded.service.ModdedTreeFellerService;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;
import art.arcane.volmlib.nativelib.minecraft26_2.forge.NativeForgeLoader;
import art.arcane.volmlib.nativelib.minecraft26_2.forge.NativeForgeBootstrap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod("irisworldgen")
public final class IrisForgeBootstrap {
    public IrisForgeBootstrap(FMLJavaModLoadingContext context) {
        NativeForgeLoader loader = new NativeForgeLoader(new NativeLoaderOptions(
                "irisworldgen", "treefeller", ModdedTreeFellerService::runBreakProbe));
        NativeChunkGeneratorDefinition generator = IrisModdedChunkGenerator.DEFINITION;
        ModdedEngineBootstrap.bootCommon(loader, "Forge " + FMLLoader.versionInfo().forgeVersion(),
                () -> NativeForgeBootstrap.registerGenerators(context, generator, "block_drops"));
        ForgeProtocolNetworking.register();
        if (loader.clientEnvironment()) {
            IrisForgeClient.init();
        }
        NativeForgeBootstrap.install(loader, ModdedLifecycleCallbacks.create());
    }
}
