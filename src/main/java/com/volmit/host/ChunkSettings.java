package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/** The settings one chunk's stages see: for every settings type the running stages condition on, each
 * distinct entry of the chunk's 16 by 16 columns. A stage is therefore active when any column of the chunk
 * configures its settings, also where a biome border crosses the chunk. Entries are distinct by level
 * and source and keep the order their first column was read in, columns by X and then Z.
 */
final class ChunkSettings {
    /** The chunk's width along X and Z, in blocks: the footprint of chunk and mantle stages. */
    static final int CHUNK_WIDTH = 16;

    private ChunkSettings() {}

    /** {@code settings} is asked only when {@code types} is not empty. */
    static ConfiguredSettings collect(List<Class<?>> types, Supplier<FeatureSettings> settings, int originX, int originZ) {
        if (types.isEmpty()) {
            return ConfiguredSettings.none();
        }
        var view = settings.get();
        var entries = new ArrayList<Entry<?>>();
        for (var type : types) {
            entries.addAll(distinctEntries(type.asSubclass(IrisFeature.class), view, originX, originZ));
        }
        return ConfiguredSettings.of(entries);
    }

    private static Collection<Entry<?>> distinctEntries(
            Class<? extends IrisFeature> type, FeatureSettings view, int originX, int originZ) {
        var distinct = new LinkedHashMap<Declaration, Entry<?>>();
        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int z = 0; z < CHUNK_WIDTH; z++) {
                for (var entry : view.at(type, originX + x, originZ + z)) {
                    distinct.putIfAbsent(new Declaration(entry.level(), entry.source()), entry);
                }
            }
        }
        return distinct.values();
    }

    private record Declaration(FeatureAttachment.Target level, ResourceId source) {}
}
