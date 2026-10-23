package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.SettingsStack;
import com.volmit.generation.ChunkContext;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/** The settings one chunk's stages see: for every settings type the running stages condition on, each
 * distinct entry of the chunk's 16 by 16 columns ({@link FeatureSettings#declaredInChunk}). A stage is therefore
 * active when any column of the chunk configures its settings, also where a biome border crosses the chunk.
 */
final class ChunkSettings {
    private ChunkSettings() {}

    /** {@code settings} is asked only when {@code types} is not empty. */
    static ConfiguredSettings collect(List<Class<?>> types, Supplier<FeatureSettings> settings, int originX, int originZ) {
        if (types.isEmpty()) {
            return ConfiguredSettings.none();
        }
        var view = settings.get();
        int chunkX = Math.floorDiv(originX, ChunkContext.CHUNK_WIDTH);
        int chunkZ = Math.floorDiv(originZ, ChunkContext.CHUNK_WIDTH);
        var stacks = new HashMap<Class<?>, SettingsStack<?>>();
        for (var type : types) {
            stacks.put(type, view.declaredInChunk(type.asSubclass(IrisFeature.class), chunkX, chunkZ));
        }
        return ConfiguredSettings.ofStacks(stacks);
    }
}
