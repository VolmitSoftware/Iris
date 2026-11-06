package art.arcane.iris.core.studio.assets;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinecraftAssetsTest {
    @Test
    public void onlyFilesInTheClientsAssetFolderAreServed() {
        assertTrue(MinecraftAssets.isAssetPath("assets/minecraft/textures/block/stone.png"));
        assertFalse(MinecraftAssets.isAssetPath("net/minecraft/client/Main.class"));
        assertFalse(MinecraftAssets.isAssetPath("assets/../META-INF/MANIFEST.MF"));
        assertFalse(MinecraftAssets.isAssetPath("assets//minecraft/models/block/stone.json"));
        assertFalse(MinecraftAssets.isAssetPath("assets\\minecraft\\x.json"));
    }
}
