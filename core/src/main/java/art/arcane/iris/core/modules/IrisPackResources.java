/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.block.BlockRegistry;
import com.volmit.object.GeneratedObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKey;
import com.volmit.world.PackBiome;
import com.volmit.world.PackRegion;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The resources of one loaded snapshot of an Iris pack, which every chunk, mantle and placement stage of that
 * snapshot shares. Each load or hotload of the pack's data makes a new one, with a new {@link #snapshotId()}.
 * <p>
 * Three kinds of resource are served, by the type the key asks for: {@link GeneratedObject} (a pack object, as
 * its geometry), {@link PackBiome} and {@link PackRegion}. All use the id {@code <pack namespace>:<key>} that
 * {@link PackResourceIds} gives the pack's biomes in a biome sample, so one id can name a biome and a region.
 * An id the pack lacks, or of another namespace, is absent; any other type fails with
 * {@link IllegalArgumentException}. A resource that exists but cannot be loaded fails with
 * {@link IllegalStateException}. Each resource is built once and kept for the snapshot.
 */
public final class IrisPackResources implements PackResources {
    private static final String SERVED_TYPES = "GeneratedObject, PackBiome and PackRegion";

    private final PackResourceIds ids;
    private final UUID snapshotId = UUID.randomUUID();
    private final BlockRegistry blocks = new IrisBlockRegistry();
    private final Map<Class<?>, Kind<?, ?>> kinds;

    public IrisPackResources(IrisData data) {
        this.ids = new PackResourceIds(data.getDataFolder());
        this.kinds = Map.of(
                GeneratedObject.class, new Kind<IrisObject, GeneratedObject>(ids, data::getObjectLoader, (id, object) -> object.getGeometry()),
                PackBiome.class, new Kind<IrisBiome, PackBiome>(ids, data::getBiomeLoader, this::biomeOf),
                PackRegion.class, new Kind<IrisRegion, PackRegion>(ids, data::getRegionLoader, this::regionOf));
    }

    @Override
    public ResourceId packId() {
        return ids.packId();
    }

    @Override
    public UUID snapshotId() {
        return snapshotId;
    }

    @Override
    public BlockRegistry blocks() {
        return blocks;
    }

    @Override
    @SuppressWarnings("unchecked") // The kinds are keyed by the type they serve.
    public <T> Optional<T> find(ResourceKey<T> key) {
        Kind<?, T> kind = (Kind<?, T>) kinds.get(key.type());
        if (kind == null) {
            throw new IllegalArgumentException("Iris serves pack resources of " + SERVED_TYPES + ", not " + key.type().getName());
        }
        return kind.find(key.id());
    }

    private PackBiome biomeOf(ResourceId id, IrisBiome biome) {
        return new PackBiome(id, biome.getName(), ResourceId.parse(biome.getDerivativeKey()), biome.getRarity(), idsOf(biome.getChildren()));
    }

    private PackRegion regionOf(ResourceId id, IrisRegion region) {
        return new PackRegion(id, region.getName(), region.getRarity(), idsOf(region.getLandBiomes()), idsOf(region.getSeaBiomes()),
                idsOf(region.getShoreBiomes()), idsOf(region.getCaveBiomes()));
    }

    private List<ResourceId> idsOf(Collection<String> keys) {
        return keys.stream().map(ids::of).toList();
    }

    /** One kind of pack resource: the loader that serves its keys, an index from ids to keys and what was built so far. */
    private static final class Kind<R extends IrisRegistrant, T> {
        private final PackResourceIds ids;
        private final Supplier<ResourceLoader<R>> loader;
        private final BiFunction<ResourceId, R, T> conversion;
        private final Map<ResourceId, T> built = new ConcurrentHashMap<>();
        private volatile Map<ResourceId, String> keysById;

        Kind(PackResourceIds ids, Supplier<ResourceLoader<R>> loader, BiFunction<ResourceId, R, T> conversion) {
            this.ids = ids;
            this.loader = loader;
            this.conversion = conversion;
        }

        Optional<T> find(ResourceId id) {
            T cached = built.get(id);
            if (cached != null) {
                return Optional.of(cached);
            }
            String key = keysById().get(id);
            if (key == null) {
                return Optional.empty();
            }
            R resource = loader.get().load(key);
            if (resource == null) {
                throw new IllegalStateException("Pack resource " + id + " exists but failed to load");
            }
            T made = conversion.apply(id, resource);
            T raced = built.putIfAbsent(id, made);
            return Optional.of(raced != null ? raced : made);
        }

        /** The pack's keys by the ids they get, built on first use; a key whose id another key already has is left out. */
        private Map<ResourceId, String> keysById() {
            Map<ResourceId, String> index = keysById;
            if (index != null) {
                return index;
            }
            Map<ResourceId, String> indexed = new HashMap<>();
            for (String key : loader.get().getPossibleKeys()) {
                indexed.putIfAbsent(ids.of(key), key);
            }
            keysById = Map.copyOf(indexed);
            return keysById;
        }
    }
}
