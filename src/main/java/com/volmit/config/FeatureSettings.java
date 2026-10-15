package com.volmit.config;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Invocation-scoped lookup of the settings that apply at one world column.
 *
 * <p>Every level that declares the settings contributes its own entry: the dimension, the region and
 * the biome of the column, and in a placement the placement itself. Levels stack: nothing is merged or
 * overridden, and a module decides what several entries mean, such as running a deposit once per
 * entry. Entries are immutable lists in level order ({@link FeatureAttachment.Target} order). The
 * biome is the column's surface biome; cave biomes are not a level. Values must be treated as
 * read-only. Bounds and lifetime match the invocation's sampler.
 *
 * <p>Something that spans several columns, such as a chunk-wide pass or a placed object, reads the
 * settings at one column it treats as its origin; per-chunk behaviour reads the chunk's centre column
 * (local 7, 7).
 */
public interface FeatureSettings {
    <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ);

    /** The settings {@code biome} declares itself, whichever column it is found at: for a biome that is not a column's
     * surface biome, such as the cave biome of {@link com.volmit.world.BiomeSample#caveBiome()}, which is no level of
     * {@link #at}. {@code biome} is an id as a {@code BiomeSample} gives it. Empty when the biome declares none, or the
     * host knows no such biome.
     */
    default <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType) {
        return Optional.empty();
    }

    /** One level's declaration of the settings.
     *
     * @param level where the settings are declared: the dimension, a region, a biome or the placement
     * @param source the resource that declares them, such as {@code overworld:biome/desert/dunes}
     * @param value the decoded settings
     */
    record Entry<F extends IrisFeature>(FeatureAttachment.Target level, ResourceId source, F value) {
        public Entry {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(value, "value");
        }
    }
}
