package com.volmit.config;

import com.volmit.IrisFeature;
import com.volmit.generation.ChunkContext;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/** Invocation-scoped lookup of the settings that apply at one world column.
 *
 * <p>Every level that declares the settings contributes its own entry: the dimension, the region and
 * the biome of the column, and in a placement the placement itself. Levels stack: nothing is merged or
 * overridden, and a module decides what several entries mean, such as running a deposit once per
 * entry. The biome is the column's surface biome; cave biomes are not a level. Values must be treated as
 * read-only. Bounds and lifetime match the invocation's sampler.
 *
 * <p>Something that spans several columns, such as a chunk-wide pass or a placed object, reads the
 * settings at one column it treats as its origin; per-chunk behaviour reads the chunk's centre column
 * ({@link ChunkContext#centreX()}, {@link ChunkContext#centreZ()}).
 */
public interface FeatureSettings {
    /** The entries of {@code featureType} at this column, in level order; empty when no level declares it. */
    <F extends IrisFeature> SettingsStack<F> at(Class<F> featureType, int worldX, int worldZ);

    /** Every distinct entry of {@code featureType} at the 16 by 16 columns of chunk {@code chunkX}, {@code chunkZ}, in
     * level order; entries of one level in the order their first column was found, columns by X and then Z. Entries are
     * distinct by level and source. The chunk and mantle runners decide with it whether a conditioned stage runs in a
     * chunk. The default asks {@link #at} for every column; a host that knows which regions and biomes a chunk holds
     * answers it from them.
     */
    default <F extends IrisFeature> SettingsStack<F> declaredInChunk(Class<F> featureType, int chunkX, int chunkZ) {
        var declarations = new HashSet<Declaration>();
        var distinct = new ArrayList<Entry<F>>();
        int originX = chunkX * ChunkContext.CHUNK_WIDTH;
        int originZ = chunkZ * ChunkContext.CHUNK_WIDTH;
        for (int x = 0; x < ChunkContext.CHUNK_WIDTH; x++) {
            for (int z = 0; z < ChunkContext.CHUNK_WIDTH; z++) {
                for (var entry : at(featureType, originX + x, originZ + z)) {
                    if (declarations.add(new Declaration(entry.level(), entry.source()))) {
                        distinct.add(entry);
                    }
                }
            }
        }
        return SettingsStack.of(distinct);
    }

    /** The settings {@code biome} declares itself, whichever column it is found at: for a biome that is not a column's
     * surface biome, such as the cave biome of {@link com.volmit.world.BiomeSample#caveBiome()}, which is no level of
     * {@link #at}. {@code biome} is an id as a {@code BiomeSample} gives it. Empty when the biome declares none, or the
     * host knows no such biome.
     */
    <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType);

    /** One level's declaration of the settings.
     *
     * @param level where the settings are declared: the dimension, a region, a biome or the placement
     * @param source the resource that declares them, such as {@code overworld:biome/desert/dunes}
     * @param value the decoded settings
     */
    record Entry<F extends IrisFeature>(Level level, ResourceId source, F value) {
        public Entry {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(value, "value");
        }
    }
}

/** What makes two entries of a chunk the same declaration. */
record Declaration(Level level, ResourceId source) {}
