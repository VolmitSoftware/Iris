package art.arcane.iris.client;

import art.arcane.volmlib.nativelib.client.ClientGraphics;
import art.arcane.volmlib.nativelib.client.ClientWorldPolicy;
import art.arcane.volmlib.nativelib.client.ClientWorldPolicies;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedWorldgenIds;
import art.arcane.iris.modded.localization.ClientUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.nativelib.client.ClientHudBinding;

import java.util.List;

/**
 * CLIENT DIST ONLY. Calls the native client implementation and must never be reachable from
 * art.arcane.iris.modded or art.arcane.iris.nativegen. ModdedClientPackageIsolationTest enforces that
 * direction; there is no @Environment annotation because net.fabricmc.api is not on the Forge or NeoForge
 * compile classpath and this source set builds for all three loaders.
 */
public final class IrisClientHud {
    private IrisClientHud() {
    }

    public static ClientHudBinding binding() {
        ClientWorldPolicies.register(new ClientWorldPolicy("irisworldgen", IrisModdedChunkGenerator.class,
                ModdedWorldgenIds::displayName, true, true,
                () -> IrisLanguage.plain(ClientUiMessages.CREATE_STRUCTURES_REQUIRED_TITLE),
                () -> IrisLanguage.plain(ClientUiMessages.CREATE_STRUCTURES_REQUIRED_BODY)));
        return new ClientHudBinding(IrisClient.HUD_ELEMENT_ID,
                List.of(IrisClientKeybinds.TOGGLE_HUD, IrisClientKeybinds.OPEN_MAP, IrisClientKeybinds.TOGGLE_WHAT),
                IrisClientHud::render, IrisClientHud::clientTick, IrisClientKeybinds::pollToggle);
    }

    private static void clientTick() {
        IrisClient.tick();
        tick();
    }

    /**
     * Per client tick, independent of the HUD layer. Toasts are pumped here rather than from
     * {@link #render(ClientGraphics)} because the whole layered HUD draw is skipped while hideGui (F1)
     * is on, which would silently strand every queued toast until the player pressed F1 again.
     */
    public static void tick() {
        IrisToastPresenter.pump();
    }

    public static void render(ClientGraphics graphics) {
        IrisPregenHud.render(graphics);
        IrisWhatOverlay.render(graphics);
    }
}
