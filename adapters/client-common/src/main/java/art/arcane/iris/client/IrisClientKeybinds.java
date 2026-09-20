package art.arcane.iris.client;

import art.arcane.volmlib.nativelib.client.ClientKeyBinding;
import art.arcane.volmlib.nativelib.client.ClientKeyCategory;
import art.arcane.volmlib.nativelib.minecraft26_2.client.NativeClientAccess;


/**
 * CLIENT DIST ONLY. Static key bindings call the native client runtime; loading this on a dedicated server causes a
 * NoClassDefFoundError. Reachable only from the per-loader client shims, which are Dist.CLIENT gated.
 * ModdedClientPackageIsolationTest enforces that no modded or nativegen class reaches it. No @Environment
 * annotation: net.fabricmc.api is absent from the Forge and NeoForge compile classpath.
 */
public final class IrisClientKeybinds {
    private static final ClientKeyCategory CATEGORY = NativeClientAccess.category(IrisClient.KEYBIND_CATEGORY_ID);
    public static final ClientKeyBinding TOGGLE_HUD = NativeClientAccess.key(IrisClient.KEYBIND_TOGGLE_HUD, 72, CATEGORY);
    public static final ClientKeyBinding OPEN_MAP = NativeClientAccess.key(IrisClient.KEYBIND_OPEN_MAP, 77, CATEGORY);
    public static final ClientKeyBinding TOGGLE_WHAT = NativeClientAccess.key(IrisClient.KEYBIND_TOGGLE_WHAT, 74, CATEGORY);

    private IrisClientKeybinds() {
    }

    public static ClientKeyCategory category() {
        return CATEGORY;
    }

    public static void pollToggle() {
        while (TOGGLE_HUD.consumeClick()) {
            IrisClient.toggleHud();
        }
        while (TOGGLE_WHAT.consumeClick()) {
            IrisClient.toggleWhat();
        }
        while (OPEN_MAP.consumeClick()) {
            openVisionScreen();
        }
    }

    private static void openVisionScreen() {
        NativeClientAccess.openScreen(new IrisVisionScreen());
    }
}
