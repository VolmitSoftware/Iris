package com.volmit.resource;

import com.volmit.block.BlockRegistry;
import com.volmit.noise.NoiseFactory;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/** Immutable view of one loaded pack snapshot, safe for concurrent reads.
 * Reload produces a different snapshotId, even for the same pack. Resources must be immutable
 * or explicitly thread-safe. The requested type selects the kind of resource, so one ID can name
 * both a biome and a region. Hosts serve {@code GeneratedObject} (pack objects),
 * {@code PackBiome}, {@code PackRegion} and the type of every {@link ResourceKind} the pack's modules
 * register; an ID the pack lacks for that kind returns empty, and a type the host does not serve throws
 * IllegalArgumentException. Broken resources fail loading rather than appearing absent.
 * The host owns resolution, imports, native handles and caches; no filesystem paths escape.
 */
public interface PackResources {
    ResourceId packId();
    UUID snapshotId();
    BlockRegistry blocks();

    /** Builds the noise of the {@link com.volmit.noise.NoiseStyle}s the pack's settings describe. */
    NoiseFactory noise();

    <T> Optional<T> find(ResourceKey<T> key);

    /** The ids of every resource of the kind {@code type} selects, in id order. A type the host does not serve
     * throws IllegalArgumentException, as in {@link #find}.
     */
    List<ResourceId> ids(Class<?> type);

    default <T> T require(ResourceKey<T> key) {
        return find(key).orElseThrow(() -> new NoSuchElementException("Missing resource: " + key.id()));
    }
}
