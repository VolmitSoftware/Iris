package art.arcane.iris.modded;

import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;

public final class ModdedLifecycleCallbacks {
    private ModdedLifecycleCallbacks() {
    }

    public static NativeModdedCallbacks create() {
        return new NativeModdedCallbacks(
                ModdedEngineBootstrap::serverAboutToStart,
                ModdedEngineBootstrap::start,
                ModdedEngineBootstrap::serverStarted,
                ModdedEngineBootstrap::stop,
                ModdedEngineBootstrap::levelLoaded,
                ModdedEngineBootstrap::levelUnloaded,
                ModdedProtocolHandler::onPlayerJoin,
                ModdedProtocolHandler::onPlayerDisconnect,
                ModdedForcedDatapack::repositorySource,
                IrisModdedCommands::register,
                ModdedBlockBreakHandler::prepare,
                ModdedBlockBreakHandler::cancel,
                ModdedBlockBreakHandler::clearPlacedProvenance,
                ModdedWandService::attackBlock,
                ModdedWandService::useBlock,
                server -> {
                    ModdedEngineBootstrap.tick(server);
                    ModdedWandService.serverTick(server);
                });
    }
}
