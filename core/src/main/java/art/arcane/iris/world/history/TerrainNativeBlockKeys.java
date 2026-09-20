package art.arcane.iris.world.history;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

public final class TerrainNativeBlockKeys {
    private TerrainNativeBlockKeys() {
    }

    public static String placementKey(String key) {
        if (!key.startsWith("minecraft:") && IrisPlatforms.isBound()) {
            NativeBlockState logicalState = IrisPlatforms.get().registries().blockOrNull(key);
            NativeBlockState baseState = logicalState == null ? null : logicalState.placementBaseState();
            if (baseState != null) {
                return baseState.key();
            }
        }
        return key;
    }
}
